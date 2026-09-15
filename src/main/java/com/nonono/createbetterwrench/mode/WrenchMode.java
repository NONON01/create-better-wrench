package com.nonono.createbetterwrench.mode;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.foundation.gui.AllIcons;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * 扳手的"模式"(用 ALT 呼出底部工具条 + 滚轮循环切换)。
 *
 * <p>图标: 扳手/连接/装配/模组描述 用自绘 PNG(ResourceLocation, 机械动力风格);
 * 拆除 用 Create 的蓝图垃圾桶小图标({@link AllIcons#I_TRASH})。</p>
 */
public enum WrenchMode {

    /** HUD 顶部提示与描述里"按键/可选项"提示共用的蓝色(与顶部 "[SCROLL] 循环" 那行同色)。 */
    public static final int HINT_BLUE = 0xCCDDFF;

    WRENCH("wrench", ResourceLocation.fromNamespaceAndPath(
        BetterWrenchMod.MODID, "textures/gui/mode_wrench.png")),
    CONNECT("connect", ResourceLocation.fromNamespaceAndPath(
        BetterWrenchMod.MODID, "textures/gui/mode_connect.png")),
    DECONSTRUCT("deconstruct", AllIcons.I_TRASH),
    ASSEMBLE("assemble", ResourceLocation.fromNamespaceAndPath(
        BetterWrenchMod.MODID, "textures/gui/mode_assemble.png")),
    COMING_SOON("coming_soon", ResourceLocation.fromNamespaceAndPath(
        BetterWrenchMod.MODID, "textures/gui/mode_coming_soon.png"));

    private final String id;
    private final Object icon; // ResourceLocation 或 AllIcons, 或 null(暂不放图标)

    WrenchMode(String id, Object icon) {
        this.id = id;
        this.icon = icon;
    }

    public Component displayName() {
        return Component.translatable("mode." + BetterWrenchMod.MODID + "." + id);
    }

    /** 该模式小图标(ResourceLocation=自绘 PNG, AllIcons=蓝图白色小图标, null=暂未放)。 */
    public Object icon() {
        return icon;
    }

    /** 该模式的描述(用于选择器 tooltip); 文案在语言文件: mode.<modid>.<id>.desc。 */
    public Component description() {
        return emphasizeBrackets(Component.translatable("mode." + BetterWrenchMod.MODID + "." + id + ".desc"));
    }

    /**
     * 描述里被 {@code []} 或 {@code {}} 包起来的文字设为**加粗 + 蓝色**, **括号本身不加粗也不变色**。
     *
     * <p>这些是"按键提示"与"可选项"提示(例如 {@code [右键]} / {@code [Ctrl+滚轮]} / {@code {齿轮箱/大齿轮}})。
     * 用组件样式实现而不是 {@code §l}/{@code §b} 代码, 免得依赖渲染器对旧式格式码的解析。</p>
     */
    private static Component emphasizeBrackets(Component raw) {
        String text = raw.getString();
        MutableComponent out = Component.empty();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            char close = c == '[' ? ']' : (c == '{' ? '}' : (char) 0);
            if (close != 0) {
                int end = text.indexOf(close, i + 1);
                if (end > i) {
                    out.append(Component.literal(String.valueOf(c)));
                    out.append(Component.literal(text.substring(i + 1, end))
                        .withStyle(Style.EMPTY.withBold(true).withColor(HINT_BLUE)));
                    out.append(Component.literal(String.valueOf(close)));
                    i = end + 1;
                    continue;
                }
            }
            int next = i + 1;
            while (next < text.length() && text.charAt(next) != '[' && text.charAt(next) != '{')
                next++;
            out.append(Component.literal(text.substring(i, next)));
            i = next;
        }
        return out;
    }
}
