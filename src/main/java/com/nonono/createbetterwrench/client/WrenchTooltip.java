package com.nonono.createbetterwrench.client;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;

import net.createmod.catnip.lang.FontHelper;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 「万能扳手」的物品悬停提示: 复用 Create 的提示体系, 不自绘。
 *
 * <p>挂接方式是把 {@link ItemDescription.Modifier} 注册进 Create 的物品提示注册表
 * {@link TooltipModifier#REGISTRY}; Create 的客户端事件处理按物品取出该实现并写入 tooltip。
 * 因此 Shift / Ctrl 两行提示、调色板与折行规则都与 Create 自身物品一致。
 * 文案键名按 Create 的约定为 {@code item.<modid>.<path>.tooltip.*}:</p>
 *
 * <ul>
 *   <li>{@code .summary} —— 按住 Shift 时显示的概要, 可有多条;</li>
 *   <li>{@code .controlN} 与 {@code .actionN} —— 按住 Ctrl 时显示的「条件 + 行为」配对, 编号自 1 起须连续,
 *       遇到缺号即停止读取;</li>
 *   <li>{@code .conditionN} 与 {@code .behaviourN} —— 归入 Shift 段的同类配对, 本模组当前未使用;</li>
 *   <li>「按住 Shift 可查看概要」与「按住 Ctrl 可查看控制方法」两行由 Create 依据自身语言键自动添加并高亮当前按下的键,
 *       本模组不重复定义这两行。</li>
 * </ul>
 *
 * <p>文本里的 {@code _下划线_} 片段按调色板的高亮样式渲染, 下划线本身不显示。</p>
 *
 * <p><b>配色与排版</b>: 直接使用 Create 的标准调色板 {@code FontHelper.Palette.STANDARD_CREATE},
 * 不再叠加任何自定义样式; 折行由 Create 的 {@code TooltipHelper} 处理, 因此颜色、粗体与换行
 * 都与 Create 自身物品完全一致。若要改变配色, 只需替换 {@link #PALETTE} 的取值。</p>
 *
 * <p><b>注册时机</b>: 该注册表以物品为键, 需要在物品注册完成之后写入, 因此挂在客户端设置阶段
 * ({@code FMLClientSetupEvent})。本类只被客户端专用入口引用, 专用服务器不会加载
 * (见 {@code docs/design/known-issues.md} B-1)。</p>
 */
public final class WrenchTooltip {

    /** 与 Create 自身物品完全相同的调色板。 */
    private static final FontHelper.Palette PALETTE = FontHelper.Palette.STANDARD_CREATE;

    private WrenchTooltip() {
    }

    /** 把扳手的提示实现写入 Create 的注册表; 由客户端入口挂到 MOD 总线的 {@code FMLClientSetupEvent}。 */
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> TooltipModifier.REGISTRY.register(
            BetterWrenchMod.BETTER_WRENCH.get(),
            new ItemDescription.Modifier(BetterWrenchMod.BETTER_WRENCH.get(), PALETTE)));
    }
}
