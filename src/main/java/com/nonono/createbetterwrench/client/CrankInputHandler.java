package com.nonono.createbetterwrench.client;

import org.lwjgl.glfw.GLFW;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.mode.WrenchMode;
import com.nonono.createbetterwrench.network.CrankPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端「曲柄」模式输入。
 *
 * <p><b>为什么要"按住"而不是单次点击:</b> 原版曲柄靠 {@code inUse} 倒计时实现"按住持续摇",
 * 这里用同样思路 —— 按住期间每隔几刻发一次 {@link CrankPayload}, 服务端超时未收到就自动停止并还原。</p>
 *
 * <p><b>为什么要吃掉 MouseButton.Pre:</b> 取消右键按下会让 {@code keyUse} 不被置为按下,
 * 于是 MC 的 {@code startUseItem} 不会再重复触发 —— 既避免扳手顺手把方块扭了/开了界面, 也避免重复开局。
 * 也正因为按键状态被吃掉, 判断"是否按住"必须直接查 GLFW 的物理按键状态。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class CrankInputHandler {

    /** 两次发包之间的间隔(刻), 与原版右键连发的 4 刻节奏接近。 */
    private static final int REPEAT_TICKS = 4;

    private static int cooldown;

    private CrankInputHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT || event.getAction() != GLFW.GLFW_PRESS)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc))
            return;
        event.setCanceled(true);
        cooldown = 0; // 按下立刻生效
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc) || !isRightButtonDown(mc)) {
            cooldown = 0;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        cooldown = REPEAT_TICKS;

        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK)
            return;
        BlockHitResult blockHit = (BlockHitResult) hit;
        PacketDistributor.sendToServer(new CrankPayload(
            blockHit.getBlockPos(), blockHit.getDirection(),
            WrenchModeSwitcher.crankRpm, mc.player.isShiftKeyDown()));
    }

    private static boolean active(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.screen != null)
            return false;
        if (WrenchModeSwitcher.current != WrenchMode.CRANK)
            return false;
        return mc.player.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH)
            || mc.player.getOffhandItem().is(BetterWrenchMod.BETTER_WRENCH);
    }

    /** 右键是否物理按下(KeyMapping 被我们吃掉了, 只能问 GLFW)。 */
    private static boolean isRightButtonDown(Minecraft mc) {
        return GLFW.glfwGetMouseButton(mc.getWindow().getWindow(),
            GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }
}
