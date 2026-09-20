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
 *   <li>{@code CombatModeState} 里还没被取走的待处理开关;</li>
 *   <li>{@code WrenchHud} 里懒加载的模式选择器(它的内部下标才是"画哪个高亮"的依据)。</li>
 * </ul>
 *
 * <p><b>两个触发点</b>: ①{@code LoggingOut}(退出世界/换服务器) → 全部归零;
 * ②{@code Clone}(切换维度/重生) → **只清选区**(旧世界坐标), 模式保持不变。</p>
 *
 * <p>统一放在这一处, 而不是分散到各个类里各写一个监听器(那样很容易漏掉新加的状态)。</p>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientStateReset {

    private ClientStateReset() {
    }

    /** 退出世界 / 断开与服务器的连接时调用。 */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetAll();
    }

    /**
     * 切换维度 / 重生时调用(见 docs/07 §6 A-7)。
     *
     * <p>⚠️ 这里**只清"未完成的选区"**: 选区里存的是**旧维度的 BlockPos**, 不清掉的话玩家在新维度
     * 一右击就会拿旧坐标发包(下界 1:8 坐标缩放时甚至可能隔着维度施工并扣料)。
     * <b>不清模式与 Ctrl 选项</b> —— 换个维度通常还希望保持当前模式。</p>
     */
    @SubscribeEvent
    public static void onDimensionChange(ClientPlayerNetworkEvent.Clone event) {
        ConnectSelectionHandler.cancel();
        DeconstructSelectionHandler.cancel();
    }

    private static void resetAll() {
        WrenchModeSwitcher.reset();
        ConnectSelectionHandler.cancel();
        DeconstructSelectionHandler.cancel();
        CombatModeState.clear();
        // ⚠️ 必须跟着重置: 否则工具条会一直高亮退出前的模式(见 docs/07 §6 A-12)
        WrenchHud.reset();
    }
}
