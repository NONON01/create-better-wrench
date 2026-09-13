package com.nonono.createbetterwrench;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllCreativeModeTabs;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 精密扳手(Create Better Wrench / Precision Wrench)主类。
 *
 * <p>纯 Create addon:不新增方块, 提供一把更强力的 Create 扳手「精密扳手」。
 * 物品注册在 {@link #BETTER_WRENCH}; 它加入 {@code c:tools/wrench} 标签使 Create 把它当扳手,
 * 并用 ALT 呼出底部工具条切换多种功能模式(连接/拆除)。物品放进 Create 的 BASE 创造标签。</p>
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

    public BetterWrenchMod(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        // 数据附件(置物台锁定态)
        com.nonono.createbetterwrench.assemble.AssembleLock.ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(BetterWrenchMod::registerPayloads);
        // BuildCreativeModeTabContentsEvent 是 IModBusEvent, 须注册在 mod 事件总线上(非 NeoForge.EVENT_BUS)
        modEventBus.addListener(BetterWrenchMod::addToCreateTab);
        LOGGER.info("{} 正在加载...", MODID);
    }

    /** 把精密扳手追加进机械动力(create:base)创造标签。 */
    private static void addToCreateTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey())
            event.accept(BETTER_WRENCH.get().getDefaultInstance());
    }

    /** 注册自定义网络载荷(packet)。 */
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
            com.nonono.createbetterwrench.network.BattleModeSyncPayload.TYPE,
            com.nonono.createbetterwrench.network.BattleModeSyncPayload.STREAM_CODEC,
            com.nonono.createbetterwrench.network.BattleModeSyncPayload::handle);
        registrar.playToServer(
            com.nonono.createbetterwrench.network.AssemblePayload.TYPE,
            com.nonono.createbetterwrench.network.AssemblePayload.STREAM_CODEC,
            com.nonono.createbetterwrench.network.AssemblePayload::handle);
    }
}
