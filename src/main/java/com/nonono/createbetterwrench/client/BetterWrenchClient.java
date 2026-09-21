package com.nonono.createbetterwrench.client;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 客户端专用入口: {@code @Mod(dist = Dist.CLIENT)} ⇒ **专用服务器上不会被构造/加载**。
 *
 * <p>它只做一件事 —— 把本模组在 **MOD 总线**上的两处客户端注册显式挂上:
 * <ul>
 *   <li>{@code RegisterKeyMappingsEvent} → {@link WrenchModeSwitcher#onRegisterKeyMappings} (ALT 呼出工具条);</li>
 *   <li>{@code RegisterGuiLayersEvent} → {@link WrenchHud#onRegisterGuiLayers} (底部模式工具条)。</li>
 * </ul></p>
 *
 * <p><b>为什么要单独一个类</b>: 这两处以前用 {@code @EventBusSubscriber(bus = Bus.MOD)} 订阅, 而 NeoForge 21.1
 * 已把 {@code EventBusSubscriber.Bus} / {@code bus()} 标记为**待删除**(编译时的 deprecation 警告来源之一)。
 * 改成"在客户端专用 mod 类里显式 {@code addListener}"后: ①不再使用废弃 API; ②天然满足
 * "通用代码不得引用客户端类"的约束(专用服务器根本不会加载本类, 见 docs/07 §6 B-1)。</p>
 */
@Mod(value = BetterWrenchMod.MODID, dist = Dist.CLIENT)
public final class BetterWrenchClient {

    public BetterWrenchClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(WrenchHud::onRegisterGuiLayers);
        modEventBus.addListener(WrenchModeSwitcher::onRegisterKeyMappings);
        // 模组列表里挂上 NeoForge **内置配置界面**: 玩家能直接看/改 WrenchConfig 里的参数,
        // 而且会把我们写的**中英双语注释**显示出来(不需要 Cloth Config 之类额外依赖)。
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
            (IConfigScreenFactory) (container, parent) -> new ConfigurationScreen(container, parent));
    }
}
