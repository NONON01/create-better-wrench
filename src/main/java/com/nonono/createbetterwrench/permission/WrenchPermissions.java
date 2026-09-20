package com.nonono.createbetterwrench.permission;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * 「万能扳手」的权限节点集合。
 *
 * <p>战斗模式受权限 {@code cbw.combatmode} 控制(NeoForge 权限节点名用"."分隔, 实际节点名 = {@code cbw.combatmode}),
 * <b>默认仅 OP 拥有</b>(默认解析器要求权限等级 ≥ 2; 可用 NeoForge 权限 API 单独授予普通玩家)。</p>
 *
 * <p>注意: 节点通过 {@link PermissionGatherEvent.Nodes} 注册, 该事件在 <b>NeoForge.EVENT_BUS(GAME 总线)</b> 上触发。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WrenchPermissions {

    /** OP 判定阈值: 权限等级 ≥ 2(与原版"可执行多数管理指令"的等级一致)。 */
    public static final int OP_PERMISSION_LEVEL = 2;

    /** 战斗模式权限节点: cbw.combatmode, **默认仅 OP 拥有**。 */
    public static final PermissionNode<Boolean> COMBAT_MODE = new PermissionNode<>(
        ResourceLocation.fromNamespaceAndPath("cbw", "combatmode"),
        PermissionTypes.BOOLEAN,
        // player 可能为 null(离线查询), 此时一律拒绝
        (player, playerUUID, context) -> player != null && player.hasPermissions(OP_PERMISSION_LEVEL));

    private WrenchPermissions() {
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(COMBAT_MODE);
    }

    /**
     * 该玩家是否拥有「战斗模式」权限。**默认仅 OP 拥有**。
     *
     * <p>审计发现: 原先权限 API 异常时 `return true`(fail-open) ⇒ 任何异常都会把战斗模式
     * **默认放开给所有人**。越权类功能必须 fail-closed, 故改为 `return false`。</p>
     */
    public static boolean canUseCombatMode(ServerPlayer player) {
        try {
            return PermissionAPI.getPermission(player, COMBAT_MODE);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 三个操作类服务端入口共用的「能不能动手」闸门。
     *
     * <p>对照 Create 自己的 {@code WrenchItem.useOn} —— 它同样先查 {@code mayBuild()}。
     * 无建造权限时不执行任何操作;若是**冒险模式**则额外给一条 actionbar 提示
     * (用户要求:「在冒险模式触发时, 提示『当前是冒险模式』」)。</p>
     *
     * <p>旁观者/其它无权限情形保持**静默**拒绝 —— 提示只针对冒险模式, 避免误报。</p>
     *
     * @return {@code true} 表示已拒绝, 调用方应立刻 {@code return}
     */
    public static boolean rejectIfCannotBuild(ServerPlayer player) {
        if (!player.isSpectator() && player.mayBuild())
            return false;                                   // 有建造权限, 放行
        if (player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)
            player.displayClientMessage(
                Component.translatable("msg." + BetterWrenchMod.MODID + ".adventure_mode"), true);
        return true;
    }
}
