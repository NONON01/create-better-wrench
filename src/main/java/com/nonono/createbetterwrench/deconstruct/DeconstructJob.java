package com.nonono.createbetterwrench.deconstruct;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.config.WrenchConfig;
import com.nonono.createbetterwrench.mode.DeconstructScope;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 「拆除」的**逐刻执行器**:把一个大选区切成若干片, 每服务端刻处理一片。
 *
 * <p>为什么需要分片:一次拆几千格会长时间占住服务端主线程(单机时客户端一起冻)。
 * 但**分片的粒度不该按时间预算精调** —— 实测表明卡顿的真正来源是
 * <b>Create {@code onSneakWrenched} 每格产生的破坏粒子 / 音效 / 经验球</b>,
 * 而不是拆除本身的计算量。那部分已在 {@link DeconstructLogic#deconstructBlock} 里
 * 通过"静默拆除"消除(绝大多数方块不再走 Create 那条产生特效的路径)。</p>
 *
 * <p>所以这里只用**一个朴素的固定片大小** {@link #blocksPerTick}:每刻最多处理这么多格。
 * 简单、可预测、便于解释, 没有时钟读取也没有自适应逻辑。</p>
 *
 * <p>提交时先**当场处理一片**:小选区因此立即完成(保持即时反馈), 没做完才登记为跨刻任务。
 * 同一玩家只保留一个任务;玩家登出或换维度会丢弃任务。</p>
 */
public final class DeconstructJob {

    /**
     * 每服务端刻处理的格数上限 —— 读配置 {@code deconstruct.blocks_per_tick}(默认 1024)。
     *
     * <p>默认值 1024 的依据: 静默拆除后每格只剩「BreakEvent + 产物入包 + removeBlock」,
     * 16240 格约 16 刻(≈0.8 秒)完成。若改大则单刻更重(可能感觉到顿), 改小则总耗时更长。</p>
     *
     * <p>⚠️ 每个任务在**创建时读一次**(不是每格去查配置)。</p>
     */
    private final int blocksPerTick;

    /**
     * 「实际减少方块数」统计的体量上限。
     *
     * <p>为了让 Create 多方块的**连带拆除**也计入上报数(旧实现只给"发起格"+1, 所以大型水车这类结构会少报),
     * 我们在开始时数一遍选区里的非空气方块、结束时再数一遍, 用差值作为结果。
     * 两次全量扫描对超大选区(64³ = 262144 格)会有可感知的主线程停顿,
     * 所以只对 ≤ {@value} 格的选区启用; 更大的选区退回"直接计数"(可能少算连带拆除的格数)。
     * 详见 docs/07 §6 A-13。</p>
     */
    private static final long COUNT_DELTA_MAX_VOLUME = 32768; // 32³

    private final ServerLevel level;
    private final UUID playerId;
    private final DeconstructScope scope;

    private final int minX, minY, minZ;
    private final int sizeX, sizeY, sizeZ;
    private final long volume;

    /** 游标(相对选区的坐标, z 最快、然后 y、最后 x)。 */
    private int cx, cy, cz;
    private boolean done;

    /** 直接由本次循环拆掉的格数(**下界**: 不含多方块被连带拆除的那些)。 */
    private int removed;

    /** 开始时的非空气方块数; {@code -1} = 该选区体量过大, 不启用差值统计。 */
    private final long blocksBefore;

    private static final Map<UUID, DeconstructJob> ACTIVE = new HashMap<>();

    private DeconstructJob(ServerLevel level, UUID playerId, DeconstructScope scope,
                           int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.level = level;
        this.playerId = playerId;
        this.scope = scope;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.sizeX = maxX - minX + 1;
        this.sizeY = maxY - minY + 1;
        this.sizeZ = maxZ - minZ + 1;
        this.volume = (long) this.sizeX * this.sizeY * this.sizeZ;
        this.blocksBefore = this.volume <= COUNT_DELTA_MAX_VOLUME ? countNonAir() : -1L;
        this.blocksPerTick = WrenchConfig.deconstructBlocksPerTick();
    }

    /** 选区体积(格数), 用于给玩家的提示文案。 */
    public long volume() {
        return volume;
    }

    /** 提交一次拆除请求: 先当场处理一片;没做完再登记为跨刻任务。 */
    public static void start(ServerLevel level, ServerPlayer player, BlockPos a, BlockPos b,
                             DeconstructScope scope) {
        int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());

        DeconstructJob job = new DeconstructJob(level, player.getUUID(), scope,
            minX, minY, minZ, maxX, maxY, maxZ);

        // 用户要求: 拆除时在**玩家处播放一次** Create 扳手音效。
        // (Create 的默认实现是**每格**一次 —— 批量拆除时那是成千上万个音效事件, 正是卡顿来源之一;
        //  现改为整次操作只播一次, 位置取玩家脚下, 保留"扳手把东西拆下来"的听感。)
        IWrenchable.playRemoveSound(level, player.blockPosition());

        job.runSlice(player);

        if (job.done) {
            // 小选区: 当场做完, 保持即时反馈
            report(player, job.reportCount());
            return;
        }

        // 大选区: 覆盖同一玩家的旧任务, 跨刻继续
        ACTIVE.put(player.getUUID(), job);
        player.displayClientMessage(Component.translatable(
            "msg." + BetterWrenchMod.MODID + ".deconstruct.batching", job.volume), true);
    }

    /** 处理至多 {@link #blocksPerTick} 格;做完就置 done。 */
    private void runSlice(ServerPlayer player) {
        for (int i = 0; i < blocksPerTick && !done; i++) {
            int x = minX + cx, y = minY + cy, z = minZ + cz;
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && DeconstructLogic.matchesScope(state, scope)
                && DeconstructLogic.deconstructBlock(level, pos, player)) {
                removed++;
            }
            advance();
        }
    }

    /** 游标前进一格: z → y → x, 自底向上扫;走到头就置 done。 */
    private void advance() {
        if (++cz < sizeZ) {
            return;
        }
        cz = 0;
        if (++cy < sizeY) {
            return;
        }
        cy = 0;
        if (++cx >= sizeX) {
            done = true;
        }
    }

    /** 选区内的非空气方块数(跳过未加载区块, 避免顺带把区块加载进来)。 */
    private long countNonAir() {
        long n = 0;
        for (int x = minX; x < minX + sizeX; x++)
            for (int y = minY; y < minY + sizeY; y++)
                for (int z = minZ; z < minZ + sizeZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    // isLoaded: hasChunkAt 家族已被 NeoForge 弃用(见 ConnectLogic#loadedCached 的说明)
                    if (level.isLoaded(pos) && !level.getBlockState(pos).isAir())
                        n++;
                }
        return n;
    }

    /**
     * 上报用的拆除数量: 优先用「开始 − 结束」的非空气方块**差值**, 这样多方块的连带拆除也会被算进来。
     * 大选区(未启用差值统计)退回直接计数。
     */
    private int reportCount() {
        if (blocksBefore < 0)
            return removed;
        long delta = blocksBefore - countNonAir();
        return (int) Math.max(removed, Math.max(0L, delta));
    }

    private static void report(ServerPlayer player, int removed) {
        player.displayClientMessage(Component.translatable(
            "msg." + BetterWrenchMod.MODID + ".deconstruct.count", removed), true);
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, DeconstructJob>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            DeconstructJob job = it.next().getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(job.playerId);
            // 玩家已离线 / 换了维度 -> 放弃任务(不报错)
            if (player == null || player.serverLevel() != job.level) {
                it.remove();
                continue;
            }
            if (!job.done) {
                job.runSlice(player);
            }
            if (job.done) {
                it.remove();
                report(player, job.reportCount());
            }
        }
    }

    @EventBusSubscriber(modid = BetterWrenchMod.MODID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            tick(event.getServer());
        }

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            ACTIVE.remove(event.getEntity().getUUID());
        }
    }
}
