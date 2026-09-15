package com.nonono.createbetterwrench.assemble;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 服务端: 已锁定的置物台被右键时 —— 先尝试「工作」模式的各种施加
 * (序列装配 / 机械手式施加 / 原木去皮 / 注液, 见 {@link AssembleLogic}),
 * 无论成功与否都**吃掉这次交互**, 从而不会把台面上的物品取下来。
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
}
