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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 「锁定的置物台 + 下方是灵魂底座」时<b>缓慢往上冒灵魂火焰粒子</b>。
 *
 * <h2>设计约定(2026-09-23)</h2>
 * <p>置物台下方有灵魂沙且该置物台被锁定时, 置物台缓慢地冒灵魂火焰粒子,
 * 效果看起来要像是从置物台下方冒出来的。</p>
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
 * 所以粒子放在<b>四条竖边的外侧一丝</b>(离方块面 0.03 格), y 从台座底部往上一点开始,
 * 再给一个<b>很小的纯向上速度</b> —— 观感就是从台面与灵魂沙的接缝里渗出来、贴着台座慢慢往上飘。
 *
 * <p>速度为什么能精确控制: {@code ServerLevel#sendParticles} 在 {@code count == 0} 时,
 * 客户端({@code ClientPacketListener#handleParticleEvent} 的 {@code count==0} 分支, 已 javap 核过)
 * 会把三个偏移量<b>当成速度</b>再乘上 {@code speed}。因此
 * {@code sendParticles(type, x, y, z, 0, 0, 1, 0, 0.02)} 得到一颗<b>竖直向上、速度 0.02</b> 的粒子,
 * 而不是 {@code count > 0} 那种高斯随机方向的粒子。</p>
 */
public final class DepotSoulFlames {

    /** 每多少个服务端刻冒一颗(20 tps 下 10 tick 约 0.5 秒一颗)。设计约定要求缓慢, 因此不宜调小。 */
    private static final int PERIOD_TICKS = 10;

    /** 纯向上的初速(格/刻)。0.02 配合粒子的 0.96 摩擦, 一生大约上升 0.35 格。 */
    private static final double RISE_SPEED = 0.02;

    /** 粒子离方块面多远(放在外面一丝, 避免与整格底座模型重叠)。 */
    private static final double EDGE_GAP = 0.03;

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
        double t = 0.12 + level.random.nextDouble() * 0.76;   // 沿边位置(避开四个角)
        double x;
        double z;
        switch (level.random.nextInt(4)) {
            case 0 -> {                                        // 北边
                x = pos.getX() + t;
                z = pos.getZ() - EDGE_GAP;
            }
            case 1 -> {                                        // 东边
                x = pos.getX() + 1 + EDGE_GAP;
                z = pos.getZ() + t;
            }
            case 2 -> {                                        // 南边
                x = pos.getX() + t;
                z = pos.getZ() + 1 + EDGE_GAP;
            }
            default -> {                                       // 西边
                x = pos.getX() - EDGE_GAP;
                z = pos.getZ() + t;
            }
        }
        double y = pos.getY() + 0.02 + level.random.nextDouble() * 0.06;
        // count=0 时偏移量当速度(见类注释), 得到一颗纯向上、缓慢上升的灵魂火焰
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0, 0.0, 1.0, 0.0, RISE_SPEED);
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
        public static void onServerTick(ServerTickEvent.Post event) {
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
