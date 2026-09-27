package com.nonono.createbetterwrench.mode;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.network.chat.Component;

/**
 * 「加工」模式下的<b>六种加工方式</b> —— 与配置中的六个子功能开关一一对应。
 *
 * <pre>
 *   装配 assembly · 注液 filling · 洗涤 splash · 冶炼 blasting · 烤制 smoking · 缠魂 haunting
 * </pre>
 *
 * <p>它们各自可以在配置里单独关掉(见 {@code config/WrenchConfig} 的 {@code process.*} 键);
 * 关掉后玩家用那种方式加工时会收到 actionbar 提示, 文案为 lang key
 * {@code msg.create_better_wrench.subfeature_disabled}。</p>
 *
 * <p>注意: 本模组的加工模式还有第 7 条路径"原木去皮"(手持斧头右击原木)。该路径不属于上述六个子功能,
 * 因此只受总开关约束。</p>
 */
public enum ProcessKind {

    ASSEMBLY("assembly"),
    FILLING("filling"),
    SPLASH("splash"),
    BLASTING("blasting"),
    SMOKING("smoking"),
    HAUNTING("haunting");

    private final String id;

    ProcessKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** 该方式的显示名(装配/注液/洗涤/冶炼/烤制/缠魂)。 */
    public Component displayName() {
        return Component.translatable("process." + BetterWrenchMod.MODID + "." + id);
    }

    /** 配置项路径(与 {@code WrenchConfig} 里的 TOML 键一致)。 */
    public String configPath() {
        return "process." + id;
    }
}
