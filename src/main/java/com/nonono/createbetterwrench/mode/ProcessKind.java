package com.nonono.createbetterwrench.mode;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.network.chat.Component;

/**
 * 「加工」模式下的**六种加工方式** —— 与用户定的六个子功能开关一一对应。
 *
 * <pre>
 *   装配 assembly · 注液 filling · 洗涤 splash · 冶炼 blasting · 烤制 smoking · 缠魂 haunting
 * </pre>
 *
 * <p>它们各自可以在配置里单独关掉(见 {@code config/WrenchConfig} → {@code process.*});
 * 关掉后玩家用那种方式加工时会收到 actionbar「此子功能未启用」。</p>
 *
 * <p>ℹ️ 本模组的加工模式其实还有第 7 条路径"**原木去皮**"(手持斧头右击原木), 用户没把它列进六个子功能里,
 * 所以它只受**总开关**约束。</p>
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
