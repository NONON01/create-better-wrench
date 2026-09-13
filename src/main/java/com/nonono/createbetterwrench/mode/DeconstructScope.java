package com.nonono.createbetterwrench.mode;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.network.chat.Component;

/**
 * 「拆除」模式下, Ctrl 滚轮循环切换的三种拆除范围。
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
