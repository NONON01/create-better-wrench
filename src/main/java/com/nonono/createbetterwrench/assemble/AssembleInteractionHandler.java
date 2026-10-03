package com.nonono.createbetterwrench.assemble;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * 服务端: 已锁定的置物台被右键时, 先尝试「工作」模式的各种施加
 * (序列装配 / 机械手式施加 / 原木去皮 / 注液, 见 {@link AssembleLogic}),
 * 无论成功与否都<b>吃掉这次交互</b>, 从而不会把台面上的物品取下来。
 *
 * <p>另外负责<b>置物台被破坏/炸毁时释放料堆</b> —— 否则那两个掉落物实体会永久停留在
 * {@code pickupDelay=32767} + {@code age=-32768}(既拿不走也不会消失), 导致物品被直接丢掉。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID)
public final class AssembleInteractionHandler {

    private AssembleInteractionHandler() {
    }

    /**
     * 右键方块。
     *
     * <p><b>必须 {@code priority = HIGHEST} + {@code receiveCanceled = true}</b>(2026-10-03 修复):
     * Create 的工作盆交互会在更高优先级处理并取消该事件, 而订阅默认不接收已取消事件,
     * 因此工作盆的右键在本处理器中不会被调用 —— 现象为重锤右键仍由 Create 取走盆内物品。</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide)
            return;
        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);

        // ⓪ 已锁定的工作盆(2026-10-03 新增): 手持锤类物品右键 = 执行一次压缩(冲压机 + 工作盆那一类配方)。
        //    与置物台不同, 工作盆没有"台面物品""料堆"这些概念, 因此走独立的一条判定, 成功与否都吃掉这次交互
        //    (锁定期间不允许再往盆里放/取物品)。没有配方时什么也不发生。
        if (be instanceof BasinBlockEntity basin) {
            Player basinPlayer = event.getEntity();
            ItemStack inHand = event.getItemStack();
            boolean locked = AssembleLock.isLocked(basin);

            // 手持锤类物品: 直接尝试压缩, 「不要求先锁定」 —— 锁定与否只决定"是否阻止 Create 的原生右键取出",
            //   不影响能否锻造(2026-10-03: 原先要求已锁定, 于是锁定一旦没成功就完全没有反应)。
            if (Forging.tryOnBasin(level, pos, basin, basinPlayer, inHand, event.getHand())) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }

            BetterWrenchMod.LOGGER.info("[CBW/加工] 右键工作盆 {}: 已锁定={}, 手持={}, 未执行任何配方",
                pos, locked, inHand.getHoverName().getString());
            // 临时诊断(定位工作盆问题期间保留): 让玩家直接看到本模组确实收到了这次右键
            basinPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "msg." + BetterWrenchMod.MODID + ".assemble.forge_not_hammer"), true);

            // 已锁定: 阻止 Create 把盆内物品直接取出来(未锁定则交回 Create 的原生行为)
            if (locked) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
            return;
        }

        if (!(be instanceof DepotBlockEntity depot))
            return;
        if (!AssembleLock.isLocked(depot))
            return;

        Player player = event.getEntity();
        ItemStack held = event.getItemStack();

        // ① 潜行 + 右键 = 从锁定台面上取回当前那一个(设计约定, 2026-09-22; 不需要先解锁)。
        //    与解锁返还不同: 这里只动台面那一个, 保持锁定、原料堆原地不动。
        //    装不下的部分由原版 placeItemBackInInventory 掉在脚下, 不会丢。
        //    若该坐标上还有停留后弹出的待弹条目, 到点时因台面物品对不上会被自动跳过
        //    (DepotProductEjector 的 A-6 守卫)。
        if (player.isShiftKeyDown() && !depot.getHeldItem().isEmpty()) {
            ItemStack taken = depot.getHeldItem().copy();
            AssembleLogic.setDepot(depot, ItemStack.EMPTY);            // 同包, 会顺带 notifyUpdate
            player.getInventory().placeItemBackInInventory(taken);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        // ② 主流程: 在已锁定的置物台上尝试加工/施加
        boolean acted = AssembleLogic.tryAssemble(level, pos, depot, player, held, event.getHand());

        // ③ 加工没有发生 + 台面仍然是空的 + 手上拿着东西 + 没有潜行时不吃掉这次交互,
        //    交给 Create 的置物台把手上那一摞放上台面(设计约定, 2026-09-22; 含扳手, 见 docs/log/01-operations.md)。
        //    注意: 判定放在 tryAssemble 之后 —— 这样台面空但原料堆有货 + 手持工具时仍会先自动续料并加工
        //    (② 会 success), 不会因为这条分支把工具当成材料放上台面、也不会把原料堆的东西倒进玩家背包。
        if (!acted && !player.isShiftKeyDown() && depot.getHeldItem().isEmpty() && !held.isEmpty())
            return;

        // 锁定的台面: 其余情况始终阻止默认交互(取走/放上物品)
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
     * 置物台被<b>爆炸</b>波及时同样释放料堆, 并作废该坐标上还在排队的「停留后弹出」条目
     * (否则置物台已经没了, 条目到点还会照弹一次那时台面上的东西)。
     *
     * <p>{@code Detonate} 在真正破坏方块/结算实体伤害<b>之前</b>触发, 所以此时释放还有意义 ——
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
