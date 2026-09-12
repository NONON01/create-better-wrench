package com.example.createbetterwrench.client;

import com.example.createbetterwrench.BetterWrenchMod;
import com.example.createbetterwrench.mode.WrenchMode;
import com.example.createbetterwrench.network.AssemblePayload;
import com.simibubi.create.content.logistics.depot.DepotBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端「装配」模式: 手持扳手 + 模式=ASSEMBLE 时, 右击置物台 → 发包切换锁定状态并吃掉本次点击。
 *
 * <p>注意: 只有"手持扳手"时才会被本处理器接管(用于锁定/解锁); 当玩家手持装配用的物品(如小齿轮)时
 * 不拦截, 右键会正常发到服务端, 由 {@code AssembleInteractionHandler} 在"已锁定"的置物台上执行装配。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class AssembleSelectionHandler {

    private AssembleSelectionHandler() {
    }

    private static boolean active(Minecraft mc) {
        return mc.player != null
            && (mc.player.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH)
                || mc.player.getOffhandItem().is(BetterWrenchMod.BETTER_WRENCH))
            && WrenchModeSwitcher.current == WrenchMode.ASSEMBLE;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.level == null)
            return;
        if (event.getAction() != 1 || event.getButton() != 1) // 右键按下
            return;
        if (!active(mc))
            return;
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK)
            return;
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        if (!(mc.level.getBlockState(pos).getBlock() instanceof DepotBlock))
            return;

        ClientPacketListener conn = mc.getConnection();
        if (conn != null)
            PacketDistributor.sendToServer(new AssemblePayload(pos));
        event.setCanceled(true);
    }
}
