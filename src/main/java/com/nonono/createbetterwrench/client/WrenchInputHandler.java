package com.nonono.createbetterwrench.client;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * 客户端 GAME 总线(NeoForge.EVENT_BUS)输入事件: 把滚轮事件转交给 {@link WrenchHud} 处理。
 *
 * <p>{@code WrenchHud.onMouseScrolled} 返回 {@code true} 表示本次滚动已被底部工具条消费
 * (ALT 聚焦后循环切换模式, 或按住 Ctrl 切换当前模式的选项), 此时取消事件, 避免滚轮同时触发
 * 原版快捷栏切换; 返回 {@code false} 则不干预, 滚轮照常传给其它界面。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID, value = Dist.CLIENT)
public final class WrenchInputHandler {

    private WrenchInputHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseScrolled(InputEvent.MouseScrollingEvent event) {
        if (WrenchHud.onMouseScrolled(event))
            event.setCanceled(true);
    }
}
