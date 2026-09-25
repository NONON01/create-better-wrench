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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 「锁定的置物台 + 下方是灵魂底座」⇒ **缓慢往上冒灵魂火焰粒子**。
 *
 * <h2>用户要求(2026-09-23)</h2>
 * <blockquote>
 * 「如果置物台下方有灵魂沙并且被锁定时, 置物台缓慢的冒灵魂火焰粒子
 * (效果看起来要像是从置物台下方冒出来的)」
 * </blockquote>
 *
 * <h2>为什么要自己记一份坐标表</h2>
 * 置物台是 Create 的方块实体, 我们**没法**给它挂 ticker(那需要 Mixin 改
 * {@code BlockEntityType} 或 {@code DepotBlockEntity} 本身)。所以改成"**只遍历锁定的置物台**":
 * <ul>
 *   <li>上锁/解锁时由 {@link AssembleLock#setLocked} 通知增删; </li>
 *   <li>为了**存档重进后也有效**, 每个区块加载时({@link ChunkEvent.Load})扫一遍该区块的方块实体,
 *       把已经锁着的置物台补进来(见 {@link #indexChunk});</li>
 *   <li>世界卸载时整表清掉(与 {@code DepotProductEjector} 同一套理由: 别长期持有废弃的 {@code ServerLevel})。</li>
 * </ul>
 * 表只在**服务端主线程**读写, 因此用普通 {@code HashMap} 即可(与 {@code DepotProductEjector} 的约定一致)。
 *
 * <h2>粒子怎么放才"像是从置物台下方冒出来"</h2>
 * 置物台的模型是**整格**的底座(实测 {@code assets/create/models/block/depot/block.json}:
 * 主体 y=0~11/16、台面 11/16~13/16, 横向铺满 0~16) ⇒ 若把粒子放在方块**内部**会被模型挡住。
 * 所以粒子放在**四条竖边的外侧一丝**(离方块面 0.03 格), y 从台座底部往上一点开始,
 * 再给一个**很小的纯向上速度** —— 观感就是"从台面与灵魂沙的接缝里渗出来、贴着台座慢慢往上飘"。
 *
 * <p>速度为什么能精确控制: {@code ServerLevel#sendParticles} 在 {@code count == 0} 时,
 * 客户端({@code ClientPacketListener#handleParticleEvent} 的 {@code count==0} 分支, 已 javap 核过)
 * 会把三个偏移量**当成速度**再乘上 {@code speed}。于是
 * {@code sendParticles(type, x, y, z, 0, 0, 1, 0, 0.02)} = 一颗**竖直向上、速度 0.02** 的粒子,
 * 而不是 {@code count > 0} 那种"高斯随机方向"的粒子。</p>
 */
public final class DepotSoulFlames {

    /** 每多少个服务端刻冒一颗(20 tps ⇒ 10 tick 约 0.5 秒一颗)。用户要"缓慢", 所以别调太小。 */
    private static final int PERIOD_TICKS = 10;

    /** 纯向上的初速(格/刻)。0.02 配合粒子的 0.96 摩擦 ⇒ 一生大约升 0.35 格, 慢悠悠的。 */
    private static final double RISE_SPEED = 0.02;

    /** 粒子离方块面多远(放在外面一丝, 免得与整格底座模型打架)。 */
    private static final double EDGE_GAP = 0.03;

    /** 世界 → 该世界里**已锁定**的置物台坐标。仅服务端主线程访问。 */
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

    /** 某个坐标不再需要跟踪(置物台被拆掉等)。 */
    static void untrack(Level level, BlockPos pos) {
        if (level == null || level.isClientSide())
            return;
        Set<BlockPos> set = LOCKED_DEPOTS.get(level.dimension());
        if (set == null)
            return;
        set.remove(pos);
        if (set.isEmpty())
            LOCKED_DEPOTS.remove(level.dimension());
    }

    /**
     * 区块加载时把里面**已经锁着**的置物台补进表里 —— 这是"存档重进后粒子还在"的关键。
     *
     * <p>只扫方块实体(不是全部方块), 每区块一次性开销极小。NeoForge 的 {@code ChunkEvent.Load}
     * 客户端也会发, 所以先判 {@code ServerLevel}。</p>
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

    /** 与 {@code AssembleLogic#isSoulBase} 同一判据: 原版 {@code SOUL_FIRE_BASE_BLOCKS}(灵魂沙/灵魂土) + 灵魂火。 */
    private static boolean isSoulBase(Level level, BlockPos below) {
        BlockState state = level.getBlockState(below);
        return state.is(BlockTags.SOUL_FIRE_BASE_BLOCKS) || state.is(Blocks.SOUL_FIRE);
    }

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
        // count=0 + 偏移量当速度(见类注释) ⇒ 一颗纯向上、慢悠悠的灵魂火焰
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
            // 只处理"完整区块"(LevelChunk 才有 getBlockEntities); 客户端也会收到本事件, 故先判 ServerLevel
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
                    if (isSoulBase(level, pos.below()))
                        spawn(level, pos);
                }
            }
        }
    }
}
