package com.nonono.createbetterwrench.combat;

import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端: **单独给某个玩家**开/关战斗模式的授权(指令 {@code /cbw combat <目标选择器> true|false})。
 *
 * <p>存在玩家自己的持久化数据里({@code Entity#getPersistentData()} → 存档时随玩家 NBT 一起保存),
 * 所以重启服务器后依然有效。被单独授权的玩家**不受**配置里"需要的权限等级"限制
 * (见 {@code permission/WrenchPermissions#canUseCombatMode})。</p>
 */
public final class WrenchCombatGrant {

    /** 持久化数据的键。 */
    private static final String KEY = "CbwCombatAllowed";

    private WrenchCombatGrant() {
    }

    /** 该玩家是否被单独授权。 */
    public static boolean isGranted(ServerPlayer player) {
        return player.getPersistentData().getBoolean(KEY);
    }

    /** 设置/取消单独授权。 */
    public static void set(ServerPlayer player, boolean granted) {
        player.getPersistentData().putBoolean(KEY, granted);
    }
}
