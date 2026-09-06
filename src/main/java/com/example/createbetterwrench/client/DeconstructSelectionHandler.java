package com.example.createbetterwrench.client;

import com.example.createbetterwrench.BetterWrenchMod;
import com.example.createbetterwrench.mode.DeconstructScope;
import com.example.createbetterwrench.mode.WrenchMode;
import com.example.createbetterwrench.network.DeconstructPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端「拆除」选框状态机(仿 Create SchematicAndQuillHandler)。
 *
 * <p>仅当:手持我们的扳手 + 当前模式为 DECONSTRUCT 时生效。
 * 通过拦截客户端鼠标右键(MouseButton.Pre)抢在 Create WrenchEventHandler 之前吃掉本次点击,
 * 从而让右键只做"选角 A / 选角 B", 不会触发普通扳手的旋转/拆除。</p>
 *
 * <p>右键两次: 第一次定 A, 第二次定 B 并立即向服务端发送 DeconstructPayload 执行拆除。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class DeconstructSelectionHandler {

    private static BlockPos cornerA;

    private DeconstructSelectionHandler() {
    }

    private static boolean active(Minecraft mc) {
        return mc.player != null
            && (mc.player.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH)
                || mc.player.getOffhandItem().is(BetterWrenchMod.BETTER_WRENCH))
            && WrenchModeSwitcher.current == WrenchMode.DECONSTRUCT;
    }

    /** 右键按下: 仅在拆除模式下接管。返回 true 表示本 mod 消费了这次点击。 */
    private static boolean onRightClick() {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc))
            return false;
        BlockPos hit = rayTraceBlock(mc);
        if (hit == null)
            return true; // 没点到方块: 仍吃掉, 避免误触普通扳手

        if (cornerA == null) {
            cornerA = hit;
            mc.player.displayClientMessage(
                Component.literal("Corner A set: " + hit.toShortString()), true);
            return true;
        }
        // 第二次右键: 定 B 并发包
        BlockPos b = hit;
        DeconstructScope scope = WrenchModeSwitcher.deconstructScope;
        ClientPacketListener conn = mc.getConnection();
        if (conn != null)
            PacketDistributor.sendToServer(DeconstructPayload.create(cornerA, b, scope));
        mc.player.displayClientMessage(
            Component.literal("Deconstructing region A=" + cornerA.toShortString()
                + " B=" + b.toShortString() + " (" + scope.name() + ")"), false);
        cornerA = null;
        return true;
    }

    /** 玩家视线对某方块的射线(用于选角)。 */
    private static BlockPos rayTraceBlock(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK)
            return ((BlockHitResult) hit).getBlockPos();
        return null;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null)
            return;
        boolean pressed = event.getAction() == 1; // PRESS
        boolean isUseButton = event.getButton() == 1; // 右键
        if (!pressed || !isUseButton)
            return;
        if (active(mc) && onRightClick())
            event.setCanceled(true);
    }

    /** 供其它类查询当前是否已选 A(如 HUD 是否画提示)。 */
    public static boolean hasCornerA() {
        return cornerA != null;
    }

    /** 供其它类查询已选 A 的位置。 */
    public static BlockPos getCornerA() {
        return cornerA;
    }

    /** 潜行时取消已选的 A(供后续 Esc/Shift 取消)。 */
    public static void cancel() {
        cornerA = null;
    }
}
