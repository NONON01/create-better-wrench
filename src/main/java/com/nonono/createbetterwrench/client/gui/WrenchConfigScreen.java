package com.nonono.createbetterwrench.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.nonono.createbetterwrench.config.WrenchConfig;

import net.minecraft.ChatFormatting;
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
 * <h2>2026-09-25 重构(用户要求"优化配置页面结构")</h2>
 * <p>页面按**功能分组**展示, 每组第一行是该功能的**总开关**, 下面是它的子开关与数值:</p>
 * <pre>
 *   [连接]      总开关 · 拐点上限 · 单段轴长 · 单次方块数
 *   [拆除]      总开关 · 允许机械动力方块 · 允许红石方块 · 选区上限 · 每刻处理格数
 *   [加工]      总开关 · 装配 · 注液 · 洗涤 · 冶炼 · 烤制 · 缠魂
 *   [战斗]      是否启用 · 需要的权限等级(普通 / OP)
 * </pre>
 * <p>控件: 布尔 = 开/关按钮; 数值 = 滑块 + `-`/`+` 微调; 权限等级 = 普通 ↔ OP 两档按钮。
 * 开关之间有联动规则(见 {@code WrenchConfig#applyToggle}), 所以任何一次点击之后会把**所有行**刷新一遍。</p>
 *
 * <h2>怎么读写配置(走 NeoForge 官方路径)</h2>
 * <ul>
 *   <li>读: {@code ConfigValue#get()}; 范围与默认值取 {@code getSpec().getRange()} / {@code getDefault()};</li>
 *   <li>写: {@code ConfigValue#set(v)} —— 立即更新内存缓存(本 mod 的 getter 马上生效, 不必重进世界);</li>
 *   <li>落盘: {@code ModConfigSpec#save()}, 本页面**只在关闭时统一保存一次**, 不在拖动过程中反复写盘。</li>
 * </ul>
 *
 * <p><b>⚠️ 只读情形</b>: 配置是 SERVER 类型。专用服务器上客户端拿不到服务端配置
 * ({@code ModConfigSpec#isLoaded()} 为 false)⇒ 本页面自动变**只读**并给出提示
 * (此时开关值来自服务端下发的快照, 见 {@code config/FeatureToggles})。</p>
 */
public final class WrenchConfigScreen extends Screen {

    // ---------------------------------------------------------------- 布局常量
    private static final int ROW_H = 24;
    private static final int HEADER_H = 22;
    /** 列表区域垂直起点(标题与搜索框之下)。 */
    private static final int LIST_TOP = 58;
    /** 列表区域宽度(水平居中)。 */
    private static final int LIST_W = 470;
    private static final int LABEL_W = 240;
    private static final int CTL_W = 170;
    private static final int STEP_BTN_W = 22;

    private final Screen parent;
    /** 列表内容 = 分组标题 + 选项行(顺序与 {@link WrenchConfig#rows()} 一致)。 */
    private final List<Entry> entries = new ArrayList<>();

    private String query = "";
    private double scroll;
    private EditBox search;
    /** 「重置为默认」按钮: 只读(专用服务器客户端)时会被禁用, 免得点了没反应。 */
    private Button resetButton;

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
        entries.clear();
        String lastGroup = null;
        // 内部类不能有 static 工厂方法 ⇒ 借一个实例来造分组标题
        Entry factory = new Entry(true, "", null);
        for (WrenchConfig.Row row : WrenchConfig.rows()) {
            if (!row.group().equals(lastGroup)) {
                entries.add(factory.header(row.group()));
                lastGroup = row.group();
            }
            entries.add(factory.option(row));
        }

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

        for (Entry entry : entries) {
            if (!entry.isHeader)
                entry.createWidgets();
        }

        int bottom = height - 30;
        resetButton = Button.builder(Component.translatable("gui.create_better_wrench.config.reset"), b -> resetToDefaults())
            .bounds(width / 2 - 160, bottom, 150, 20)
            .tooltip(Tooltip.create(Component.translatable("gui.create_better_wrench.config.reset.tip")))
            .build();
        // 只读时把「重置为默认」也禁掉(旧写法点了静默无效, 让人以为界面坏了)
        resetButton.active = WrenchConfig.isWritable();
        addRenderableWidget(resetButton);
        addRenderableWidget(Button.builder(Component.translatable("gui.create_better_wrench.config.done"), b -> onClose())
            .bounds(width / 2 + 10, bottom, 150, 20)
            .build());

        search.setValue(query);
        refreshAll();
        layoutRows();
    }

    private void resetToDefaults() {
        if (!WrenchConfig.isWritable())
            return;
        WrenchConfig.resetAll();
        refreshAll();
    }

    /** 任何一次改动之后把所有控件刷新一遍(开关之间有联动规则, 别的行也可能变了)。 */
    private void refreshAll() {
        for (Entry entry : entries)
            entry.refresh();
    }

    // ---------------------------------------------------------------- 布局 / 滚动

    private int listHeight() {
        return Math.max(ROW_H, height - LIST_TOP - 46);
    }

    private int entryHeight(Entry entry) {
        return entry.isHeader ? HEADER_H : ROW_H;
    }

    private int visibleHeight() {
        int h = 0;
        for (Entry entry : entries)
            if (entry.matches(query))
                h += entryHeight(entry);
        return h;
    }

    /** 按过滤条件摆放行; 只有**完整落在列表区域内**的行才显示(避免画到标题/按钮上)。 */
    private void layoutRows() {
        int x = width / 2 - LIST_W / 2;
        double maxScroll = Math.max(0, visibleHeight() - listHeight());
        scroll = Mth.clamp(scroll, 0, maxScroll);
        int y = (int) Math.round(LIST_TOP - scroll);
        for (Entry entry : entries) {
            int h = entryHeight(entry);
            if (!entry.matches(query)) {
                entry.setVisible(false);
                continue;
            }
            boolean fullyVisible = y >= LIST_TOP && y + h <= LIST_TOP + listHeight();
            if (fullyVisible)
                entry.layout(x, y);
            entry.setVisible(fullyVisible);
            y += h;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double maxScroll = Math.max(0, visibleHeight() - listHeight());
        if (maxScroll > 0) {
            scroll = Mth.clamp(scroll - scrollY * 14, 0, maxScroll);
            layoutRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        // 不在这里 saveAll() —— 紧接着的 setScreen(parent) 会触发 removed(), 那里统一写盘一次就够
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

        int total = visibleHeight();
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
        for (Entry entry : entries) {
            if (!entry.shown)
                continue;
            if (entry.isHeader) {
                g.fill(x + 4, entry.y + 3, x + LIST_W - 4, entry.y + HEADER_H - 3, 0x40FFFFFF);
                g.drawString(font, entry.groupName(), x + 12, entry.y + 7, 0xFFD070, false);
            } else {
                // 子项缩进一点, 视觉上从属于上面的分组
                g.drawString(font, entry.label(), x + 20, entry.y + 7, 0xE0E0E0, false);
            }
        }

        boolean writable = WrenchConfig.isWritable();
        Component hint = Component.translatable(writable ? "gui.create_better_wrench.config.hint" : "gui.create_better_wrench.config.readonly");
        g.drawCenteredString(font, hint, width / 2, height - 44, writable ? 0x909090 : 0xFFAA55);
    }

    // ---------------------------------------------------------------- 行

    /** 列表里的一项: 分组标题 或 一个配置行。 */
    private final class Entry {
        private final boolean isHeader;
        private final String group;
        private final WrenchConfig.Row row;
        /** 数值行: 滑块 + `-` / `+`; 其它行为 null。 */
        private Slider slider;
        private Button minus;
        private Button plus;
        /** 布尔行 / 权限等级行的按钮。 */
        private Button toggle;
        private int y;
        private boolean shown;

        private Entry(boolean isHeader, String group, WrenchConfig.Row row) {
            this.isHeader = isHeader;
            this.group = group;
            this.row = row;
        }

        private Entry header(String group) {
            return new Entry(true, group, null);
        }

        private Entry option(WrenchConfig.Row row) {
            return new Entry(false, row.group(), row);
        }

        private Component groupName() {
            return Component.translatable("gui.create_better_wrench.config.group." + group);
        }

        private Component label() {
            return Component.translatable(row.labelKey());
        }

        private void createWidgets() {
            if (isHeader)
                return;
            boolean writable = WrenchConfig.isWritable();
            Tooltip tip = Tooltip.create(Component.translatable(row.descKey()));
            if (row.isToggle() || row.kind() == WrenchConfig.Kind.LEVEL) {
                toggle = Button.builder(Component.empty(), b -> {
                    if (!WrenchConfig.isWritable())
                        return;
                    if (row.isToggle())
                        WrenchConfig.applyToggle(row, !row.asBool());
                    else
                        // 权限等级只有两档: 0 = 普通玩家, 2 = OP
                        WrenchConfig.applyNumber(row, row.asNumber() >= 1 ? 0 : 2);
                    refreshAll();
                }).bounds(0, 0, CTL_W, 20).tooltip(tip).build();
                toggle.active = writable;
                addRenderableWidget(toggle);
            } else {
                slider = new Slider(row);
                slider.setTooltip(tip);
                minus = Button.builder(Component.literal("-"), b -> step(-stepSize()))
                    .tooltip(Tooltip.create(Component.translatable("gui.create_better_wrench.config.step.tip")))
                    .bounds(0, 0, STEP_BTN_W, 20)
                    .build();
                plus = Button.builder(Component.literal("+"), b -> step(stepSize()))
                    .tooltip(Tooltip.create(Component.translatable("gui.create_better_wrench.config.step.tip")))
                    .bounds(0, 0, STEP_BTN_W, 20)
                    .build();
                addRenderableWidget(slider);
                addRenderableWidget(minus);
                addRenderableWidget(plus);
            }
        }

        /** 名称/说明/TOML 路径/分组名 任一命中即匹配。 */
        private boolean matches(String q) {
            if (q.isEmpty())
                return true;
            if (isHeader)
                return groupName().getString().toLowerCase(Locale.ROOT).contains(q);
            String label = label().getString().toLowerCase(Locale.ROOT);
            String desc = Component.translatable(row.descKey()).getString().toLowerCase(Locale.ROOT);
            return label.contains(q) || desc.contains(q) || row.path().contains(q)
                || groupName().getString().toLowerCase(Locale.ROOT).contains(q);
        }

        /** 从配置回读当前值, 刷新控件上的文字/位置。 */
        private void refresh() {
            if (isHeader)
                return;
            if (toggle != null) {
                if (row.isToggle()) {
                    boolean on = row.asBool();
                    toggle.setMessage(Component.translatable(
                        "gui.create_better_wrench.config.toggle." + (on ? "on" : "off"))
                        .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
                } else {
                    boolean op = row.asNumber() >= 1;
                    toggle.setMessage(Component.translatable(
                        "gui.create_better_wrench.config.level." + (op ? "op" : "normal")));
                }
            }
            if (slider != null)
                slider.syncFromRow();
        }

        /** 微调步长: ±1; Shift ×10; Ctrl ×100(小数项 ×50)。不把 Alt 当 Ctrl(Alt 是呼出工具条的键)。 */
        private double stepSize() {
            double base = 1;
            if (net.minecraft.client.gui.screens.Screen.hasControlDown())
                return base * 100;
            if (net.minecraft.client.gui.screens.Screen.hasShiftDown())
                return base * 10;
            return base;
        }

        private void step(double delta) {
            if (!WrenchConfig.isWritable() || row == null)
                return;
            WrenchConfig.applyNumber(row, row.asNumber() + delta);
            refreshAll();
        }

        private void setVisible(boolean visible) {
            this.shown = visible;
            boolean editable = visible && WrenchConfig.isWritable();
            if (slider != null) {
                slider.visible = visible;
                slider.active = editable;
            }
            if (minus != null)
                minus.visible = editable;
            if (plus != null)
                plus.visible = editable;
            if (toggle != null) {
                toggle.visible = visible;
                toggle.active = editable;
            }
        }

        private void layout(int listX, int rowY) {
            this.y = rowY;
            if (isHeader)
                return;
            if (toggle != null) {
                toggle.setPosition(listX + 20 + LABEL_W, rowY + 2);
                return;
            }
            int sliderX = listX + 20 + LABEL_W + STEP_BTN_W + 6;
            minus.setPosition(listX + 20 + LABEL_W + 2, rowY + 2);
            slider.setPosition(sliderX, rowY + 2);
            plus.setPosition(sliderX + CTL_W + 4, rowY + 2);
        }
    }

    /** 数值滑块: 拖动即改配置(内存), 显示"值"。 */
    private final class Slider extends AbstractSliderButton {
        private final WrenchConfig.Row row;

        private Slider(WrenchConfig.Row row) {
            super(0, 0, CTL_W, 20, Component.empty(), norm(row, row.asNumber()));
            this.row = row;
            // AbstractSliderButton 的构造器**不会**调 updateMessage()(javap 实测) ⇒ 必须自己补一次,
            // 否则刚打开配置页时滑块上是空的, 得点一下才出现数值。
            updateMessage();
        }

        private static double norm(WrenchConfig.Row row, double value) {
            double span = row.max() - row.min();
            return span <= 0 ? 0 : Mth.clamp((value - row.min()) / span, 0, 1);
        }

        private double raw() {
            return row.min() + value * (row.max() - row.min());
        }

        /** 外部(重置 / 微调 / 联动)改了配置后, 把滑块拉回同步。 */
        private void syncFromRow() {
            this.value = norm(row, row.asNumber());
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(Long.toString(Math.round(raw()))));
        }

        @Override
        protected void applyValue() {
            if (!WrenchConfig.isWritable())
                return;
            WrenchConfig.applyNumber(row, Math.round(raw()));
            updateMessage();
        }
    }
}
