package com.nonono.createbetterwrench;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.nonono.createbetterwrench.config.WrenchConfig;
import com.simibubi.create.AllCreativeModeTabs;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Create Better Wrench(万能扳手)主类。
 *
 * <p>纯 Create addon:不新增方块, 提供一把更好用的 Create 扳手「万能扳手」。
 * 物品只注册 {@link #BETTER_WRENCH}; 它加入 {@code c:tools/wrench} 标签使 Create 把它当扳手,
 * 并用 ALT 呼出底部工具条切换多种功能模式(连接/拆除)。物品放进 Create 的 BASE 创造标签。</p>
 *
 * <p>构造器依次完成: 物品注册、数据组件注册、SERVER 类型配置注册、数据附件注册、
 * 网络载荷注册({@link #registerPayloads})、配置重载监听、{@code /cbw} 服务端指令注册与创造标签注入。</p>
 */
@Mod(BetterWrenchMod.MODID)
public class BetterWrenchMod {
    public static final String MODID = "create_better_wrench";
    public static final Logger LOGGER = LogUtils.getLogger();

    // 物品延迟注册器
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    // 精密扳手物品
    public static final DeferredItem<com.nonono.createbetterwrench.item.BetterWrenchItem> BETTER_WRENCH =
        ITEMS.register("better_wrench", () -> new com.nonono.createbetterwrench.item.BetterWrenchItem(new Item.Properties()));

    public BetterWrenchMod(IEventBus modEventBus, ModContainer modContainer) {
        ITEMS.register(modEventBus);
        // 数据组件: 万能扳手的材质标记 create_better_wrench:glow(光辉石)/ shadow_steel(暗影钢)
        // (实验, 分支 exp/wrench-glow)。普通合成出的扳手不带任何标记; 两种标记由铁砧升级产生(见下面的
        // AnvilUpdateEvent 监听), 且互相独立, 可以同时存在于一把扳手上。
        com.nonono.createbetterwrench.item.WrenchGlowComponent.COMPONENTS.register(modEventBus);
        com.nonono.createbetterwrench.item.WrenchShadowSteelComponent.COMPONENTS.register(modEventBus);
        // 功能开关 + 可调参数写入 config/create_better_wrench-server.toml(实测位置: 实例或服务器根目录)
        // 类型 SERVER: 数值由服务端权威读取; 单人游戏里客户端与内置服务端共用同一份(预览与限制一致)。
        // 专用服务器上客户端读不到, 因此由 FeatureSyncServer 在登录/配置重载时下发 FeatureTogglePayload 快照。
        modContainer.registerConfig(ModConfig.Type.SERVER, WrenchConfig.SPEC);
        // 数据附件(置物台锁定态)
        com.nonono.createbetterwrench.assemble.AssembleLock.ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(BetterWrenchMod::registerPayloads);
        // 配置一重载(改 TOML + /reload, 或单人游戏里改配置)就把新开关下发给所有人
        modEventBus.addListener(com.nonono.createbetterwrench.network.FeatureSyncServer::onConfigReload);
        // 指令 /cbw(服务端那一半: version / combat / glow); 客户端那一半见 client/CbwClientCommands
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.RegisterCommandsEvent event) ->
                com.nonono.createbetterwrench.command.CbwCommands.registerServer(event.getDispatcher()));
        // 铁砧升级: 普通扳手 + 光辉石 -> 发光扳手(实验, 分支 exp/wrench-glow)
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            com.nonono.createbetterwrench.item.WrenchGlowItemEvents::onAnvilUpdate);
        // BuildCreativeModeTabContentsEvent 是 IModBusEvent, 须注册在 mod 事件总线上(非 NeoForge.EVENT_BUS)
        modEventBus.addListener(BetterWrenchMod::addToCreateTab);
        LOGGER.info("{} 正在加载...", MODID);
    }

    /** 把精密扳手追加进机械动力(create:base)创造标签。 */
    private static void addToCreateTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey())
            event.accept(BETTER_WRENCH.get().getDefaultInstance());
    }

    /**
     * 注册自定义网络载荷(packet)。
     *
     * <p>协议版本 {@code "1"}; 方向: 拆除 / 连接 / 战斗模式切换 / 装配锁定 / 加工停留时间为
     * 客户端到服务端, 战斗模式权威回包与功能开关快照为服务端到客户端。</p>
     */
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MODID).versioned("1");
        registrar.playToServer(
            com.nonono.createbetterwrench.network.DeconstructPayload.TYPE,
            com.nonono.createbetterwrench.network.DeconstructPayload.STREAM_CODEC,
            com.nonono.createbetterwrench.network.DeconstructPayload::handle);
        registrar.playToServer(
            com.nonono.createbetterwrench.network.ConnectPayload.TYPE,
            com.nonono.createbetterwrench.network.ConnectPayload.STREAM_CODEC,
            com.nonono.createbetterwrench.network.ConnectPayload::handle);
        registrar.playToServer(
            com.nonono.createbetterwrench.network.CombatModePayload.TYPE,
            com.nonono.createbetterwrench.network.CombatModePayload.STREAM_CODEC,
            com.nonono.createbetterwrench.network.CombatModePayload::handle);
        registrar.playToClient(
            com.nonono.createbetterwrench.network.CombatModeSyncPayload.TYPE,
            com.nonono.createbetterwrench.network.CombatModeSyncPayload.STREAM_CODEC,
            com.nonono.createbetterwrench.network.CombatModeSyncPayload::handle);
        registrar.playToServer(
            com.nonono.createbetterwrench.network.AssemblePayload.TYPE,
            com.nonono.createbetterwrench.network.AssemblePayload.STREAM_CODEC,
            com.nonono.createbetterwrench.network.AssemblePayload::handle);
        registrar.playToServer(
            com.nonono.createbetterwrench.network.AssembleStayPayload.TYPE,
            com.nonono.createbetterwrench.network.AssembleStayPayload.STREAM_CODEC,
            com.nonono.createbetterwrench.network.AssembleStayPayload::handle);
        // 功能开关快照(服务端到客户端): 专用服务器上客户端靠它知道哪些功能被关掉了
        registrar.playToClient(
            com.nonono.createbetterwrench.network.FeatureTogglePayload.TYPE,
            com.nonono.createbetterwrench.network.FeatureTogglePayload.STREAM_CODEC,
            com.nonono.createbetterwrench.network.FeatureTogglePayload::handle);
        // 锁链传动: 规划好的路径(客户端到服务端), 登记逻辑集中在 WrenchNetwork, 此处只调用一次
        com.nonono.createbetterwrench.network.WrenchNetwork.register(registrar);
    }
}
