package com.nonono.createbetterwrench.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 「暗影钢扳手」的标记(1.20.1 版)。
 *
 * <p><b>与 1.21.1 的对应关系</b>: 1.21.1 是数据组件 {@code create_better_wrench:shadow_steel};
 * 1.20.1 改用 {@code ItemStack} 的 <b>NBT</b>, 与光辉石标记共用同一个以模组 id 命名的子标签:</p>
 *
 * <pre>
 *   ItemStack.tag.create_better_wrench.shadow_steel = 1b
 * </pre>
 *
 * <p><b>确切 NBT 路径</b>: {@code ItemStack.tag.create_better_wrench.shadow_steel}(ByteTag, {@code 1b});
 * 成就物品谓词可直接写 {@code "nbt": "{create_better_wrench:{shadow_steel:1b}}"}。语义与 1.21.1 一致:
 * <b>子键存在且为 true = 已附上暗影钢材质</b>, 缺失或 false = 未附上。</p>
 *
 * <p>与 {@link WrenchGlowComponent} 的 {@code glow} 是<b>两个互相独立的子键</b>, 同一次升级只写自己那一个,
 * 因此一把扳手可以同时拥有两者 —— 名字颜色与 tooltip 行数由两者的组合决定
 * (见 {@link WrenchGlowItemEvents#onAnvilUpdate} 与 {@code BetterWrenchItem})。</p>
 */
public final class WrenchShadowSteelComponent {

    /** 承载全部材质标记的子标签名(等于模组 id)。 */
    public static final String NBT_ROOT = "create_better_wrench";

    /** 子标签内的暗影钢键名。 */
    public static final String NBT_KEY = "shadow_steel";

    private WrenchShadowSteelComponent() {
    }

    /** 该物品栈是否已附上暗影钢材质(子键缺失或 false 都算否)。 */
    public static boolean isShadowSteel(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getCompound(NBT_ROOT).getBoolean(NBT_KEY);
    }

    /** 该标记当前是否存在于物品栈上。 */
    public static boolean hasComponent(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(NBT_ROOT) && tag.getCompound(NBT_ROOT).contains(NBT_KEY);
    }

    /** 标记当前值; 缺失时返回 {@code null}(日志用)。 */
    public static Boolean componentValue(ItemStack stack) {
        if (!hasComponent(stack))
            return null;
        return stack.getTag().getCompound(NBT_ROOT).getBoolean(NBT_KEY);
    }

    /** 只写入暗影钢标记; 名字颜色由 {@code BetterWrenchItem#getRarity} 按两个标记的组合现算。 */
    public static void applyShadowSteel(ItemStack stack) {
        stack.getOrCreateTagElement(NBT_ROOT).putBoolean(NBT_KEY, true);
    }
}
