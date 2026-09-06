package com.example.createbetterwrench;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 机械动力:更好的扳手(Create Better Wrench)主类。
 *
 * <p>本 mod 是纯 Create addon:不新增方块,核心是一把"更好用的 Create 扳手"。
 * 当前骨架先落地:独立扳手物品 + 加入 {@code c:tools/wrench} 标签(使 Create 把它当扳手),
 * 以及 ALT 呼出的底部工具条 + 模式切换框架(「连接」模式为空壳,逻辑后续实现)。</p>
 */
@Mod(BetterWrenchMod.MODID)
public class BetterWrenchMod {
    public static final String MODID = "create_better_wrench";
    public static final Logger LOGGER = LogUtils.getLogger();

    // 物品延迟注册器
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // 独立"更好的扳手"物品(行为靠 c:tools/wrench 标签 + 自写模式逻辑)
    public static final DeferredItem<com.example.createbetterwrench.item.BetterWrenchItem> BETTER_WRENCH =
        ITEMS.register("better_wrench", () -> new com.example.createbetterwrench.item.BetterWrenchItem(new net.minecraft.world.item.Item.Properties()));

    // 创造标签:TOOLS_AND_UTILITIES(和原扳手同栏)
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WRENCH_TAB =
        CREATIVE_TABS.register("wrench_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + MODID))
            .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES)
            .icon(() -> BETTER_WRENCH.get().getDefaultInstance())
            .displayItems((params, output) -> output.accept(BETTER_WRENCH.get()))
            .build());

    public BetterWrenchMod(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        LOGGER.info("{} 正在加载...", MODID);
    }
}
