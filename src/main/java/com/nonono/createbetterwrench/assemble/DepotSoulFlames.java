package com.nonono.createbetterwrench.assemble;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.TickEvent;

/**
 * 「锁定的置物台 + 下方是灵魂底座」时从台座<b>下方</b>往上冒灵魂火焰粒子。
 *
 * <h2>设计约定</h2>
 * <p><b>2026-09-23</b>: 置物台下方有灵魂沙且该置物台被锁定时, 置物台缓慢地冒灵魂火焰粒子,
 * 效果看起来要像是从置物台下方冒出来的。</p>
 * <p><b>2026-10-02</b>: 需求改为<b>更密、更快、更扩散</b> —— 发射周期由 10 tick 缩短到 4 tick,
 * 每次发 2 颗, 向上初速由 0.02 提到 0.05, 并给水平初速一个正负 0.02 的随机范围(原先水平速度恒为 0),
 * 离面距离也加了随机抖动。四项参数都可独立调整, 见下方常量。</p>
 *
 * <h2>为什么要自己记一份坐标表</h2>
 * 置物台是 Create 的方块实体, 本模组<b>无法</b>给它挂 ticker(那需要 Mixin 改
 * {@code BlockEntityType} 或 {@code DepotBlockEntity} 本身)。因此改成<b>只遍历锁定的置物台</b>:
 * <ul>
 *   <li>上锁/解锁时由 {@link AssembleLock#setLocked} 通知增删; </li>
 *   <li>为了<b>存档重进后也有效</b>, 每个区块加载时({@link ChunkEvent.Load})扫一遍该区块的方块实体,
 *       把已经锁着的置物台补进来(见 {@link #indexChunk});</li>
 *   <li>世界卸载时整表清掉(与 {@code DepotProductEjector} 同一套理由: 不长期持有废弃的 {@code ServerLevel})。</li>
 * </ul>
 * 表只在<b>服务端主线程</b>读写, 因此用普通 {@code HashMap} 即可(与 {@code DepotProductEjector} 的约定一致)。
 *
 * <h2>粒子位置: 看起来像从置物台下方冒出来</h2>
 * 置物台的模型是<b>整格</b>的底座(实测 {@code assets/create/models/block/depot/block.json}:
 * 主体 y=0~11/16、台面 11/16~13/16, 横向铺满 0~16), 因此把粒子放在方块<b>内部</b>会被模型挡住。
 * 所以粒子放在<b>四条竖边的外侧一丝</b>(离方块面 0.03 格起, 另有随机抖动), y 从台座底部往上一点开始,
 * 再给一个向上为主、水平带随机分量的初速 —— 观感就是从台面与灵魂沙的接缝里渗出来往上飘散。
 *
 * <p>速度为什么能精确控制: {@code ServerLevel#sendParticles} 在 {@code count == 0} 时,
 * 客户端({@code ClientPacketListener#handleParticleEvent} 的 {@code count==0} 分支, 已 javap 核过)
 * 会把三个偏移量<b>当成速度</b>再乘上 {@code speed}。因此把 {@code speed} 取 1 并直接给出速度分量
 * (见 {@link #spawnOne}), 得到的就是指定方向的粒子, 而不是 {@code count > 0} 那种高斯随机方向的粒子。</p>
 */
public final class DepotSoulFlames {

    /**
     * 每多少个服务端刻发一次粒子(20 tps 下 4 tick 约 0.2 秒一次)。
     *
     * <p>2026-10-02 由 10 调整为 4: 需求改为"更密、更快、更扩散", 原值是按"缓慢"设计的。</p>
     */
    private static final int PERIOD_TICKS = 4;

    /** 单次在四条边之间共发出几颗粒子(位置各自随机选边)。 */
    private static final int PARTICLES_PER_EMISSION = 2;

    /** 向上的初速(格/刻)。0.05 配合粒子的 0.96 摩擦, 上升高度约为原先 0.02 时的 2.5 倍。 */
    private static final double RISE_SPEED = 0.05;

    /** 水平初速的最大绝对值(格/刻), 用于制造扩散; 取负值到正值的均匀分布。 */
    private static final double SPREAD_SPEED = 0.02;

    /** 粒子离方块面多远(放在外面一丝, 避免与整格底座模型重叠)。 */
    private static final double EDGE_GAP = 0.03;

    /** 离面距离的随机抖动, 让粒子不落在同一条细线上。 */
    private static final double EDGE_JITTER = 0.02;

    /** 世界到该世界里<b>已锁定</b>置物台坐标的映射。仅服务端主线程访问。 */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> LOCKED_DEPOTS = new HashMap<>();

    private DepotSoulFlames() {
    }

    // ------------------------------------------------------------------ 登记

    /** 置物台上锁/解锁时调用(见 {@link AssembleLock#setLocked})。 */
    static void setTracked(Level level, BlockPos pos, boolean locked) {
        if (level == null || level.isClientSide())
            return;
        Set<BlockPos> set = LOCKED_DEPOTS.computeIfAbsent(level.dimension(), k -> new HashSet<>());
        if (locked)
            set.add(pos.immutable());
        else
            set.remove(pos);
        if (set.isEmpty())
            LOCKED_DEPOTS.remove(level.dimension());
    }


