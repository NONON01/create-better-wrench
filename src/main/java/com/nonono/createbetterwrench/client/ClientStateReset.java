package com.nonono.createbetterwrench.client;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.combat.CombatModeState;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * 客户端: **断开连接时统一清理本模组的全部客户端静态状态**。
 *
 * <p>审计发现的问题: 客户端这些状态原本没有登出清理, 于是存在跨世界残留 ——</p>
 * <ul>
 *   <li>{@code WrenchModeSwitcher} 的模式/Ctrl 选项/战斗开关 ——
 *       换服务器后会出现「本地显示已开战斗模式、服务端却没生效」的假象;</li>
 *   <li>{@code ConnectSelectionHandler} / {@code DeconstructSelectionHandler} 的**未完成选区** ——
 *       里面存的是**旧世界的 BlockPos**, 进了新世界后下一次右键会拿旧坐标去发包;</li>
 *   <li>{@code CombatModeState} 里还没被取走的待处理开关。</li>
 * </ul>
 *
 * <p>统一放在这一处, 而不是分散到各个类里各写一个监听器(那样很容易漏掉新加的状态)。</p>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientStateReset {

    private ClientStateReset() {
    }

    /** 退出世界 / 断开与服务器的连接时调用(换维度不会触发)。 */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        WrenchModeSwitcher.reset();
        ConnectSelectionHandler.cancel();
        DeconstructSelectionHandler.cancel();
        CombatModeState.clear();
    }
}
