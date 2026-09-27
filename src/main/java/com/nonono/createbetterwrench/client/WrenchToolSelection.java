package com.nonono.createbetterwrench.client;

import java.util.List;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.mode.WrenchMode;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * 底部模式选择器: 一行横向排列的模式图标条, 每个图标对应一个 {@link WrenchMode}。
 *
 * <p>本类改编自 Create 的 {@code ToolSelectionScreen}(MIT 许可): 工具列表换成本模组的模式,
 * 图标与文字换成本模组自己的资源, 视觉上与 Create 蓝图工具条完全一致。</p>
 *
 * <p><b>MIT 署名(硬性义务)</b>: Derived from Create's ToolSelectionScreen.
 * Create code is MIT-licensed — Copyright (c) The Create Team / The Creators of Create.
 * Full license text (Create's MIT notice, verbatim): see {@code LICENSE.md} inside this JAR,
 * Appendix A. That single file also carries this mod's own terms and every third-party notice.</p>
 *
 * <p>生命周期: {@link #update()} 由 {@code client/WrenchHud} 每客户端刻推进一次(20Hz);
 * {@link #render} 由 HUD 渲染层每帧调用(主手持扳手期间, 以及淡出尚未结束的帧)。</p>
 *
 * <p>状态语义: {@code focused} = 按住 ALT 时为真, 图标清晰、顶部显示
 * {@code hint.create_better_wrench.toolbar.scroll} 并显示描述 tooltip; 未聚焦时半透明、顶部显示
 * {@code hint.create_better_wrench.toolbar.focus}; {@code selection} = 当前选中模式(上浮高亮)。</p>
 */
public final class WrenchToolSelection {

    private final List<WrenchMode> modes;
    public boolean focused;
    private float yOffset;
    private int selection;

    public WrenchToolSelection(List<WrenchMode> modes) {
        this.modes = modes;
        this.focused = false;
        this.yOffset = 0;
        this.selection = 0;
    }

    public WrenchMode getSelected() {
        return modes.get(selection);
    }

    public void setSelected(WrenchMode mode) {
        int idx = modes.indexOf(mode);
        if (idx >= 0)
            selection = idx;
    }

    public void cycle(int direction) {
        selection += (direction < 0) ? 1 : -1;
        selection = (selection + modes.size()) % modes.size();
    }

    /** 每客户端刻: focused 时 yOffset 平滑趋近 10, 否则趋近 0(淡入淡出)。 */
    public void update() {
        if (focused)
            yOffset += (10 - yOffset) * .1f;
        else
            yOffset *= .9f;
    }

    /** 每帧绘制(仿 Create ToolSelectionScreen.draw)。 */
    public void render(GuiGraphics graphics, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        Window win = mc.getWindow();
        int screenW = win.getGuiScaledWidth();
        int screenH = win.getGuiScaledHeight();

        int n = modes.size();
        int w = Math.max(n * 50 + 30, 220);
        int h = 30;
        int x = (screenW - w) / 2 + 15;
        int y = screenH - h - 75;

        AllGuiTextures bg = AllGuiTextures.HUD_BACKGROUND;

        // 与 Create 蓝图工具条一致: 整块随 yOffset 上浮, 聚焦时再抬到 z=100 盖在其它 GUI 之上。
        // (Create: matrixStack.translate(0, -yOffset, focused ? 100 : 0))
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, -yOffset, focused ? 100 : 0);

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, focused ? 7 / 8f : 1 / 2f);
        graphics.blit(bg.location, x - 15, y, bg.getStartX(), bg.getStartY(),
            w, h, bg.getWidth(), bg.getHeight());

        // 下方 tooltip(描述)面板: 面板与文字一起淡入(yOffset 越大越不透明)
        float toolTipAlpha = yOffset / 10;
        if (toolTipAlpha > 0.25f) {
            // 描述支持多行: 语言文件里写 \n 换行, 过长的行再由字体按面板宽度自动折行。
            // (对齐规则见下方 centerText 处的说明。)
            List<FormattedCharSequence> lines = mc.font.split(modes.get(selection).description(), w - 20);
            RenderSystem.setShaderColor(.7f, .7f, .8f, toolTipAlpha);
            // 面板高度与 Create 一致固定为 h + 22, 不随行数增长: 若按行数加高, 面板会向下扩张,
            // 底边压到物品栏上。Create 面板 y+33 起、高 52, 底边在 y+85; 聚焦上浮 10 后底边 = screenH-30, 正好让开物品栏。
            graphics.blit(bg.location, x - 15, y + 33, bg.getStartX(), bg.getStartY(),
                w, h + 22, bg.getWidth(), bg.getHeight());
            RenderSystem.setShaderColor(1, 1, 1, 1);

            // 文字自身也带 alpha(与 Create 一样把 alpha 编进颜色), 否则面板淡入时文字会直接出现
            int textAlpha = ((int) (toolTipAlpha * 0xFF)) << 24;

            // 对齐规则:
            //   ① 默认 —— 多行左对齐(两行居中会参差, 且 [右键]/[滚轮] 前缀左对齐更好读), 单行居中;
            //   ② 特例 —— [扳手] 模式永远居中(设计约定, 2026-09-20): 其描述见语言文件
            //      mode.create_better_wrench.wrench.desc, 共两行, 需要整段居中显示,
            //      因此该模式不受"多行左对齐"这条通用规则约束。
            boolean centerText = modes.get(selection) == WrenchMode.WRENCH || lines.size() <= 1;
            int leftX = x - 15 + 10; // 面板左内边距(多行左对齐时用)
            int textY = y + 38;
            for (FormattedCharSequence line : lines) {
                // 居中位置 = 屏幕中心 = 面板中心(面板本身水平居中, 所以两者等价)
                int lineX = centerText ? screenW / 2 - mc.font.width(line) / 2 : leftX;
                graphics.drawString(mc.font, line, lineX, textY, 0xEEEEEE + textAlpha, false);
                textY += 12;
            }
        }

        RenderSystem.setShaderColor(1, 1, 1, 1);
        // 顶部提示(文案在语言文件: hint.<modid>.toolbar.*)
        Component topHint = focused
            ? Component.translatable("hint." + BetterWrenchMod.MODID + ".toolbar.scroll")
            : Component.translatable("hint." + BetterWrenchMod.MODID + ".toolbar.focus",
                WrenchModeSwitcher.TOOLS_KEY.getTranslatedKeyMessage());
        graphics.drawCenteredString(mc.font, topHint, screenW / 2, y - 10, WrenchMode.HINT_BLUE);

        // 各模式图标
        for (int i = 0; i < n; i++) {
            RenderSystem.enableBlend();
            PoseStack ms = graphics.pose();
            ms.pushPose();
            float alpha = focused ? 1 : .2f;
            if (i == selection) {
                ms.translate(0, -10, 0);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                graphics.drawCenteredString(mc.font, modes.get(i).displayName(),
                    x + i * 50 + 24, y + 28, 0xCCDDFF);
                alpha = 1;
            }
            renderIcon(graphics, modes.get(i), x + i * 50 + 16, y + 12, alpha);
            ms.popPose();
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private void renderIcon(GuiGraphics graphics, WrenchMode mode, int ix, int iy, float alpha) {
        // 图标全部是本模组自绘的 PNG(ResourceLocation) —— 不再有任何 Create 蓝图图标(AllIcons)分支。
        // 这样 LICENSE.md §2.1 里"不再引用 Create 的 AllIcons"才是代码上可核实的。
        ResourceLocation icon = mode.icon();
        if (icon == null)
            return;
        RenderSystem.setShaderColor(1, 1, 1, alpha);
        graphics.blit(icon, ix, iy, 0, 0, 16, 16, 16, 16);
    }
}
