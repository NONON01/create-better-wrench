package com.example.createbetterwrench.client;

import com.example.createbetterwrench.BetterWrenchMod;
import com.example.createbetterwrench.mode.WrenchMode;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import net.minecraft.client.DeltaTracker;
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
 * 客户端: 底部"模式工具条"(仿 Create 蓝图部署的 ToolSelection 风格)。
 *
 * <p>仅在玩家手持扳手时绘制。平时很淡/几乎隐藏, 按住 TOOLS_KEY(默认 ALT)聚焦后变清晰,
 * 松开后逐渐淡出(淡入淡出靠 displayAlpha / yOffset 每帧插值)。
 * 底部横条中央显示各模式(当前项上浮高亮), Ctrl+滚轮/ALT+滚轮 切换见 WrenchInputHandler。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WrenchHud {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "wrench_mode_bar");

    /** 视觉聚焦进度 0..1(用于淡入淡出)。 */
    private static float focusAmount;
    /** 工具条向上浮起量(聚焦时上移)。 */
    private static float lift;
    /** 模式选择器(懒加载)。 */
    private static WrenchToolSelection selection;

    private WrenchHud() {
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, WrenchHud::renderLayer);
    }

    private static void renderLayer(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null)
            return;
        boolean holding = isHoldingOurWrench(mc);
        boolean focused = holding && WrenchModeSwitcher.TOOLS_KEY.isDown();

        // 淡入淡出由 selection.update() 内部(yOffset)处理; 未持扳手时若完全淡出则不画
        float target = focused ? 1f : 0f;
        focusAmount += (target - focusAmount) * 0.15f;
        if (!holding && focusAmount < 0.02f)
            return;

        WrenchToolSelection sel = getSelection();
        sel.focused = focused;
        sel.update();
        sel.render(g, deltaTracker.getGameTimeDeltaPartialTick(false));
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
                    mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(hint), true);
            }
            return true;
        }

        // 规则二: 切"模式本身"仍需按 ALT(TOOLS_KEY)聚焦后滚轮, 否则放行(让滚轮正常切物品)。
        if (!WrenchModeSwitcher.TOOLS_KEY.isDown())
            return false;

        // 同步切换 selection(权威 current)并让 current 跟随
        WrenchToolSelection sel = getSelection();
        sel.setSelected(WrenchModeSwitcher.current);
        sel.cycle(dir);
        WrenchModeSwitcher.current = sel.getSelected();
        return true;
    }

    /** 懒加载模式选择器(保持与 WrenchModeSwitcher.current 同步)。 */
    private static WrenchToolSelection getSelection() {
        if (selection == null) {
            selection = new WrenchToolSelection(java.util.Arrays.asList(WrenchMode.values()));
            selection.setSelected(WrenchModeSwitcher.current);
        }
        return selection;
    }

    /** 该模式是否有可循环的 Ctrl 选项。 */
    private static boolean modeHasCtrlOption(WrenchMode mode) {
        return mode == WrenchMode.DECONSTRUCT;
    }
}
