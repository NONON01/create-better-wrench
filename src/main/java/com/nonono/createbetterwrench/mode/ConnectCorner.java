package com.nonono.createbetterwrench.mode;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.network.chat.Component;

/**
 * 「连接」模式下, Ctrl+滚轮循环切换的"拐角类型"。
 *
 * <p>齿轮箱(GEARBOX): 在每个方向改变处放 1 个齿轮箱(水平↔水平=普通齿轮箱; 涉竖直=竖直齿轮箱)。
 * 大齿轮(LARGE_COG): 用 Create 的"两个大齿轮斜对角"换轴机制(等速换向)替代齿轮箱;
 * 大齿轮靠 {@code RotationPropagator.isLargeToLargeGear} 连接: 两块 AXIS 互相垂直、偏移在两轴各 ±1、第三轴为 0。</p>
 */
public enum ConnectCorner {
    GEARBOX("gearbox"),
    LARGE_COG("large_cog");

    private final String id;

    ConnectCorner(String id) {
        this.id = id;
    }

    public Component displayName() {
        return Component.translatable("corner." + BetterWrenchMod.MODID + "." + id);
    }

    public static ConnectCorner byName(String name) {
        for (ConnectCorner c : values())
            if (c.name().equals(name))
                return c;
        return GEARBOX;
    }
}
