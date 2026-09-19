package com.nonono.createbetterwrench.deconstruct;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.mode.DeconstructScope;

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
 * 「拆除」的**按时间预算的分帧执行器**。
 *
 * <p>大范围拆除若一次跑完, 每格都要 {@code BlockState} 查询 + {@code BlockEvent.BreakEvent} 广播 +
 * loot table + {@code destroyBlock}, 会长时间占住服务端主线程(单机下客户端一并冻住)。</p>
 *
 * <h2>为什么不是"每刻一个 16³ 子块"</h2>
 * <p>最初按 16³ 子块分批: 每刻处理一个子块。但一个子块最多 **4096 格** ——
 * 实测 16240 格 / 8 批时**每刻要干约 2000 格, 造成约 1 秒卡顿**。
 * 批的"粒度"其实由**单刻的工作量**决定, 而不是由几何切块决定。</p>
 *
 * <p>现在改成**时间预算**: 每刻最多干 {@link #BUDGET_NANOS} 纳秒(默认 10ms, 约占一个 tick 的 20%),
 * 到点就停、下一 tick 接着从游标处继续。这样单刻开销**有上界**, 无论选区多大都不会再"卡一下";
 * 代价是大选区耗时变长(16k 格 ≈ 5 秒), 但期间**一直流畅**且能看见逐片消失。</p>
 *
 * <p>另加一道硬上限 {@link #MAX_BLOCKS_PER_TICK} 兜底(防止个别方块极廉价时单刻处理过多)。</p>
 *
 * <p>提交时先**当场**跑一个预算: 小选区因此立即完成(保持原有的即时反馈, 无延迟感);
 * 没跑完才登记为跨刻任务。同一玩家只保留一个任务;玩家登出/换维度会丢弃任务。</p>
 */
public final class DeconstructJob {

    /** 每刻的时间预算(纳秒)。10ms ≈ 一个 50ms tick 的 20%, 留足余量给别的系统。 */
    private static final long BUDGET_NANOS = 10_000_000L;

    /** 单刻处理的格数硬上限(兜底;正常由时间预算先触发)。 */
    private static final int MAX_BLOCKS_PER_TICK = 4096;

    /** 每处理这么多格才查一次时钟 —— {@code System.nanoTime()} 很便宜, 但没必要每格都查。 */
    private static final int TIME_CHECK_INTERVAL = 64;

    private final ServerLevel level;
    private final UUID playerId;
    private final DeconstructScope scope;

    private final int minX, minY, minZ;
    private final int sizeX, sizeY, sizeZ;
    private final long volume;

    /** 游标(相对选区的坐标, z 最快、然后 y、最后 x)。 */
    private int cx, cy, cz;
    private boolean done;

    private int removed;

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
    }

    /** 选区体积(格数), 用于给玩家的提示文案。 */
    public long volume() {
        return volume;
    }

    /** 提交一次拆除请求: 先当场跑一个预算;没跑完再登记为跨刻任务。 */
    public static void start(ServerLevel level, ServerPlayer player, BlockPos a, BlockPos b,
                             DeconstructScope scope) {
        int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());

        DeconstructJob job = new DeconstructJob(level, player.getUUID(), scope,
            minX, minY, minZ, maxX, maxY, maxZ);

        job.runBudgeted(player);

        if (job.done) {
            // 小选区: 当场做完, 保持即时反馈
            report(player, job.removed);
            return;
        }

        // 大选区: 覆盖同一玩家的旧任务, 跨刻继续
        ACTIVE.put(player.getUUID(), job);
        player.displayClientMessage(Component.translatable(
            "msg." + BetterWrenchMod.MODID + ".deconstruct.batching", job.volume), true);
    }

    /** 在时间预算内尽量多处理几格;到点或做完就返回。 */
    private void runBudgeted(ServerPlayer player) {
        long deadline = System.nanoTime() + BUDGET_NANOS;
        int processed = 0;
        while (!done && processed < MAX_BLOCKS_PER_TICK) {
            int x = minX + cx, y = minY + cy, z = minZ + cz;
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && DeconstructLogic.matchesScope(state, scope)
                && DeconstructLogic.deconstructBlock(level, pos, player)) {
                removed++;
            }
            advance();

            processed++;
            if ((processed % TIME_CHECK_INTERVAL) == 0 && System.nanoTime() >= deadline) {
                break;
            }
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
                job.runBudgeted(player);
            }
            if (job.done) {
                it.remove();
                report(player, job.removed);
            }
        }
    }

    @EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME)
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
