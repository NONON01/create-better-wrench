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
 * 底部"模式选择器" —— 从 Create 的 ToolSelectionScreen 复刻(基于 MIT, 已替换工具→我们的模式,
 * 图标→我们的, 文字→我们的), 视觉与机械动力蓝图完全一致。
 *
 * <p><b>MIT 署名(硬性义务)</b>: Derived from Create's ToolSelectionScreen.
 * Create code is MIT-licensed — Copyright (c) The Create Team / The Creators of Create.
 * Full license text: see {@code licenses/Create-MIT.txt} inside this JAR
 * (and {@code THIRD_PARTY_NOTICES.md}).</p>
 *
 * <p>行为: 始终渲染(renderPassive 每帧); focused=按住 ALT 时清晰、顶部"[SCROLL] 循环"并显示描述 tooltip;
 * 未聚焦半透明、顶部"按住[ALT]..."; current=当前选中模式(上浮高亮)。</p>
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

    /** 每帧: focused 时 yOffset 平滑趋近 10, 否则趋近 0(淡入淡出)。 */
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
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, focused ? 7 / 8f : 1 / 2f);
        graphics.blit(bg.location, x - 15, y, bg.getStartX(), bg.getStartY(),
            w, h, bg.getWidth(), bg.getHeight());

        // tooltip(描述)面板: 聚焦且 yOffset 起来后显示
        float toolTipAlpha = yOffset / 10;
        if (toolTipAlpha > 0.25f && focused) {
            // 描述支持多行: 语言文件里写 \n 换行, 过长的行再由字体按面板宽度自动折行
            // 对齐规则: **多行左对齐**(两行居中会参差, 且 [右键]/[滚轮] 前缀左对齐更好读);
            //           **单行居中**(视觉上更平衡)。
            List<FormattedCharSequence> lines = mc.font.split(modes.get(selection).description(), w - 20);
            RenderSystem.setShaderColor(.7f, .7f, .8f, toolTipAlpha);
            graphics.blit(bg.location, x - 15, y + 33, bg.getStartX(), bg.getStartY(),
                w, h + 6 + lines.size() * 12, bg.getWidth(), bg.getHeight());
            RenderSystem.setShaderColor(1, 1, 1, 1);
            boolean multiLine = lines.size() > 1;
            int leftX = x - 15 + 10; // 面板左内边距
            int textY = y + 38;
            for (FormattedCharSequence line : lines) {
                int lineX = multiLine ? leftX : screenW / 2 - mc.font.width(line) / 2;
                graphics.drawString(mc.font, line, lineX, textY, 0xEEEEEE, false);
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
    }

    private void renderIcon(GuiGraphics graphics, WrenchMode mode, int ix, int iy, float alpha) {
        Object icon = mode.icon();
        if (icon == null)
            return; // 图标暂未放: 不绘制
        if (icon instanceof com.simibubi.create.foundation.gui.AllIcons ai) {
            RenderSystem.setShaderColor(0, 0, 0, alpha);
            ai.render(graphics, ix, iy);
            RenderSystem.setShaderColor(1, 1, 1, alpha);
            ai.render(graphics, ix, iy - 1);
        } else if (icon instanceof ResourceLocation rl) {
            RenderSystem.setShaderColor(1, 1, 1, alpha);
            graphics.blit(rl, ix, iy, 0, 0, 16, 16, 16, 16);
        }
    }
}
