package com.nonono.createbetterwrench.mode;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * 扳手的"模式"(用 ALT 呼出底部工具条 + 滚轮循环切换)。
 *
 * <p>图标: 全部 6 个模式都用<b>本模组自绘 PNG</b>(ResourceLocation) —— 本类<b>不再持有</b> Create 的
 * {@code AllIcons} 之类的蓝图图标(2026-09-17 已彻底移除该分支), 因此本模组对 Create 资源的
 * 运行时引用只剩 HUD 底纹 {@code AllGuiTextures.HUD_BACKGROUND} 一处。</p>
 */
public enum WrenchMode {

    WRENCH("wrench", ResourceLocation.fromNamespaceAndPath(
        BetterWrenchMod.MODID, "textures/gui/mode_wrench.png")),
    CONNECT("connect", ResourceLocation.fromNamespaceAndPath(
        BetterWrenchMod.MODID, "textures/gui/mode_connect.png")),
    DECONSTRUCT("deconstruct", ResourceLocation.fromNamespaceAndPath(
        BetterWrenchMod.MODID, "textures/gui/mode_deconstruct.png")),
    ASSEMBLE("assemble", ResourceLocation.fromNamespaceAndPath(
        BetterWrenchMod.MODID, "textures/gui/mode_assemble.png")),
    CHAIN("chain", ResourceLocation.fromNamespaceAndPath(
        BetterWrenchMod.MODID, "textures/gui/mode_chain.png")),
    COMING_SOON("coming_soon", ResourceLocation.fromNamespaceAndPath(
        BetterWrenchMod.MODID, "textures/gui/mode_coming_soon.png"));

    /** HUD 顶部提示与描述里"按键/可选项"提示共用的蓝色(与 {@code hint.create_better_wrench.toolbar.scroll} 那行同色)。 */
    public static final int HINT_BLUE = 0xCCDDFF;

    /**
     * 「模组描述」模式第二行(简短更新日志)的语言键。
     *
     * <p>该行的正式文案由语言文件提供: 每次发布只替换这一个键, 不必改动 Java 代码, 也不必改动
     * 第一行的版本占位符。键名沿用 {@code mode.<modid>.<id>.desc} 的既有前缀约定。</p>
     */
    private static final String KEY_CHANGELOG = "mode." + BetterWrenchMod.MODID + ".coming_soon.changelog";

    /**
     * 上述语言键缺失时的兜底文案(英文, 与 en_us 的值逐字一致)。
     *
     * <p>仅在语言文件未提供该键时显示; 随包发布的 en_us 与 zh_cn 都会提供正式值。</p>
     */
    private static final String CHANGELOG_FALLBACK = "Added feature [Chain Conveyor]";

    private final String id;
    private final ResourceLocation icon;

    WrenchMode(String id, ResourceLocation icon) {
        this.id = id;
        this.icon = icon;
    }

    public Component displayName() {
        return Component.translatable("mode." + BetterWrenchMod.MODID + "." + id);
    }

    /** 该模式的小图标 —— 一律为本模组自绘的 PNG。 */
    public ResourceLocation icon() {
        return icon;
    }

    /** 该模式的描述(用于选择器 tooltip); 文案在语言文件: mode.<modid>.<id>.desc。 */
    public Component description() {
        String key = "mode." + BetterWrenchMod.MODID + "." + id + ".desc";
        // 「模组描述」模式显示两行: 第一行是版本号(占位符 + 运行时读取, 与 /cbw version 同源,
        //   2026-10-03 修正过"一直停在 1.0.5-beta"的问题), 第二行是简短更新日志(单独语言键,
        //   见 KEY_CHANGELOG)。两行之间的换行符由本方法插入, 渲染层据此拆行并对齐。
        if (this == COMING_SOON)
            return emphasizeBrackets(Component.translatable(key, modVersion())
                .append(Component.literal("\n"))
                .append(Component.translatableWithFallback(KEY_CHANGELOG, CHANGELOG_FALLBACK)));
        return emphasizeBrackets(Component.translatable(key));
    }

    /** 本模组的版本号(与 gradle.properties 写入模组容器的值同源)。 */
    private static String modVersion() {
        return net.neoforged.fml.ModList.get()
            .getModContainerById(BetterWrenchMod.MODID)
            .map(container -> container.getModInfo()
                .getVersion()
                .toString())
            .orElse("?");
    }

    /**
     * 描述里被 {@code []} 或 {@code {}} 包起来的文字设为<b>加粗 + 蓝色</b>, <b>括号本身不加粗也不变色</b>。
     *
     * <p>这些是"按键提示"与"可选项"提示(例如 {@code [右键]} / {@code [Ctrl+滚轮]} / {@code {齿轮箱/大齿轮}})。
     * 用组件样式实现而不是 {@code §l}/{@code §b} 代码, 以避免依赖渲染器对旧式格式码的解析。</p>
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
