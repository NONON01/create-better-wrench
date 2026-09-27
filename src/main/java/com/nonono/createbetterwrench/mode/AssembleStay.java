package com.nonono.createbetterwrench.mode;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.network.chat.Component;

/**
 * 「加工」模式下由 Ctrl+滚轮循环切换的成品在置物台上的停留时间。
 *
 * <p>设计约定, 2026-09-17: 不停留 / 短 / 中(默认) / 长, 分别对应
 * 立刻弹出 / 2 tick / 4 tick / 8 tick。单位是<b>服务端 tick</b>(20 tick = 1 秒)。</p>
 *
 * <p>这是<b>观感选项</b>: 加工产出成品后, 成品先在置物台台面上停 {@link #ticks()} 个 tick,
 * 再作为掉落物弹出去(见 {@code assemble/DepotProductEjector})。</p>
 */
public enum AssembleStay {

    /** 不停留: 产出即弹出(观感等同于产出后立即消失)。 */
    NONE("none", 0),
    /** 短: 2 tick。 */
    SHORT("short", 2),
    /** 中: 4 tick —— 出厂默认。 */
    MEDIUM("medium", 4),
    /** 长: 8 tick。 */
    LONG("long", 8);

    /** 出厂默认档(Ctrl 选项的初始值; 也是服务端在没收到客户端同步时的兜底)。 */
    public static final AssembleStay DEFAULT = MEDIUM;

    private final String id;
    private final int ticks;

    AssembleStay(String id, int ticks) {
        this.id = id;
        this.ticks = ticks;
    }

    /** 停留的服务端 tick 数; {@code 0} = 立刻弹出。 */
    public int ticks() {
        return ticks;
    }

    /** 展示名; 文案在语言文件: stay.<modid>.<id>。 */
    public Component displayName() {
        return Component.translatable("stay." + BetterWrenchMod.MODID + "." + id);
    }

    /** 循环切到相邻档: direction > 0 取枚举中的下一档, 越界回绕。 */
    public AssembleStay cycle(int direction) {
        AssembleStay[] all = values();
        int idx = ordinal() + (direction < 0 ? -1 : 1);
        return all[((idx % all.length) + all.length) % all.length];
    }

    /** 按枚举名解析(网络/持久化用); 无效值回退到 {@link #DEFAULT}。 */
    public static AssembleStay byName(String name) {
        for (AssembleStay stay : values())
            if (stay.name().equals(name))
                return stay;
        return DEFAULT;
    }

    /**
     * 把 tick 数夹回合法区间。
     *
     * <p>服务端<b>绝不信任</b>客户端传来的 tick 数 —— 上界 {@link #MAX_TICKS} 仅用于防止改包传入过大的值
     * 使弹出队列膨胀。</p>
     */
    public static int clampTicks(int ticks) {
        return Math.max(0, Math.min(MAX_TICKS, ticks));
    }

    /** 客户端可同步的最大停留 tick 数(60 tick = 3 秒, 远大于最长档 8)。 */
    public static final int MAX_TICKS = 60;
}
