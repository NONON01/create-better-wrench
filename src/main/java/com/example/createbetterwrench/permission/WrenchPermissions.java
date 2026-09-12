package com.example.createbetterwrench.permission;

import com.example.createbetterwrench.BetterWrenchMod;

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
 * <p>战斗模式受权限 {@code cbw:battlemode} 控制(NeoForge 权限节点名用"."分隔, 实际节点名 = {@code cbw.battlemode}),
 * <b>默认拥有</b>(默认解析器返回 true)。</p>
 *
 * <p>注意: 节点通过 {@link PermissionGatherEvent.Nodes} 注册, 该事件在 <b>NeoForge.EVENT_BUS(GAME 总线)</b> 上触发。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WrenchPermissions {

    /** 战斗模式权限节点: cbw.battlemode, 默认拥有。 */
    public static final PermissionNode<Boolean> BATTLE_MODE = new PermissionNode<>(
        ResourceLocation.fromNamespaceAndPath("cbw", "battlemode"),
        PermissionTypes.BOOLEAN,
        (player, playerUUID, context) -> true);

    private WrenchPermissions() {
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(BATTLE_MODE);
    }

    /**
     * 该玩家是否拥有「战斗模式」权限。默认拥有; 若权限 API 尚未就绪(未注册/未初始化)也按默认(拥有)处理, 避免抛错。
     */
    public static boolean canUseBattleMode(ServerPlayer player) {
        try {
            return PermissionAPI.getPermission(player, BATTLE_MODE);
        } catch (RuntimeException e) {
            return true;
        }
    }
}
