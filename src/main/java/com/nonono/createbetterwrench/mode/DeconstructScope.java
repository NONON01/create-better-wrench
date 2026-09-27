package com.nonono.createbetterwrench.mode;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.network.chat.Component;

/**
 * 「拆除」模式下由 Ctrl+滚轮循环切换的三种拆除范围: 全部(ALL)、仅机械动力(CREATE_ONLY)、
 * 仅红石(REDSTONE_ONLY)。
 *
 * <p>展示名来自语言文件 {@code scope.create_better_wrench.deconstruct.*}。配置里禁止破坏某一类方块时,
 * 范围档会被强制锁定(详见 {@code config/WrenchConfig#deconstructForcedScope} 与
 * {@code client/WrenchModeSwitcher#cycleCtrlOption})。</p>
 */
public enum DeconstructScope {
    ALL("all"),
    CREATE_ONLY("create_only"),
    REDSTONE_ONLY("redstone_only");

    private final String id;

    DeconstructScope(String id) {
        this.id = id;
    }

    public Component displayName() {
        return Component.translatable("scope." + BetterWrenchMod.MODID + ".deconstruct." + id);
    }
}
