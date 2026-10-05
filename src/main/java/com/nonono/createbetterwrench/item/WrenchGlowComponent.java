package com.nonono.createbetterwrench.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 万能扳手的<b>光辉石标记</b>(1.20.1 版)。
 *
 * <p><b>与 1.21.1 的对应关系</b>: 1.21.1 用数据组件 {@code create_better_wrench:glow};1.20.1 没有
 * DataComponents, 因此改用 {@code ItemStack} 的 <b>NBT</b>, 但键仍然带命名空间 —— 用一个以模组 id 命名的
 * <b>子标签</b>装两个标记, 而不是把 {@code glow} 这种裸键直接放在根部:</p>
 *
 * <pre>
 *   ItemStack.tag = {
 *     "create_better_wrench": {      // 子标签(CompoundTag)
 *       "glow": 1b,                  // 光辉石标记
 *       "shadow_steel": 1b           // 暗影钢标记(另一个子键, 见 WrenchShadowSteelComponent)
 *     }
 *   }
 * </pre>
 *
 * <p><b>确切 NBT 路径</b>: {@code ItemStack.tag.create_better_wrench.glow}(ByteTag, 值 {@code 1b})。
 * 用子标签而不是根部裸键, 是为了让 1.20.1 的成就物品谓词能用 {@code nbt} 精确匹配而没有歧义, 例如
 * {@code "nbt": "{create_better_wrench:{glow:1b}}"}(谓词是部分匹配, 不要求整个 tag 只有这一项),
 * 也避免与其它模组在根部放同名键相撞。</p>
 *
 * <p><b>语义</b>(与 1.21.1 逐字一致): <b>子键存在且为 true = 有该标记</b>, 缺失或 false = 没有。
 * NBT 随 {@code ItemStack} 一起存档与网络同步, 因此客户端渲染端读到的是同一个值。</p>
 *
 * <p><b>双重含义</b>: ①"这把扳手附过光辉石"的记录; ②外发光的开关, 客户端渲染据此产出发光 pass。暗影钢标记是
 * 同级的另一个子键, 两者可以同时存在, 名字颜色与说明行按组合决定(见 {@link WrenchGlowItemEvents} 与
 * {@code BetterWrenchItem#appendHoverText})。</p>
 *
 * <p><b>默认来源不是物品属性</b>: 正常合成出来的扳手不带该子键, 由铁砧升级产生(普通扳手 + 一颗光辉石)。
 * 这与 1.21.1 版做法相同 —— 默认值若写进物品本体, 后续就无法"移除标记回到普通扳手"。</p>
 */
public final class WrenchGlowComponent {

    /** 承载全部材质标记的子标签名(等于模组 id)。 */
    public static final String NBT_ROOT = "create_better_wrench";

    /** 子标签内的光辉石键名。 */
    public static final String NBT_KEY = "glow";

    private WrenchGlowComponent() {
    }

    /** 该物品栈是否启用外发光(子键缺失或 false 都算关闭)。 */
    public static boolean isGlowing(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getCompound(NBT_ROOT).getBoolean(NBT_KEY);
    }

    /** 该标记当前是否存在于物品栈上(用于日志区分"缺失"与"显式 false")。 */
    public static boolean hasComponent(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(NBT_ROOT) && tag.getCompound(NBT_ROOT).contains(NBT_KEY);
    }

    /** 标记当前值; 缺失时返回 {@code null}。 */
    public static Boolean componentValue(ItemStack stack) {
        if (!hasComponent(stack))
            return null;
        return stack.getTag().getCompound(NBT_ROOT).getBoolean(NBT_KEY);
    }

    /**
     * 写入「光辉石标记」: {@code ItemStack.tag.create_better_wrench.glow = 1b}。它同时是外发光的开关
     * (客户端渲染据此产生发光 pass), 也是"这把扳手附过光辉石"的记录。
     *
     * <p>名字颜色不在这里设置: 一把扳手可以同时拥有光辉石与暗影钢标记, 颜色必须按两者的组合统一决定,
     * 而 1.20.1 的稀有度来自 {@code Item#getRarity(ItemStack)}(没有可写的 rarity 组件), 因此改由
     * {@code BetterWrenchItem#getRarity} 在读的时候按两个标记现算。本方法只写自己这一个子键。</p>
     */
    public static void applyRadiance(ItemStack stack) {
        stack.getOrCreateTagElement(NBT_ROOT).putBoolean(NBT_KEY, true);
    }
}
