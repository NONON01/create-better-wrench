package com.nonono.createbetterwrench.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.nonono.createbetterwrench.config.WrenchConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 「万能扳手」的**专用配置页面**(自绘, 模仿 Tweakeroo / malilib 那种配置界面)。
 *
 * <h2>长什么样</h2>
 * <ul>
 *   <li>顶部: 标题 + **搜索框**(输入关键字过滤选项, 名称/说明/TOML 路径都能匹配);</li>
 *   <li>中间: **可滚动的选项列表**, 每行 = 左边名称 + 右边**滑块** + `-` / `+` 微调按钮,
 *       悬停控件会显示该选项的说明(tooltip);</li>
 *   <li>右侧: 自绘滚动条; 底部: **重置为默认** / **完成** + 一行操作提示。</li>
 * </ul>
 *
 * <h2>怎么读写配置(走 NeoForge 官方路径)</h2>
 * <ul>
 *   <li>读: {@code ConfigValue#get()}; 范围与默认值取 {@code getSpec().getRange()} / {@code getDefault()};</li>
 *   <li>写: {@code ConfigValue#set(v)} —— 它会**立即更新内存缓存**(本 mod 的 getter 马上生效, **不必重进世界**),
 *       所以滑块一拖, 游戏里立刻按新值工作;</li>
 *   <li>落盘: {@code ConfigValue#save()} = {@code ModConfigSpec#save()}(写文件 + 触发重载事件)。
 *       本页面**只在关闭时统一保存一次**, 不在拖动过程中反复写盘。</li>
 * </ul>
 *
 * <p><b>⚠️ 只读情形</b>: 配置是 SERVER 类型。专用服务器上客户端拿不到服务端配置
 * ({@code ModConfigSpec#isLoaded()} 为 false, 此时 {@code get()} 会抛异常)⇒ 本页面自动变**只读**并给出提示。
 * 单人游戏里客户端与内置服务端同进程, 可以直接改。</p>
 */
public final class WrenchConfigScreen extends Screen {

    // ---------------------------------------------------------------- 布局常量
    private static final int ROW_H = 24;
    /** 列表区域垂直起点(标题与搜索框之下)。 */
    private static final int LIST_TOP = 58;
    /** 列表区域宽度(水平居中)。 */
    private static final int LIST_W = 404;
    private static final int LABEL_W = 150;
    private static final int SLIDER_W = 170;
    private static final int STEP_BTN_W = 22;

    private final Screen parent;
    private final List<WrenchConfig.Option> options = WrenchConfig.options();
    private final List<Row> rows = new ArrayList<>();

    private String query = "";
    private double scroll;
    private EditBox search;

    public WrenchConfigScreen(Screen parent) {
        super(Component.translatable("gui.create_better_wrench.config.title"));
        this.parent = parent;
    }

    /** 供 mod 列表的「配置」按钮等外部入口复用。 */
    public static Screen create(Screen parent) {
        return new WrenchConfigScreen(parent);
    }

    // ---------------------------------------------------------------- 构建

    @Override
    protected void init() {
        rows.clear();
        for (WrenchConfig.Option option : options)
            rows.add(new Row(option));

        int searchW = Math.min(240, width - 40);
        search = new EditBox(font, width / 2 - searchW / 2, 30, searchW, 18,
            Component.translatable("gui.create_better_wrench.config.search"));
        search.setHint(Component.translatable("gui.create_better_wrench.config.search"));
        search.setResponder(text -> {
            query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            scroll = 0;
            layoutRows();
        });
        addRenderableWidget(search);

        for (Row row : rows) {
            addRenderableWidget(row.slider);
            addRenderableWidget(row.minus);
            addRenderableWidget(row.plus);
        }

        int bottom = height - 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.create_better_wrench.config.reset"), b -> resetToDefaults())
            .bounds(width / 2 - 160, bottom, 150, 20)
            .tooltip(Tooltip.create(Component.translatable("gui.create_better_wrench.config.reset.tip")))
            .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.create_better_wrench.config.done"), b -> onClose())
            .bounds(width / 2 + 10, bottom, 150, 20)
            .build());

        search.setValue(query);
        layoutRows();
    }

    private void resetToDefaults() {
        if (!WrenchConfig.isWritable())
            return;
        for (Row row : rows)
            row.resetToDefault();
    }

    // ---------------------------------------------------------------- 布局 / 滚动

    private int listHeight() {
        return Math.max(ROW_H, height - LIST_TOP - 46);
    }

    private int visibleRowCount() {
        int n = 0;
        for (Row row : rows)
            if (row.matches(query))
                n++;
        return n;
    }

    /** 按过滤条件摆放行; 只有**完整落在列表区域内**的行才显示(避免画到标题/按钮上)。 */
    private void layoutRows() {
        int x = width / 2 - LIST_W / 2;
        double maxScroll = Math.max(0, visibleRowCount() * ROW_H - listHeight());
        scroll = Mth.clamp(scroll, 0, maxScroll);
        int index = 0;
        for (Row row : rows) {
            boolean match = row.matches(query);
            if (!match) {
                row.setVisible(false);
                continue;
            }
            int y = (int) Math.round(LIST_TOP - scroll) + index * ROW_H;
            index++;
            boolean fullyVisible = y >= LIST_TOP && y + ROW_H <= LIST_TOP + listHeight();
            if (!fullyVisible) {
                row.setVisible(false);
                continue;
            }
            row.layout(x, y);
            row.setVisible(true);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double maxScroll = Math.max(0, visibleRowCount() * ROW_H - listHeight());
        if (maxScroll > 0) {
            scroll = Mth.clamp(scroll - scrollY * 14, 0, maxScroll);
            layoutRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        WrenchConfig.saveAll();
        if (minecraft != null)
            minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        super.removed();
        WrenchConfig.saveAll();
    }

    // ---------------------------------------------------------------- 绘制

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = width / 2 - LIST_W / 2;
        int listBottom = LIST_TOP + listHeight();
        g.fill(0, 0, width, height, 0xC0101010);
        g.fill(x - 6, LIST_TOP - 6, x + LIST_W + 6, listBottom + 6, 0x60202020);
        g.renderOutline(x - 6, LIST_TOP - 6, LIST_W + 12, listHeight() + 12, 0x60FFFFFF);

        int total = visibleRowCount() * ROW_H;
        if (total > listHeight()) {
            int barX = x + LIST_W + 2;
            g.fill(barX, LIST_TOP, barX + 4, listBottom, 0x40FFFFFF);
            int thumbH = Math.max(16, listHeight() * listHeight() / total);
            int thumbY = LIST_TOP + (int) ((listHeight() - thumbH) * (scroll / Math.max(1, total - listHeight())));
            g.fill(barX, thumbY, barX + 4, thumbY + thumbH, 0xC0FFFFFF);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);

        int x = width / 2 - LIST_W / 2;
        for (Row row : rows)
            if (row.shown)
                g.drawString(font, row.label(), x + 6, row.y + 7, 0xE0E0E0, false);

        boolean writable = WrenchConfig.isWritable();
        Component hint = Component.translatable(writable ? "gui.create_better_wrench.config.hint" : "gui.create_better_wrench.config.readonly");
        g.drawCenteredString(font, hint, width / 2, height - 44, writable ? 0x909090 : 0xFFAA55);
    }

    // ---------------------------------------------------------------- 行

    /** 一行 = 一个配置项(名称 + 滑块 + 微调按钮)。 */
    private final class Row {
        private final WrenchConfig.Option option;
        private final Slider slider;
        private final Button minus;
        private final Button plus;
        private int y;
        private boolean shown;

        private Row(WrenchConfig.Option option) {
            this.option = option;
            this.slider = new Slider(option);
            this.slider.setTooltip(Tooltip.create(Component.translatable(option.descKey())));
            this.minus = Button.builder(Component.literal("-"), b -> step(-stepSize()))
                .tooltip(Tooltip.create(Component.translatable("gui.create_better_wrench.config.step.tip")))
                .bounds(0, 0, STEP_BTN_W, 20)
                .build();
            this.plus = Button.builder(Component.literal("+"), b -> step(stepSize()))
                .tooltip(Tooltip.create(Component.translatable("gui.create_better_wrench.config.step.tip")))
                .bounds(0, 0, STEP_BTN_W, 20)
                .build();
        }

        private boolean matches(String q) {
            if (q.isEmpty())
                return true;
            String label = label().getString().toLowerCase(Locale.ROOT);
            String desc = Component.translatable(option.descKey()).getString().toLowerCase(Locale.ROOT);
            return label.contains(q) || desc.contains(q) || option.path().contains(q);
        }

        private Component label() {
            return Component.translatable(option.labelKey());
        }

        /** 微调步长: ±1(小数项 ±0.5); Shift ×10; Ctrl ×100(小数项 ×50)。 */
        private double stepSize() {
            double base = option.isDouble() ? 0.5 : 1;
            if (Screen.hasControlDown() || Screen.hasAltDown())
                return base * (option.isDouble() ? 50 : 100);
            if (Screen.hasShiftDown())
                return base * 10;
            return base;
        }

        private void step(double delta) {
            if (!WrenchConfig.isWritable())
                return;
            option.set(Mth.clamp(option.get() + delta, option.min(), option.max()));
            slider.syncFromOption();
        }

        private void resetToDefault() {
            option.set(Mth.clamp(option.getDefault(), option.min(), option.max()));
            slider.syncFromOption();
        }

        private void setVisible(boolean visible) {
            this.shown = visible;
            boolean editable = visible && WrenchConfig.isWritable();
            slider.visible = visible;
            slider.active = editable;   // 只读时滑块也点不动(applyValue 另有兜底)
            minus.visible = editable;
            plus.visible = editable;
        }

        private void layout(int listX, int rowY) {
            this.y = rowY;
            int sliderX = listX + 6 + LABEL_W + STEP_BTN_W + 6;
            minus.setPosition(listX + 6 + LABEL_W + 2, rowY + 2);
            slider.setPosition(sliderX, rowY + 2);
            plus.setPosition(sliderX + SLIDER_W + 4, rowY + 2);
        }
    }

    /** 数值滑块: 拖动即改配置(内存), 显示"TOML 路径 = 值"。 */
    private final class Slider extends AbstractSliderButton {
        private final WrenchConfig.Option option;

        private Slider(WrenchConfig.Option option) {
            super(0, 0, SLIDER_W, 20, Component.empty(), norm(option, option.get()));
            this.option = option;
        }

        private static double norm(WrenchConfig.Option option, double value) {
            double span = option.max() - option.min();
            return span <= 0 ? 0 : Mth.clamp((value - option.min()) / span, 0, 1);
        }

        private double raw() {
            return option.min() + value * (option.max() - option.min());
        }

        /** 外部(重置 / 微调)改了配置后, 把滑块拉回同步。
         *  ⚠️ 1.21.1 的 {@code AbstractSliderButton} **没有** setValue(): 只能直接写它 protected 的 {@code value} 字段。 */
        private void syncFromOption() {
            this.value = norm(option, option.get());
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(option.path() + " = " + format(raw())));
        }

        @Override
        protected void applyValue() {
            if (!WrenchConfig.isWritable())
                return;
            option.set(Mth.clamp(option.isDouble() ? raw() : Math.round(raw()), option.min(), option.max()));
            updateMessage();
        }

        private String format(double v) {
            return option.isDouble() ? String.format(Locale.ROOT, "%.1f", v) : Long.toString(Math.round(v));
        }
    }
}
