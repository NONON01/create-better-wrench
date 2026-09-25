package com.nonono.createbetterwrench.network;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.combat.WrenchCombatGrant;
import com.nonono.createbetterwrench.config.FeatureToggles;
import com.nonono.createbetterwrench.config.WrenchConfig;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 服务端: 把**功能开关快照**下发给客户端(专用服务器上客户端读不到 SERVER 配置, 靠这个知道开关状态)。
 *
 * <p>三个时机: ① 玩家登录; ② 配置重载(改 TOML 后 {@code /reload}, 或单人游戏里改配置);
 * ③ 指令 {@code /cbw combat} 改了某个玩家的单独授权(见 {@code command/CbwCommands})。</p>
 *
 * <p>⚠️ 本类只引用通用 API, 不含任何客户端类(专用服务器安全)。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID)
public final class FeatureSyncServer {

    private FeatureSyncServer() {
    }

    /** 玩家登录 → 单独发给他一份(含他自己的战斗授权)。 */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp)
            sendTo(sp);
    }

    /** 配置一重载 → 给所有在线玩家重发(单人游戏里客户端本来就能读本地配置, 重发只是保持一致)。 */
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != WrenchConfig.SPEC)
            return;
        broadcast();
    }

    /** 给单个玩家下发快照。 */
    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
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
