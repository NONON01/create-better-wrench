package com.nonono.createbetterwrench;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.nonono.createbetterwrench.config.WrenchConfig;
import com.simibubi.create.AllCreativeModeTabs;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Create Better Wrench(万能扳手)主类。
 *
 * <p>纯 Create addon:不新增方块, 提供一把更好用的 Create 扳手「万能扳手」。
 * 物品只注册 {@link #BETTER_WRENCH}; 它加入 {@code c:tools/wrench} 标签使 Create 把它当扳手,
 * 并用 ALT 呼出底部工具条切换多种功能模式(连接/拆除)。物品放进 Create 的 BASE 创造标签。</p>
 *
 * <p>构造器依次完成: 物品注册、SERVER 类型配置注册、数据附件注册、
 * 网络通道注册({@link com.nonono.createbetterwrench.network.WrenchNetwork#init})、配置重载监听、{@code /cbw} 服务端指令注册与创造标签注入。</p>
 */
@Mod(BetterWrenchMod.MODID)
public class BetterWrenchMod {
    public static final String MODID = "create_better_wrench";
    public static final Logger LOGGER = LogUtils.getLogger();

    // 物品延迟注册器
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ITEMS, MODID);

    // 精密扳手物品
    public static final RegistryObject<com.nonono.createbetterwrench.item.BetterWrenchItem> BETTER_WRENCH =
        ITEMS.register("better_wrench", () -> new com.nonono.createbetterwrench.item.BetterWrenchItem(new Item.Properties()));

    public BetterWrenchMod() {
        // Forge 1.20.1 用无参构造(带 FMLJavaModLoadingContext 参数的构造在这一版不被识别:
        // 实测会报 NoSuchMethodException: <init>()), 事件总线从上下文取。
        IEventBus modEventBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get()
            .getModEventBus();
        // 网络通道必须在模组构造期建立(Forge 的 SimpleChannel 不依赖事件)
        com.nonono.createbetterwrench.network.WrenchNetwork.init();

        // 客户端专用初始化: 用 DistExecutor 包住, 保证专用服务器不会加载 client/ 下的类
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
            net.minecraftforge.api.distmarker.Dist.CLIENT,
            () -> () -> com.nonono.createbetterwrench.client.BetterWrenchClient.init(modEventBus));
        ITEMS.register(modEventBus);
        // 功能开关 + 可调参数写入 config/create_better_wrench-server.toml(实测位置: 实例或服务器根目录)
        // 类型 SERVER: 数值由服务端权威读取; 单人游戏里客户端与内置服务端共用同一份(预览与限制一致)。
        // 专用服务器上客户端读不到, 因此由 FeatureSyncServer 在登录/配置重载时下发 FeatureTogglePayload 快照。
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
            net.minecraftforge.fml.config.ModConfig.Type.SERVER, WrenchConfig.SPEC);
        // 数据附件(置物台锁定态)
            // 配置一重载(改 TOML + /reload, 或单人游戏里改配置)就把新开关下发给所有人
        modEventBus.addListener(com.nonono.createbetterwrench.network.FeatureSyncServer::onConfigReload);
        // 指令 /cbw(服务端那一半: version / combat); 客户端那一半见 client/CbwClientCommands
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
            (net.minecraftforge.event.RegisterCommandsEvent event) ->
                com.nonono.createbetterwrench.command.CbwCommands.registerServer(event.getDispatcher()));
        // 铁砧升级: 普通扳手 + 光辉石 / 暗影钢 -> 材质变体(标记写进 ItemStack 的 NBT 子标签
        // create_better_wrench.{glow|shadow_steel}; 详细语义见 item/WrenchGlowItemEvents)。
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
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
}
