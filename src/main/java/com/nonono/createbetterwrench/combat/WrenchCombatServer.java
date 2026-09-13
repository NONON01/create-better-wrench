package com.nonono.createbetterwrench.combat;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 服务端: 每 tick 依据该玩家的战斗模式开关(与是否持扳手)应用/移除战斗加成; 玩家登出时清理记录。
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WrenchCombatServer {

    private WrenchCombatServer() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide)
            return;
        WrenchCombat.tickServer(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WrenchCombat.clearServer(event.getEntity().getUUID());
    }
}
