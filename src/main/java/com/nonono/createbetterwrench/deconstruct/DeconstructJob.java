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
 * 「拆除」的**分帧执行器**。
 *
 * <p>大范围拆除如果一次性在同一个 tick 里跑完, 会因为每格都要
 * {@code getBlockState} + {@code BlockEvent.BreakEvent} 广播 + loot table + {@code destroyBlock}
 * 而**长时间卡住服务端主线程**。所以这里把选区切成 **16×16×16 的子块, 每个服务端刻只处理一个**:
 * 最大 64×64×64 = 64 个子块 ⇒ 最坏约 64 刻(≈3.2 秒)分批完成, 期间服务器保持响应。</p>
 *
 * <p>小选区(体积 ≤ 16³)仍然**当场做完**, 正常游玩没有任何延迟感。</p>
 *
 * <p>同一玩家同时只保留一个任务(再次发起会覆盖旧的);玩家登出或换维度会丢弃任务。</p>
 */
public final class DeconstructJob {

    /** 子块边长(用户指定: 按 16×16×16 依次计算)。 */
    public static final int CHUNK = 16;
    private static final int CHUNK_VOLUME = CHUNK * CHUNK * CHUNK;

    private final ServerLevel level;
    private final UUID playerId;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final DeconstructScope scope;
    private final int chunksX, chunksY, chunksZ;

    private int cx, cy, cz;
    private int removed;

    private static final Map<UUID, DeconstructJob> ACTIVE = new HashMap<>();

    private DeconstructJob(ServerLevel level, UUID playerId, DeconstructScope scope,
                           int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.level = level;
        this.playerId = playerId;
        this.scope = scope;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        this.chunksX = (maxX - minX) / CHUNK + 1;
        this.chunksY = (maxY - minY) / CHUNK + 1;
        this.chunksZ = (maxZ - minZ) / CHUNK + 1;
    }

    /** 提交一次拆除请求: 小选区当场完成, 大选区登记为分帧任务。 */
    public static void start(ServerLevel level, ServerPlayer player, BlockPos a, BlockPos b,
                             DeconstructScope scope) {
        int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());

        DeconstructJob job = new DeconstructJob(level, player.getUUID(), scope,
            minX, minY, minZ, maxX, maxY, maxZ);

        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume <= CHUNK_VOLUME) {
            // 小范围: 一次做完, 保持原有的"即时反馈"
            while (job.hasNext())
                job.runOneChunk(player);
            report(player, job.removed);
            return;
        }

        // 大范围: 覆盖同一玩家的旧任务, 分帧执行
        ACTIVE.put(player.getUUID(), job);
        player.displayClientMessage(Component.translatable(
            "msg." + BetterWrenchMod.MODID + ".deconstruct.batching",
            job.chunksX * job.chunksY * job.chunksZ), true);
    }

    private boolean hasNext() {
        return cz < chunksZ;
    }

    /** 处理一个 16³ 子块。 */
    private void runOneChunk(ServerPlayer player) {
        int x0 = minX + cx * CHUNK, x1 = Math.min(x0 + CHUNK - 1, maxX);
        int y0 = minY + cy * CHUNK, y1 = Math.min(y0 + CHUNK - 1, maxY);
        int z0 = minZ + cz * CHUNK, z1 = Math.min(z0 + CHUNK - 1, maxZ);

        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir())
                        continue;
                    if (!DeconstructLogic.matchesScope(state, scope))
                        continue;
                    if (DeconstructLogic.deconstructBlock(level, pos, player))
                        removed++;
                }

        // 前进到下一个子块(z → y → x 顺序, 自底向上扫)
        if (++cz >= chunksZ) {
            cz = 0;
            if (++cy >= chunksY) {
                cy = 0;
                cx++;
            }
        }
    }

    private static void report(ServerPlayer player, int removed) {
        player.displayClientMessage(Component.translatable(
            "msg." + BetterWrenchMod.MODID + ".deconstruct.count", removed), true);
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty())
            return;
        Iterator<Map.Entry<UUID, DeconstructJob>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            DeconstructJob job = it.next().getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(job.playerId);
            // 玩家已离线 / 换了维度 / 区块不再加载 -> 放弃任务(不报错)
            if (player == null || player.serverLevel() != job.level) {
                it.remove();
                continue;
            }
            if (job.hasNext())
                job.runOneChunk(player);
            if (!job.hasNext()) {
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
