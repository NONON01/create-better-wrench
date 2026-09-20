package com.nonono.createbetterwrench.assemble;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * 服务端: 已锁定的置物台被右键时 —— 先尝试「工作」模式的各种施加
 * (序列装配 / 机械手式施加 / 原木去皮 / 注液, 见 {@link AssembleLogic}),
 * 无论成功与否都**吃掉这次交互**, 从而不会把台面上的物品取下来。
 *
 * <p>另外负责**置物台被破坏/炸毁时释放料堆** —— 否则那两个掉落物实体会永久停留在
 * {@code pickupDelay=32767} + {@code age=-32768}(既拿不走也不会消失), 玩家直接丢东西。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class AssembleInteractionHandler {

    private AssembleInteractionHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide)
            return;
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof DepotBlockEntity depot))
            return;
        if (!AssembleLock.isLocked(depot))
            return;

        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        AssembleLogic.tryAssemble(level, pos, depot, player, held, event.getHand());
        // 锁定的台面: 始终阻止默认交互(取走/放上物品)
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /**
     * 置物台被(玩家 / 其它 mod / 本模组的拆除模式)破坏时, 释放它配对的料堆,
     * 并作废该坐标上还在排队的「停留后弹出」条目。
     *
     * <p>本模组自己的拆除也走这条路: {@code DeconstructLogic} 交还给 {@code IWrenchable.onSneakWrenched},
     * 而 Create 的销毁路径会正常广播 {@code BlockEvent.BreakEvent}(审计已修复的那条分支同理)。</p>
     */
    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide)
            return;
        if (!(level.getBlockEntity(event.getPos()) instanceof DepotBlockEntity))
            return;
        DepotProductEjector.cancelAt(level, event.getPos());
        DepotPiles.releaseAll(level, event.getPos());
    }

    /**
     * 置物台被**爆炸**波及时同样释放料堆, 并作废该坐标上还在排队的「停留后弹出」条目
     * (否则置物台已经没了, 条目到点还会照弹一次那时台面上的东西)。
     *
     * <p>{@code Detonate} 在真正破坏方块/结算实体伤害**之前**触发, 所以此时释放还有意义 ——
     * 料堆会被推到置物台旁边, 有机会躲过这一发爆炸, 而不是跟着置物台一起消失。</p>
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide)
            return;
        for (BlockPos pos : event.getAffectedBlocks())
            if (level.getBlockEntity(pos) instanceof DepotBlockEntity) {
                DepotProductEjector.cancelAt(level, pos);
                DepotPiles.releaseAll(level, pos);
            }
    }

    /** 玩家登出: 清掉该玩家的「成品停留时间」记录, 避免长年运行的服务端无上限累积 UUID。 */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp)
            DepotStayState.clear(sp.getUUID());
    }
}
