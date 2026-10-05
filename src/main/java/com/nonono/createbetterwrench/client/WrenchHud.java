package com.nonono.createbetterwrench.client;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.mode.WrenchMode;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;

/**
 * 客户端: 底部"模式工具条"(仿 Create 蓝图部署的 ToolSelection 风格)。
 *
 * <p>仅在玩家手持扳手时绘制。平时很淡/几乎隐藏, 按住 TOOLS_KEY(默认 ALT)聚焦后变清晰,
 * 松开后逐渐淡出(淡入淡出靠每帧插值的 yOffset, 见 {@link WrenchToolSelection#update()} 与
 * {@link WrenchToolSelection#render})。
 * 底部横条中央显示各模式(当前项上浮高亮), Ctrl+滚轮/ALT+滚轮 切换见 WrenchInputHandler。</p>
 *
 * <p>HUD 层的注册走<b>显式注册</b>(见 {@code client/BetterWrenchClient} 的 {@code @Mod(dist = Dist.CLIENT)} 入口) ——
 * NeoForge 21.1 已把 {@code @EventBusSubscriber(bus = ...)} 标记为待删除, MOD 总线的事件不再用注解订阅。</p>
 */
public final class WrenchHud {

    private static final ResourceLocation LAYER_ID =
        new ResourceLocation(BetterWrenchMod.MODID, "wrench_mode_bar");

    /** 视觉聚焦进度 0..1(用于淡入淡出)。 */
    private static float focusAmount;
    /** 模式选择器(懒加载)。 */
    private static WrenchToolSelection selection;

    private WrenchHud() {
    }

    /** MOD 总线: 把工具条注册到最顶层。由 {@code client/BetterWrenchClient} 显式注册。 */
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        // 1.20.1: HUD 叠加层用 Forge 的 RegisterGuiOverlaysEvent + IGuiOverlay
        //   (1.21 的 LayeredDraw / DeltaTracker 在这一版不存在)。
        event.registerAboveAll(LAYER_ID.getPath(), (gui, g, partialTick, screenWidth, screenHeight) ->
            WrenchHud.renderLayer(g, partialTick));
    }

    /**
     * 状态推进: <b>每客户端刻一次(20Hz)</b>, 与原版一致 —— 原版是在 {@code ClientTickEvent.Post} 里调
     * {@code SchematicHandler.tick()} 再调 {@code ToolSelectionScreen.update()}。
     * 本模组早先把这一步放在每帧渲染里, 帧率 60~200 时动画速度快了 3~10 倍。
     */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null)
            return;

        boolean holding = isHoldingOurWrench(mc);
        boolean focused = holding && WrenchModeSwitcher.TOOLS_KEY.isDown();
        float target = focused ? 1f : 0f;
        focusAmount += (target - focusAmount) * 0.15f;

        WrenchToolSelection sel = getSelection();
        sel.focused = focused;
        sel.update(); // yOffset 插值(上浮 / 落回)
    }

    /** 渲染层: 只负责画(转交 {@link WrenchToolSelection#render}), 不再推进状态。 */
    private static void renderLayer(GuiGraphics g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null)
            return;
        if (!isHoldingOurWrench(mc) && focusAmount < 0.02f)
            return;
        getSelection().render(g, partialTick);
    }

    /** GAME 总线: 每客户端刻推进动画状态(与原版 SchematicHandler 同一节奏)。 */
    @EventBusSubscriber(modid = BetterWrenchMod.MODID, value = Dist.CLIENT)
    public static final class Tick {
        private Tick() {
        }

        @SubscribeEvent
        public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
            WrenchHud.tick();
        }
    }

    /**
     * 是否<b>主手</b>持着万能扳手 —— 工具条只在这种情况下显示、也只在此时响应滚轮。
     *
     * <p>2026-09-20(设计约定): 扳手在<b>副手</b>时"只作普通扳手", 因此<b>不显示 HUD</b>,
     * ALT+滚轮也不切模式(滚轮照常切物品栏)。</p>
     */
    private static boolean isHoldingOurWrench(Minecraft mc) {
        return mc.player.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH.get());
    }

    /** 由 GAME 总线客户端事件调用: 处理扳手模式/选项的滚轮切换, 返回 true 表示已消费本次滚动。 */
    public static boolean onMouseScrolled(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null)
            return false;
        if (!isHoldingOurWrench(mc))
            return false;
        double delta = event.getScrollDelta();
        if (delta == 0)
            return true;
        int dir = (int) Math.signum(delta);

        // 规则一: 当前模式有自己的"Ctrl 选项"(如拆除范围)时, 按住 Ctrl+滚轮即切换(无需再按 ALT)。
        // 这样避免与"滚轮切换热键栏物品"冲突, 因此必须先在此消费并返回。
        if (net.minecraft.client.gui.screens.Screen.hasControlDown()
            && modeHasCtrlOption(WrenchModeSwitcher.current)) {
            Object opt = WrenchModeSwitcher.cycleCtrlOption(dir);
            // opt == null 表示"这次切换的结果要等服务端授权后再显示"(战斗模式, 见 WrenchCombatClient),
            // 此时不要乐观显示, 否则无权限时会先冒出"战斗模式"再被推翻。
            if (opt != null)
                showCtrlOptionHint();
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
        // 切到"被配置关掉"的功能时提示功能未启用(模式本身仍然切过去, 只是不能用)
        ClientFeatureGate.announceIfDisabled(WrenchModeSwitcher.current);
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

    /**
     * 断开连接时复位工具条(见 docs/design/known-issues.md A-12)。
     *
     * <p>必须与 {@link WrenchModeSwitcher#reset()} <b>成对</b>调用: 画高亮的依据是选择器<b>内部的下标</b>
     * ({@code WrenchToolSelection.render} 里的 {@code if (i == selection)}), 只重置 current 的话,
     * 工具条会一直高亮"退出世界前那个模式", 直到玩家第一次 ALT+滚轮才对齐。
     * 这里直接把单例丢掉 —— 下次 {@link #getSelection()} 会按 current 重建。</p>
     */
    public static void reset() {
        selection = null;
        focusAmount = 0f;
    }

    /** 该模式是否有可循环的 Ctrl 选项。 */
    private static boolean modeHasCtrlOption(WrenchMode mode) {
        return mode == WrenchMode.DECONSTRUCT || mode == WrenchMode.CONNECT
            || mode == WrenchMode.ASSEMBLE || mode == WrenchMode.CHAIN
            || mode == WrenchMode.COMING_SOON;
    }

    /**
     * 在 actionbar 显示"当前模式的 Ctrl 选项"提示。
     *
     * <p>本地就能定的开关(拆除范围 / 拐角类型 / 成品停留)在滚动时立刻调用它;
     * 战斗模式则要等服务端权威回包后再调用(见 {@code WrenchCombatClient#onPlayerTick})。</p>
     */
    public static void showCtrlOptionHint() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;
        net.minecraft.network.chat.Component hint = WrenchModeSwitcher.ctrlOptionHint();
        if (hint != null)
            mc.player.displayClientMessage(hint, true);
    }
}
