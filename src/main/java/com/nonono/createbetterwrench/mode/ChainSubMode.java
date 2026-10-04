package com.nonono.createbetterwrench.mode;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.network.chat.Component;

/**
 * 「锁链传动」模式下由 Ctrl+滚轮循环切换的规划方式。
 *
 * <p>标准(STANDARD): 自动规划整条路径, 路径上已有的锁链传动轮可以复用。
 * 直线(STRAIGHT): 相邻两轮的连接在水平面内只沿 X 或 Z 一个轴前进, Y 允许随坡度变化。
 * 半自动(SEMI): 中间新增传动轮的 X 与 Z 由玩家多次右键依次指定, Y 由系统在起终点之间平滑处理。</p>
 *
 * <p>三种方式的完整行为与约束见 {@code docs/design/chain-mode-spec.md} 第 4 节。
 * 本枚举只承载"当前选中哪一种"这一客户端状态; 路径规划与预览由后续阶段实现。</p>
 */
public enum ChainSubMode {

    /** 标准: 自动规划路径, 允许复用路径上已有的传动轮。 */
    STANDARD("standard"),
    /** 直线: 水平面内只沿一个轴前进, 不平移绕行。 */
    STRAIGHT("straight"),
    /** 半自动: 只指定 X 与 Z, Y 由系统平滑处理。 */
    SEMI("semi");

    private final String id;

    ChainSubMode(String id) {
        this.id = id;
    }

    /** 子模式的稳定标识(网络与持久化使用), 同时是语言键后缀。 */
    public String id() {
        return id;
    }

    /**
     * 循环切到下一个子模式: 标准, 直线, 半自动依次前进, 半自动之后回到标准。
     *
     * <p>该方法是{@link #values()} 顺序上的单向循环; Ctrl+滚轮的反向切换由调用方
     * ({@code client/WrenchModeSwitcher#cycleCtrlOption}) 按 ordinal 回绕实现。</p>
     */
    public ChainSubMode next() {
        ChainSubMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /**
     * actionbar 展示文案(切换子模式时由 {@code client/WrenchHud#showCtrlOptionHint} 显示)。
     *
     * <p>文案在语言文件: {@code hint.<modid>.chain.submode.<id>} —— 与「连接」「拆除」「加工」
     * 三个模式的 Ctrl 选项提示共用 {@code hint} 前缀。</p>
     */
    public Component displayName() {
        return Component.translatable("hint." + BetterWrenchMod.MODID + ".chain.submode." + id);
    }

    /** 按 id 解析(网络与持久化使用); 无效值(含 null)回退到 {@link #STANDARD}。 */
    public static ChainSubMode byId(String id) {
        for (ChainSubMode mode : values())
            if (mode.id.equals(id))
                return mode;
        return STANDARD;
    }
}
