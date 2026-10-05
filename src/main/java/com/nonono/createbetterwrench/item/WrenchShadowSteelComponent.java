package com.nonono.createbetterwrench.item;

import com.mojang.serialization.Codec;
import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 实验(分支 exp/wrench-glow): 「暗影钢扳手」的标记组件 {@code create_better_wrench:shadow_steel}。
 *
 * <p><b>语义</b>: 组件存在且为 true = 这把扳手已附上暗影钢材质; 缺失或 false = 未附上。与
 * {@link WrenchGlowComponent} 的 {@code glow}(光辉石标记)是<b>两个互相独立的组件</b>, 同一次升级只写自己那一个,
 * 因此一把扳手可以同时拥有两者 —— 名字颜色与 tooltip 行数由两者的组合决定(见
 * {@link WrenchGlowItemEvents#onAnvilUpdate})。</p>
 *
 * <p><b>注册与同步</b>: 与 {@code glow} 完全同款 —— {@code DeferredRegister.createDataComponents(MODID)}
 * 的 {@code registerComponentType}, 编码同时配 {@code persistent(Codec.BOOL)}(存档)与
 * {@code networkSynchronized(ByteBufCodecs.BOOL)}(网络, 客户端渲染/提示要读它)。</p>
 */
public final class WrenchShadowSteelComponent {

    /** 数据组件延迟注册器(在 {@code BetterWrenchMod} 构造器里挂到 MOD 总线上)。 */
    public static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(BetterWrenchMod.MODID);

    /** 组件 {@code create_better_wrench:shadow_steel}: Boolean, 持久化 + 网络同步。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> SHADOW_STEEL =
        COMPONENTS.registerComponentType("shadow_steel", builder -> builder
            .persistent(Codec.BOOL)
            .networkSynchronized(ByteBufCodecs.BOOL)
            .cacheEncoding());

    private WrenchShadowSteelComponent() {
    }

    /** 该物品栈是否已附上暗影钢材质(缺失或 false 都算否)。 */
    public static boolean isShadowSteel(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(SHADOW_STEEL.get()));
    }

    /** 只写入暗影钢标记; 名字颜色由 {@code WrenchGlowItemEvents} 按两个标记的组合统一刷新。 */
    public static void applyShadowSteel(ItemStack stack) {
        stack.set(SHADOW_STEEL.get(), Boolean.TRUE);
    }

    /** 组件当前值; 缺失时返回 {@code null}(日志用)。 */
    public static Boolean componentValue(ItemStack stack) {
        return stack.get(SHADOW_STEEL.get());
    }
}
