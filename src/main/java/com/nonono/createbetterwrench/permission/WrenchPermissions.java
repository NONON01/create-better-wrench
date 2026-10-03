package com.nonono.createbetterwrench.permission;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

/**
 * 「万能扳手」的权限节点集合。
 *
 * <p>战斗模式受权限 {@code cbw.combatmode} 控制(NeoForge 权限节点名以"."分隔, 实际节点名 = {@code cbw.combatmode}),
 * <b>默认仅 OP 拥有</b>(该节点的默认解析器读取配置 {@code combat.permission_level}, 出厂值 2;
 * 也可用 NeoForge 权限 API 单独授予普通玩家)。</p>
 *
 * <p>注意: 节点通过 {@link PermissionGatherEvent.Nodes} 注册, 该事件在 <b>NeoForge.EVENT_BUS(GAME 总线)</b> 上触发。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID)
public final class WrenchPermissions {

    /** OP 判定阈值(配置里"需要的权限等级"填 2 时的语义: 与原版"可执行多数管理指令"一致)。 */
    public static final int OP_PERMISSION_LEVEL = 2;

    /**
     * 战斗模式权限节点: cbw.combatmode, <b>默认按配置里"需要的权限等级"判定</b>
     * ({@code combat.permission_level} = 0 普通 / 2 OP)。
     *
     * <p>2026-09-25: 此前这里写死 ≥ 2; 现改为动态读配置, 否则配置里设成 0(人人可用)时,
     * 这个节点的默认解析器仍会拒绝普通玩家, 两套判定互相矛盾。整合包作者依旧可以用 NeoForge 权限 API
     * 单独改写这个节点来覆盖配置。</p>
     */
    public static final PermissionNode<Boolean> COMBAT_MODE = new PermissionNode<>(
        new ResourceLocation("cbw", "combatmode"),
        PermissionTypes.BOOLEAN,
        // player 可能为 null(离线查询), 此时一律拒绝
        (player, playerUUID, context) -> player != null
            && player.hasPermissions(com.nonono.createbetterwrench.config.WrenchConfig.combatPermissionLevel()));

    private WrenchPermissions() {
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(COMBAT_MODE);
    }

    /**
     * 该玩家能不能用「战斗模式」。<b>三重判定</b>(任一满足即可):
     * <ol>
     *   <li>配置里"是否启用战斗模式"= 开(关闭时任何玩家都不可用);</li>
     *   <li>被指令 {@code /cbw combat <选择器> true} <b>单独授权</b>过(不受权限等级限制);</li>
     *   <li>权限等级 ≥ 配置里的 {@code combat.permission_level}(0 = 普通玩家也能开, 2 = 需要 OP);
     *       另外 NeoForge 权限节点 {@code cbw.combatmode} 也照这个等级解析, 整合包可另行覆盖。</li>
     * </ol>
     *
     * <p>审计发现: 原先权限 API 异常时 {@code return true}(fail-open), 任何异常都会把战斗模式
     * <b>默认放开给所有人</b>。越权类功能必须 fail-closed: 异常时<b>只保留"指令授权"这一条</b>。</p>
     */
    public static boolean canUseCombatMode(ServerPlayer player) {
        if (!com.nonono.createbetterwrench.config.WrenchConfig.combatEnabled())
            return false;
        boolean granted = com.nonono.createbetterwrench.combat.WrenchCombatGrant.isGranted(player);
        try {
            return granted
                || PermissionAPI.getPermission(player, COMBAT_MODE)
                || player.hasPermissions(com.nonono.createbetterwrench.config.WrenchConfig.combatPermissionLevel());
        } catch (RuntimeException e) {
            return granted;
        }
    }

    /**
     * 三个操作类服务端入口共用的建造权限闸门。
     *
     * <p>对照 Create 自己的 {@code WrenchItem.useOn} —— 它同样先查 {@code mayBuild()}。
     * 无建造权限时不执行任何操作; 若是<b>冒险模式</b>则额外给一条 actionbar 提示
     * (设计约定, 2026-09-17: 提示文案取自 lang key
     * {@code msg.create_better_wrench.adventure_mode})。</p>
     *
     * <p>旁观者/其它无权限情形保持<b>静默</b>拒绝 —— 提示只针对冒险模式, 避免误报。</p>
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
