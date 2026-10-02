package com.nonono.createbetterwrench.client;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.client.gui.WrenchConfigScreen;
import com.nonono.createbetterwrench.client.ponder.BetterWrenchPonderPlugin;

import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 客户端专用入口: {@code @Mod(dist = Dist.CLIENT)}, 因此<b>专用服务器上不会被构造/加载</b>。
 *
 * <p>它把本模组在 <b>MOD 总线 / GAME 总线</b>上的客户端注册显式挂上:
 * <ul>
 *   <li>{@code RegisterKeyMappingsEvent} -> {@link WrenchModeSwitcher#onRegisterKeyMappings} (ALT 呼出工具条);
 *       2026-09-25: 打开配置页面的 B+C 组合键<b>已删除</b>, 改用指令 {@code /cbw config};</li>
 *   <li>{@code RegisterClientCommandsEvent} -> {@link CbwClientCommands#registerClient} ({@code /cbw config});</li>
 *   <li>{@code RegisterGuiLayersEvent} -> {@link WrenchHud#onRegisterGuiLayers} (底部模式工具条).</li>
 * </ul></p>
 *
 * <p><b>为什么单独一个类</b>: 这些以前用 {@code @EventBusSubscriber(bus = Bus.MOD)} 订阅, 而 NeoForge 21.1
 * 已把 {@code EventBusSubscriber.Bus} / {@code bus()} 标记为<b>待删除</b>(编译时的 deprecation 警告来源之一)。
 * 改成"在客户端专用 mod 类里显式 {@code addListener}"后: ①不再使用废弃 API; ②天然满足
 * "通用代码不得引用客户端类"的约束(专用服务器根本不会加载本类, 见 docs/design/known-issues.md B-1)。</p>
 */
@Mod(value = BetterWrenchMod.MODID, dist = Dist.CLIENT)
public final class BetterWrenchClient {

    public BetterWrenchClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(WrenchHud::onRegisterGuiLayers);
        modEventBus.addListener(WrenchModeSwitcher::onRegisterKeyMappings);
        // 物品悬停提示: 注册进 Create 的物品提示注册表(Shift 概要 / Ctrl 控制方法)。
        // 文案在语言文件: item.create_better_wrench.better_wrench.tooltip.*, 详见 WrenchTooltip 的类注释。
        modEventBus.addListener(WrenchTooltip::onClientSetup);
        // 2026-09-25(设计约定): 配置页面不再用快捷键打开(原来的 B+C 组合键已删除), 改用指令 /cbw config。
        // 客户端指令走 GAME 总线的 RegisterClientCommandsEvent。
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) ->
                CbwClientCommands.registerClient(event.getDispatcher()));
        // 思索(Ponder): 注册本模组的插件(场景 + 标签 + 共享文本)。
        // 必须客户端: 思索索引是纯客户端概念(上游库源码注释: `PonderRegistry can't be loaded on Server Dist`),
        //    服务端因此零改动, 也符合本项目"通用代码不引用客户端类"的约束(docs/design/known-issues.md B-1)。
        // 玩家侧: 悬停「万能扳手」按住 W 即可看到场景; 设计与 API 见 docs/modules/ponder.md。
        PonderIndex.addPlugin(new BetterWrenchPonderPlugin());
        // 模组列表里的「配置」按钮: 打开同一个自绘配置页面(快捷键入口已于 2026-09-25 删除)。
        // 2026-09-22 变更: 以前这里挂的是 NeoForge 内置的 ConfigurationScreen; 现替换为
        //    Tweakeroo 风格的专用页面(它一样遍历 WrenchConfig 的 spec, 范围/默认值都取自 spec)。
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
            (IConfigScreenFactory) (container, parent) -> WrenchConfigScreen.create(parent));
    }
}
