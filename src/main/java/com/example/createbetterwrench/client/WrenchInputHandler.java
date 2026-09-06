package com.example.createbetterwrench.client;

import com.example.createbetterwrench.BetterWrenchMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * 客户端 GAME 总线(NeoForge.EVENT_BUS)输入事件:ALT 聚焦时用滚轮循环切换扳手模式。
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class WrenchInputHandler {

    private WrenchInputHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseScrolled(InputEvent.MouseScrollingEvent event) {
        if (WrenchHud.onMouseScrolled(event))
            event.setCanceled(true);
    }
}
