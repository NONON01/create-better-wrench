package com.nonono.createbetterwrench.client;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.config.WrenchConfig;
import com.nonono.createbetterwrench.mode.WrenchMode;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 客户端: **功能开关的闸门** —— "某个功能被配置关掉了"时统一提示 + 拦截。
 *
 * <p>用户 2026-09-25 定的行为:</p>
 * <ul>
 *   <li>功能被关: **仍然可以切到那个模式**, 但切过去时显示 actionbar「**此功能未启用**」;</li>
 *   <li>之后**每次尝试使用**该功能同样提示这一句, 并且什么都不做(服务端还会再拦一次)。</li>
 * </ul>
 *
 * <p>开关值来源: 单人游戏 = 本地配置; 专用服务器 = 服务端登录时下发的 {@code FeatureToggles} 快照
 * (见 {@code network/FeatureTogglePayload})。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientFeatureGate {

    /** 提示文案(用户指定的原话)。 */
    private static final String KEY_FEATURE = "msg." + BetterWrenchMod.MODID + ".feature_disabled";
    /** 子功能提示文案(用户指定的原话)。 */
    private static final String KEY_SUB = "msg." + BetterWrenchMod.MODID + ".subfeature_disabled";

    private ClientFeatureGate() {
    }

    /** 该模式的**功能**是否被关掉(「扳手」与「模组描述」永远可用; 战斗彩蛋另有开关)。 */
    public static boolean isFeatureDisabled(WrenchMode mode) {
        return switch (mode) {
            case CONNECT -> !WrenchConfig.connectEnabled();
            case DECONSTRUCT -> !WrenchConfig.deconstructEnabled();
            case ASSEMBLE -> !WrenchConfig.processEnabled();
            default -> false;
        };
    }

    /** 战斗模式是否被关掉(配置里"是否启用战斗模式")。 */
    public static boolean isCombatDisabled() {
        return !WrenchConfig.combatEnabled();
    }

    /**
     * 使用前调用: 功能被关则提示「此功能未启用」并返回 {@code true}(调用方应立刻 return, 什么都不做)。
     */
    public static boolean blockIfDisabled(WrenchMode mode) {
        if (!isFeatureDisabled(mode))
            return false;
        show(KEY_FEATURE);
        return true;
    }

    /** 切到该模式时调用: 功能被关就提示一次(模式本身仍然切过去了)。 */
    public static void announceIfDisabled(WrenchMode mode) {
        if (isFeatureDisabled(mode))
            show(KEY_FEATURE);
    }

    /** 尝试切换战斗彩蛋但功能被关: 提示「此功能未启用」。 */
    public static void announceCombatDisabled() {
        show(KEY_FEATURE);
    }

    /** 某个**加工子功能**被关时的提示(由服务端判定具体是哪一种, 客户端只负责这句文案)。 */
    public static void showSubFeatureDisabled() {
        show(KEY_SUB);
    }

    private static void show(String key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.displayClientMessage(Component.translatable(key), true);
    }
}
