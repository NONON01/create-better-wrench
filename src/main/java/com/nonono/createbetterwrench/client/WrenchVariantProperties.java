package com.nonono.createbetterwrench.client;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.item.WrenchShadowSteelComponent;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 把「暗影钢标记」(1.20.1 为 {@code ItemStack.tag.create_better_wrench.shadow_steel})暴露成一个
 * <b>物品模型 predicate</b>, 供 {@code models/item/better_wrench.json} 的 {@code overrides} 切换外观。
 *
 * <p><b>为什么这样做</b>: 1.20.1 的 {@code ItemOverrides} 在渲染时用
 * {@code ItemProperties.getProperty(stack, 名称)} 取每个 predicate 的值, 因此 predicate 的名称可以是任意
 * 已注册的物品属性 —— 本模组把 {@code create_better_wrench:shadow_steel} 注册成"该标记存在即为 1"的函数,
 * 于是模型 {@code overrides} 直接由 NBT 标记驱动, 不需要 {@code minecraft:custom_model_data} 这种额外状态,
 * 也不会与其它模组/资源包的同名 predicate 冲突(属性按物品注册)。</p>
 *
 * <p>注册时机: 物品注册完成之后, 客户端设置阶段({@code FMLClientSetupEvent})。本类只被客户端专用入口引用,
 * 专用服务器不会加载(见 docs/design/known-issues.md B-1)。</p>
 */
public final class WrenchVariantProperties {

    /** 供模型 {@code overrides} 使用的 predicate 名: {@code create_better_wrench:shadow_steel}。 */
    public static final ResourceLocation SHADOW_STEEL =
        new ResourceLocation(BetterWrenchMod.MODID, "shadow_steel");

    private WrenchVariantProperties() {
    }

    /** 由客户端入口挂到 MOD 总线的 {@code FMLClientSetupEvent}。 */
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
            BetterWrenchMod.BETTER_WRENCH.get(),
            SHADOW_STEEL,
            (stack, level, entity, seed) -> WrenchShadowSteelComponent.isShadowSteel(stack) ? 1.0F : 0.0F));
    }
}