    // 复审 B-15(2026-09-23): 原来的 untrack(Level, BlockPos) 已删除 —— 它全项目零调用点,
    //    因为 tick 里的 positions.removeIf(pos -> !stillLocked(level, pos)) 已经覆盖了置物台被拆掉/解锁
    //    这两种情形。若将来要在破坏方块的那一刻立刻停粒子, 再把一个 untrack 接进
    //    AssembleInteractionHandler 的破坏分支即可。
    /**
     * 区块加载时把里面<b>已经锁着</b>的置物台补进表里 —— 这是存档重进后粒子仍在的关键。
     *
     * <p>只扫方块实体(不是全部方块), 每区块一次性开销极小。NeoForge 的 {@code ChunkEvent.Load}
     * 客户端也会发, 所以先判 {@code ServerLevel}。</p>
     *
     * <p>注意: 这里读得到锁状态是有依据的 —— javap 证实 {@code BlockEntity.loadAdditional()} 内部会调
     * {@code deserializeAttachments(..., nbt.getCompound("neoforge:attachments"))}, 方块实体反序列化时
     * 就恢复了锁定附件, <b>早于</b>本事件。</p>
     */
    private static void indexChunk(ServerLevel level, LevelChunk chunk) {
        Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();
        if (blockEntities.isEmpty())
            return;
        for (Map.Entry<BlockPos, BlockEntity> entry : blockEntities.entrySet()) {
            if (!(entry.getValue() instanceof DepotBlockEntity depot))
                continue;
            if (AssembleLock.isLocked(depot))
                setTracked(level, entry.getKey(), true);
        }
    }

    private static void forget(Level level) {
        LOCKED_DEPOTS.remove(level.dimension());
    }

    // ------------------------------------------------------------------ 判定


    // 复审 B-17: 原来的 isSoulBase 拷贝已删除 —— 判据统一由 AssembleLogic.isSoulBase 提供(全模组唯一一份),
    // 避免 Create 改了 SOUL_FIRE_BASE_BLOCKS 的语义时这里不同步。
    private static boolean stillLocked(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos))
            return false;                    // 区块已卸载: 先从表里摘掉, 下次 ChunkEvent.Load 会重新登记
        if (!(level.getBlockEntity(pos) instanceof DepotBlockEntity depot))
            return false;
        return AssembleLock.isLocked(depot);
    }

    // ------------------------------------------------------------------ 粒子

    private static void spawn(ServerLevel level, BlockPos pos) {
        for (int i = 0; i < PARTICLES_PER_EMISSION; i++) {
            spawnOne(level, pos);
        }
    }

    /** 从四条竖边中随机选一条发出<b>一颗</b>粒子: 向上为主, 带水平抖动与离面抖动。 */
    private static void spawnOne(ServerLevel level, BlockPos pos) {
        double t = 0.12 + level.random.nextDouble() * 0.76;   // 沿边位置(避开四个角)
        double gap = EDGE_GAP + level.random.nextDouble() * EDGE_JITTER;
        double x;
        double z;
        switch (level.random.nextInt(4)) {
            case 0 -> {                                        // 北边
                x = pos.getX() + t;
                z = pos.getZ() - gap;
            }
            case 1 -> {                                        // 东边
                x = pos.getX() + 1 + gap;
                z = pos.getZ() + t;
            }
            case 2 -> {                                        // 南边
                x = pos.getX() + t;
                z = pos.getZ() + 1 + gap;
            }
            default -> {                                       // 西边
                x = pos.getX() - gap;
                z = pos.getZ() + t;
            }
        }
        double y = pos.getY() + 0.02 + level.random.nextDouble() * 0.10;
        // count=0 时那三个偏移量被当作初速(再乘以 speed, 见类注释), 故把 speed 取 1 并直接给出速度分量:
        // 竖直方向固定 RISE_SPEED, 水平方向取正负 SPREAD_SPEED 之间的均匀随机值 —— 这就是"扩散"的来源。
        double vx = (level.random.nextDouble() - 0.5) * 2.0 * SPREAD_SPEED;
        double vz = (level.random.nextDouble() - 0.5) * 2.0 * SPREAD_SPEED;
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0, vx, RISE_SPEED, vz, 1.0);
    }

    // ------------------------------------------------------------------ 事件

    /** 事件订阅(服务端): 区块补登记 / 世界卸载清理 / 定时冒粒子。 */
    @EventBusSubscriber(modid = BetterWrenchMod.MODID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onChunkLoad(ChunkEvent.Load event) {
            // 只处理完整区块(LevelChunk 才有 getBlockEntities); 客户端也会收到本事件, 故先判 ServerLevel
            if (event.getLevel() instanceof ServerLevel serverLevel && event.getChunk() instanceof LevelChunk levelChunk)
                indexChunk(serverLevel, levelChunk);
        }

        @SubscribeEvent
        public static void onLevelUnload(LevelEvent.Unload event) {
            if (LOCKED_DEPOTS.isEmpty() || !(event.getLevel() instanceof Level level))
                return;
            if (level instanceof ServerLevel)
                forget(level);
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
            if (LOCKED_DEPOTS.isEmpty())
                return;
            var server = event.getServer();
            boolean emit = server.getTickCount() % PERIOD_TICKS == 0;
            Iterator<Map.Entry<ResourceKey<Level>, Set<BlockPos>>> it = LOCKED_DEPOTS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ResourceKey<Level>, Set<BlockPos>> entry = it.next();
                ServerLevel level = server.getLevel(entry.getKey());
                if (level == null) {
                    it.remove();
                    continue;
                }
                Set<BlockPos> positions = entry.getValue();
                positions.removeIf(pos -> !stillLocked(level, pos));
                if (positions.isEmpty()) {
                    it.remove();
                    continue;
                }
                if (!emit)
                    continue;
                for (BlockPos pos : positions) {
                    if (AssembleLogic.isSoulBase(level, pos.below()))
                        spawn(level, pos);
                }
            }
        }
    }
}
