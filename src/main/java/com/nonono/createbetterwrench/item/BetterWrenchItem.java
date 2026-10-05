package com.nonono.createbetterwrench.item;

import java.util.ArrayList;
import java.util.List;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 「万能扳手」物品。
 *
 * <p>要让它被 Create 当作扳手(对 {@code IWrenchable} 方块右键转动/拆除), 已加入
 * {@code forge:tools/wrench} 标签(Create 1.20.1 自己不提供该标签, 它只声明
 * {@code data/forge/tags/items/tools/wrench.json} 的成员)。</p>
 *
 * <p>攻击加成(伤害 +5 / 攻速 +20)与取消无敌帧属于战斗加成(可选), <b>不再烘焙在物品属性里</b>;
 * 只有在「模组描述」模式里用 Ctrl 切到战斗模式且手持本扳手时才生效, 详见
 * {@link com.nonono.createbetterwrench.combat.WrenchCombat}。</p>
 *
 * <p>正常合成出来的扳手是<b>普通扳手</b> —— 不带任何材质标记、名字为默认白色、没有额外说明行。在铁砧上用
 * 材料升级(见 {@link WrenchGlowItemEvents})会得到材质变体: 光辉石写入
 * {@code create_better_wrench:glow}(同时开启外发光), 暗影钢写入
 * {@code create_better_wrench:shadow_steel}(外观换成暗影钢材质)。两个标记互相独立, 可以同时存在;
 * 名字颜色按组合刷新 —— 单一材料 {@code Rarity.UNCOMMON}(黄, 与材料同色), 两者都有
 * {@code Rarity.EPIC}(紫, 与原版附魔金苹果同级)。按最终取舍<b>不附加附魔光效</b>。</p>
 *
 * <p><b>名字颜色为什么覆写 {@code getRarity}</b>: 1.20.1 的 {@code ItemStack#getTooltipLines} 用
 * {@code this.getRarity().getStyleModifier()} 给名字上色, 而 {@code ItemStack#getRarity()} 会转调
 * {@code Item#getRarity(ItemStack)}（这个方法在 1.21 被删除, 是本版本独有的可用扩展点）。因此这里按两个
 * NBT 标记现算: 两者都有 EPIC, 只有一种 UNCOMMON, 都没有返回物品默认值(COMMON, 白色)。</p>
 */
public class BetterWrenchItem extends Item {

    public BetterWrenchItem(Properties properties) {
        super(properties.rarity(Rarity.COMMON).stacksTo(1));
    }

    /**
     * 按材质标记现算稀有度(决定名字颜色): 光辉石 + 暗影钢 = EPIC(紫), 只有其一 = UNCOMMON(黄),
     * 都没有 = 物品默认(COMMON/白)。
     */
    @Override
    public Rarity getRarity(ItemStack stack) {
        boolean radiance = WrenchGlowComponent.isGlowing(stack);
        boolean shadowSteel = WrenchShadowSteelComponent.isShadowSteel(stack);
        if (radiance && shadowSteel)
            return Rarity.EPIC;
        if (radiance || shadowSteel)
            return Rarity.UNCOMMON;
        return super.getRarity(stack);
    }

    /**
     * 在名字<b>下一行</b>插入材质说明行: 有光辉石标记就给「+光辉石」, 有暗影钢标记就给「+暗影钢」,
     * 两者都有则两行都在(顺序: 光辉石在前, 暗影钢在后); 普通扳手什么都不加。
     *
     * <p>1.20.1 的 {@code ItemStack#getTooltipLines} 同样先把名字放进列表(下标 0)再调用本方法, 因此从下标 1
     * 开始插入就是「紧随物品名之后的第一行(们)」; Create 的 Shift / Ctrl 提示由客户端事件在更后面追加,
     * 排在这些行之下。每行的颜色取对应材料的稀有度样式 —— 光辉石与暗影钢都是 {@code Rarity.UNCOMMON},
     * 即黄色, 与材料名字同色; 两行都取黄色, 即使此时物品名本身因组合而变成紫色。文案用
     * {@code translatableWithFallback}, 语言键缺失时仍显示兜底文本。</p>
     */
    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        List<Component> variantLines = new ArrayList<>(2);
        if (WrenchGlowComponent.isGlowing(stack))
            variantLines.add(materialLine("refined_radiance", "+光辉石"));
        if (WrenchShadowSteelComponent.isShadowSteel(stack))
            variantLines.add(materialLine("shadow_steel", "+暗影钢"));
        for (int i = 0; i < variantLines.size(); i++)
            tooltip.add(Math.min(1 + i, tooltip.size()), variantLines.get(i));
    }

    /** 一条材质说明行: 颜色与材料名字同色(两种材料都是 UNCOMMON 的黄), 语言键缺失时用兜底文本。 */
    private static Component materialLine(String material, String fallback) {
        return Component.translatableWithFallback(
            "item." + BetterWrenchMod.MODID + ".better_wrench.tooltip." + material, fallback)
            .withStyle(Rarity.UNCOMMON.getStyleModifier());
    }
}
