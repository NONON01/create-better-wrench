package com.nonono.createbetterwrench.network;

import java.util.function.Supplier;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.combat.WrenchCombatGrant;
import com.nonono.createbetterwrench.config.FeatureToggles;
import com.nonono.createbetterwrench.config.WrenchConfig;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 服务端: 把<b>功能开关快照</b>下发给客户端(专用服务器上客户端读不到 SERVER 配置, 靠这个知道开关状态)。
 *
 * <p>三个时机: ① 玩家登录; ② 配置重载(改 TOML 后 {@code /reload}, 或单人游戏里改配置);
 * ③ 指令 {@code /cbw combat} 改了某个玩家的单独授权(见 {@code command/CbwCommands})。
 * 三次都走同一个载荷 {@code network/FeatureTogglePayload}, 内容是<b>该玩家专属</b>的一份
 * {@link WrenchConfig#snapshot(boolean)}(含他自己的战斗授权)。</p>
 *
 * <p>本类只引用通用 API, 不含任何客户端类(专用服务器安全)。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID)
public final class FeatureSyncServer {

    private FeatureSyncServer() {
    }

    /** 玩家登录时就单独给他发一份(含他自己的战斗授权)。 */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp)
            sendTo(sp);
    }

    /** 配置一重载就给所有在线玩家重发(单人游戏里客户端本来就能读本地配置, 重发只是保持一致)。 */
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != WrenchConfig.SPEC)
            return;
        broadcast();
    }

    /** 给单个玩家下发快照。 */
    public static void sendTo(ServerPlayer player) {
        WrenchNetwork.sendToPlayer(player,
            new FeatureTogglePayload(WrenchConfig.snapshot(WrenchCombatGrant.isGranted(player))));
    }

    /** 给所有在线玩家下发快照。 */
    public static void broadcast() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers())
            sendTo(sp);
    }

    /** 断开连接时清掉上一台服务器的快照(避免跨服务器残留开关)。 */
    public static void clearClientSnapshot() {
        FeatureToggles.clear();
    }
}
