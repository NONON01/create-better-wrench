package com.nonono.createbetterwrench.client;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.combat.BattleModeState;
import com.nonono.createbetterwrench.combat.WrenchCombat;
import com.nonono.createbetterwrench.network.CombatModePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端: 本地玩家按战斗模式开关应用加成(与 HUD 显示一致); 进入世界时把开关同步给服务端。
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class WrenchCombatClient {

    private WrenchCombatClient() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = event.getEntity();
        if (player != mc.player)
            return;
        // 先落地服务端回传的权威开关(例如因无 cbw.battlemode 权限被拒时, 把本地开关回正)。
        // 状态由通用类 BattleModeState 中转 —— 这样 network/ 里就不会出现任何客户端类引用(审计 #6)。
        Boolean synced = BattleModeState.poll();
        if (synced != null)
            WrenchModeSwitcher.combatMode = synced;

        WrenchCombat.apply(player, WrenchCombat.holdsWrench(player) && WrenchModeSwitcher.combatMode);
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        sendCombatMode();
    }

    /** 把当前战斗模式开关发给服务端(切换时 / 进入世界时)。 */
    public static void sendCombatMode() {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn != null)
            PacketDistributor.sendToServer(new CombatModePayload(WrenchModeSwitcher.combatMode));
    }
}
