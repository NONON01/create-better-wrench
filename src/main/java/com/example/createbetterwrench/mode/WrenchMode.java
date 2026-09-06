package com.example.createbetterwrench.mode;

import com.example.createbetterwrench.BetterWrenchMod;

import net.minecraft.network.chat.Component;

/**
 * 扳手的"模式"(用 ALT 呼出底部工具条 + 滚轮循环切换)。
 *
 * <p>当前骨架只有 {@link #CONNECT}(连接)作为空壳占位; 其余模式待用户补充。</p>
 */
public enum WrenchMode {
    CONNECT("connect");

    private final String id;

    WrenchMode(String id) {
        this.id = id;
    }

    public Component displayName() {
        return Component.translatable("mode." + BetterWrenchMod.MODID + "." + id);
    }
}
