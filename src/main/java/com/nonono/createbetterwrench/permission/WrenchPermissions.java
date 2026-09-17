package com.nonono.createbetterwrench.permission;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * 「万能扳手」的权限节点集合。
 *
 * <p>战斗模式受权限 {@code cbw.battlemode} 控制(NeoForge 权限节点名用"."分隔, 实际节点名 = {@code cbw.battlemode}),
 * <b>默认仅 OP 拥有</b>(默认解析器要求权限等级 ≥ 2; 可用 NeoForge 权限 API 单独授予普通玩家)。</p>
 *
 * <p>注意: 节点通过 {@link PermissionGatherEvent.Nodes} 注册, 该事件在 <b>NeoForge.EVENT_BUS(GAME 总线)</b> 上触发。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WrenchPermissions {

    /** OP 判定阈值: 权限等级 ≥ 2(与原版"可执行多数管理指令"的等级一致)。 */
    public static final int OP_PERMISSION_LEVEL = 2;

    /** 战斗模式权限节点: cbw.battlemode, **默认仅 OP 拥有**。 */
    public static final PermissionNode<Boolean> BATTLE_MODE = new PermissionNode<>(
        ResourceLocation.fromNamespaceAndPath("cbw", "battlemode"),
        PermissionTypes.BOOLEAN,
        // player 可能为 null(离线查询), 此时一律拒绝
        (player, playerUUID, context) -> player != null && player.hasPermissions(OP_PERMISSION_LEVEL));

    private WrenchPermissions() {
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(BATTLE_MODE);
    }

    /**
     * 该玩家是否拥有「战斗模式」权限。**默认仅 OP 拥有**。
     *
     * <p>审计发现: 原先权限 API 异常时 `return true`(fail-open) ⇒ 任何异常都会把战斗模式
     * **默认放开给所有人**。越权类功能必须 fail-closed, 故改为 `return false`。</p>
     */
    public static boolean canUseBattleMode(ServerPlayer player) {
        try {
            return PermissionAPI.getPermission(player, BATTLE_MODE);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
