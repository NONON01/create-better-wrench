package com.nonono.createbetterwrench.item;

import com.mojang.serialization.Codec;
import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 实验(分支 exp/wrench-glow): 万能扳手的<b>光辉石标记</b>组件 {@code create_better_wrench:glow}。
 *
 * <p><b>双重含义</b>: ①它是"这把扳手附过光辉石"的记录; ②它同时是外发光的开关, 客户端渲染端据此产出
 * 发光 pass。暗影钢标记是另一个独立组件(见 {@link WrenchShadowSteelComponent}), 两者可以同时存在,
 * 名字颜色与说明行按组合决定(见 {@link WrenchGlowItemEvents} 与 {@code BetterWrenchItem#appendHoverText})。</p>
 *
 * <p><b>语义</b>: 组件存在且为 true = 有该标记; 缺失或 false = 没有。默认来源不是物品属性: 正常合成出来的
 * 扳手<b>不带</b>该组件, 由铁砧升级产生(普通扳手 + 一颗光辉石)。之所以不写进物品属性, 是因为
 * {@code ItemStack#get} 会回退到物品原型({@code PatchedDataComponentMap} 持有 {@code prototype}),
 * 写进物品属性的默认组件无法用 {@code ItemStack#remove} 关掉。</p>
 *
 * <p><b>注册与同步</b>: 用 {@code DeferredRegister.createDataComponents(MODID)} 的
 * {@code registerComponentType(...)} 延迟注册(与工程既有的 {@code DeferredRegister.Items} 同一套通道),
 * 编码同时配 {@code persistent(Codec.BOOL)}(存档读写)与
 * {@code networkSynchronized(ByteBufCodecs.BOOL)}(网络同步)。后者保证该组件随 {@code ItemStack} 的网络
 * 编码发到客户端 —— {@code ItemStack.STREAM_CODEC} 内会编码 {@code DataComponentPatch.STREAM_CODEC} ——
 * 因此客户端渲染端能读到同一个值。</p>
 */
public final class WrenchGlowComponent {

    /** 数据组件延迟注册器(在 {@code BetterWrenchMod} 构造器里挂到 MOD 总线上)。 */
    public static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(BetterWrenchMod.MODID);

    /** 组件 {@code create_better_wrench:glow}: Boolean, 持久化 + 网络同步。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> GLOW =
        COMPONENTS.registerComponentType("glow", builder -> builder
            .persistent(Codec.BOOL)
            .networkSynchronized(ByteBufCodecs.BOOL)
            .cacheEncoding());

    private WrenchGlowComponent() {
    }

    /** 该物品栈是否启用外发光(缺失或 false 都算关闭)。 */
    public static boolean isGlowing(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(GLOW.get()));
    }

    /** 组件当前是否存在于该物品栈上(用于日志区分"缺失"与"显式 false")。 */
    public static boolean hasComponent(ItemStack stack) {
        return stack.has(GLOW.get());
    }

    /** 组件当前值; 缺失时返回 {@code null}。 */
    public static Boolean componentValue(ItemStack stack) {
        return stack.get(GLOW.get());
    }

    /**
     * 写入「光辉石标记」: {@code create_better_wrench:glow = true}。它同时是外发光的开关(客户端渲染据此产生
     * 发光 pass), 也是"这把扳手附过光辉石"的记录。
     *
     * <p>名字颜色不在这里设置: 一把扳手可以同时拥有光辉石与暗影钢标记, 名字颜色必须按两者的组合统一决定,
     * 因此由 {@link WrenchGlowItemEvents} 在每次升级后调用它的 rarity 刷新逻辑一次性写入。本方法只写自己
     * 这一个标记, 不碰
     * {@link WrenchShadowSteelComponent}。</p>
     */
    public static void applyRadiance(ItemStack stack) {
        stack.set(GLOW.get(), Boolean.TRUE);
    }
}
