package com.nonono.createbetterwrench.combat;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 服务端: 每 tick 依据该玩家的战斗模式开关(与是否持扳手)应用/移除战斗加成; 玩家登出时清理记录。
 *
 * <p>{@link PlayerTickEvent.Post} 的回调内先按 {@code player.level().isClientSide} 过滤,
 * 保证属性改动只发生在服务端({@link WrenchCombat#tickServer(Player)});
 * 登出时由 {@link WrenchCombat#clearServer(java.util.UUID)} 移除该玩家的开关记录, 避免记录随在线过的玩家累积。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID)
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
