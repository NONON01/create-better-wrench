package com.example.createbetterwrench.client;

import com.example.createbetterwrench.BetterWrenchMod;
import com.example.createbetterwrench.combat.WrenchCombat;
import com.example.createbetterwrench.network.CombatModePayload;

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
