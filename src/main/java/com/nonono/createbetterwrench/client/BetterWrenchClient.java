package com.nonono.createbetterwrench.client;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.client.gui.WrenchConfigScreen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 客户端专用入口: {@code @Mod(dist = Dist.CLIENT)} ⇒ **专用服务器上不会被构造/加载**。
 *
 * <p>它把本模组在 **MOD 总线**上的客户端注册显式挂上:
 * <ul>
 *   <li>{@code RegisterKeyMappingsEvent} → {@link WrenchModeSwitcher#onRegisterKeyMappings} (ALT 呼出工具条);</li>
 *   <li>{@code RegisterKeyMappingsEvent} → {@link WrenchConfigKeybinds#onRegisterKeyMappings} (B+C 打开配置页面);</li>
 *   <li>{@code RegisterGuiLayersEvent} → {@link WrenchHud#onRegisterGuiLayers} (底部模式工具条).</li>
 * </ul></p>
 *
 * <p><b>为什么要单独一个类</b>: 这些以前用 {@code @EventBusSubscriber(bus = Bus.MOD)} 订阅, 而 NeoForge 21.1
 * 已把 {@code EventBusSubscriber.Bus} / {@code bus()} 标记为**待删除**(编译时的 deprecation 警告来源之一)。
 * 改成"在客户端专用 mod 类里显式 {@code addListener}"后: ①不再使用废弃 API; ②天然满足
 * "通用代码不得引用客户端类"的约束(专用服务器根本不会加载本类, 见 docs/07 §6 B-1)。</p>
 */
@Mod(value = BetterWrenchMod.MODID, dist = Dist.CLIENT)
public final class BetterWrenchClient {

    public BetterWrenchClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(WrenchHud::onRegisterGuiLayers);
        modEventBus.addListener(WrenchModeSwitcher::onRegisterKeyMappings);
        modEventBus.addListener(WrenchConfigKeybinds::onRegisterKeyMappings);
        // 模组列表里的「配置」按钮 → 本模组**自绘的配置页面**(默认按键 B+C 打开的也是它)。
        // ⚠️ 2026-09-22 变更: 以前这里挂的是 NeoForge 内置的 ConfigurationScreen; 用户要求做一个
        //    Tweakeroo 风格的专用页面, 于是替换为本页面(它一样遍历 WrenchConfig 的 spec, 范围/默认值都取自 spec)。
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
            (IConfigScreenFactory) (container, parent) -> WrenchConfigScreen.create(parent));
    }
}
