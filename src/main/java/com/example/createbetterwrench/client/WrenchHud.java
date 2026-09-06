package com.example.createbetterwrench.client;

import com.example.createbetterwrench.BetterWrenchMod;
import com.example.createbetterwrench.mode.WrenchMode;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * 客户端:底部"模式工具条"的注册与输入接线。
 *
 * <p>仅在玩家手持我们的扳手、且正按住 TOOLS_KEY(默认 ALT)时显示底部条并允许滚轮循环切换模式。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WrenchHud {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "wrench_mode_bar");

    private WrenchHud() {
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, WrenchHud::renderLayer);
    }

    private static void renderLayer(GuiGraphics g, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null)
            return;
        if (!isHoldingOurWrench(mc))
            return;

        // 聚焦(按住 ALT)时全亮 + 可滚轮切换; 否则半透明提示
        boolean focused = WrenchModeSwitcher.TOOLS_KEY.isDown();
        float alpha = focused ? 1f : 0.35f;

        WrenchMode[] modes = WrenchMode.values();
        Window win = mc.getWindow();
        int width = win.getGuiScaledWidth();
        int height = win.getGuiScaledHeight();

        // 每个模式一格, 底部一行
        int cellW = 60;
        int totalW = modes.length * cellW;
        int x0 = (width - totalW) / 2;
        int y = height - 40;

        for (int i = 0; i < modes.length; i++) {
            boolean sel = modes[i] == WrenchModeSwitcher.current;
            int x = x0 + i * cellW;
            int cy = sel ? y - 6 : y; // 选中项上浮
            int color = sel ? 0xFFEEEEEE : 0xCC888888;
            g.fill(x, cy, x + cellW, cy + 18, (int) (alpha * 255) << 24 | (sel ? 0x303030 : 0x101010));
            g.drawCenteredString(mc.font, modes[i].displayName(), x + cellW / 2, cy + 5, color);
        }

        // 帮助文本(未聚焦时提示按 ALT 聚焦并滚轮)
        if (!focused) {
            String hint = "Hold " + WrenchModeSwitcher.TOOLS_KEY.getTranslatedKeyMessage().getString()
                + " to focus mode bar";
            g.drawCenteredString(mc.font, hint, width / 2, y - 16, 0xCCFFFFFF);
        }

        // 当前模式的 Ctrl 选项(如拆除范围)提示
        String ctrlOpt = WrenchModeSwitcher.ctrlOptionHint();
        if (!ctrlOpt.isEmpty())
            g.drawCenteredString(mc.font, "Ctrl+Scroll: " + ctrlOpt, width / 2, y + 22, 0xCCCCFF);
    }

    private static boolean isHoldingOurWrench(Minecraft mc) {
        return mc.player.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH)
            || mc.player.getOffhandItem().is(BetterWrenchMod.BETTER_WRENCH);
    }

    /** 由 GAME 总线客户端事件调用: 处理扳手模式/选项的滚轮切换, 返回 true 表示已消费本次滚动。 */
    public static boolean onMouseScrolled(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null)
            return false;
        if (!isHoldingOurWrench(mc))
            return false;
        double delta = event.getScrollDeltaY();
        if (delta == 0)
            return true;
        int dir = (int) Math.signum(delta);

        // 规则一: 当前模式有自己的"Ctrl 选项"(如拆除范围)时, 按住 Ctrl+滚轮即切换(无需再按 ALT)。
        // 这样避免与"滚轮切换热键栏物品"冲突——必须先在此消费并返回。
        if (net.minecraft.client.gui.screens.Screen.hasControlDown()
            && modeHasCtrlOption(WrenchModeSwitcher.current)) {
            Object opt = WrenchModeSwitcher.cycleCtrlOption(dir);
            if (opt != null) {
                String hint = WrenchModeSwitcher.ctrlOptionHint();
                if (!hint.isEmpty())
                    mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Ctrl: " + hint), true);
            }
            return true;
        }

        // 规则二: 切"模式本身"仍需按 ALT(TOOLS_KEY)聚焦后滚轮, 否则放行(让滚轮正常切物品)。
        if (!WrenchModeSwitcher.TOOLS_KEY.isDown())
            return false;

        WrenchModeSwitcher.cycle(dir);
        return true;
    }

    /** 该模式是否有可循环的 Ctrl 选项。 */
    private static boolean modeHasCtrlOption(WrenchMode mode) {
        return mode == WrenchMode.DECONSTRUCT;
    }
}
