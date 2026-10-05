package com.nonono.createbetterwrench.item;

import java.util.ArrayList;
import java.util.List;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

/**
 * 「万能扳手」物品。
 *
 * <p>要让它被 Create 当作扳手(对 {@code IWrenchable} 方块右键转动/拆除), 已加入 {@code c:tools/wrench} 标签。</p>
 *
 * <p>攻击加成(伤害 +5 / 攻速 +20)与取消无敌帧属于战斗加成(可选), <b>不再烘焙在物品属性里</b>;
 * 只有在「模组描述」模式里用 Ctrl 切到战斗模式且手持本扳手时才生效, 详见
 * {@link com.nonono.createbetterwrench.combat.WrenchCombat}。</p>
 *
 * <p>实验(分支 exp/wrench-glow): 正常合成出来的扳手是<b>普通扳手</b> —— 不带任何材质标记、名字为默认白色、
 * 没有额外说明行。在铁砧上用材料升级(见 {@link WrenchGlowItemEvents})会得到材质变体: 光辉石写入
 * {@code create_better_wrench:glow}(同时开启外发光), 暗影钢写入 {@code create_better_wrench:shadow_steel}
 * (外观换成暗影钢材质)。两个标记互相独立, 可以同时存在; 名字颜色按组合刷新 —— 单一材料
 * {@code Rarity.UNCOMMON}(黄, 与材料同色), 两者都有 {@code Rarity.EPIC}(紫, 与原版附魔金苹果同级)。
 * 按最终取舍<b>不附加附魔光效</b>。</p>
 *
 * <p><b>名字颜色为什么用数据组件而不是覆写方法</b>: 1.21.1 的 {@code ItemStack#getTooltipLines} 用
 * {@code this.getRarity().getStyleModifier()} 给名字上色, 而 {@code ItemStack#getRarity()} 只读
 * {@code minecraft:rarity} 组件(附魔时才会自动升一档); {@code Item} 上没有接收 ItemStack 的
 * {@code getRarity} 可供覆写。因此发光扳手的黄色名字由升级时写入光辉石的
 * {@code Rarity.UNCOMMON}({@code Rarity.UNCOMMON.getStyleModifier()} 即 {@code ChatFormatting.YELLOW})
 * 实现, 与光辉石名字同色。</p>
 */
public class BetterWrenchItem extends Item {

    public BetterWrenchItem(Properties properties) {
        super(properties.rarity(Rarity.COMMON).stacksTo(1));
    }

    /**
     * 在名字<b>下一行</b>插入材质说明行: 有光辉石标记就给「+光辉石」, 有暗影钢标记就给「+暗影钢」,
     * 两者都有则两行都在(顺序: 光辉石在前, 暗影钢在后); 普通扳手什么都不加。
     *
     * <p>1.21.1 的 {@code ItemStack#getTooltipLines} 先把名字放进列表(下标 0)再调用本方法, 因此从下标 1 开始插入
     * 就是「紧随物品名之后的第一行(们)」; Create 的 Shift / Ctrl 提示由客户端事件在更后面追加, 排在这些行之下。
     * 每行的颜色取对应材料的稀有度样式 —— 光辉石与暗影钢都是 {@code Rarity.UNCOMMON}, 即黄色, 与材料名字同色;
     * 两行都取黄色, 即使此时物品名本身因组合而变成紫色。文案用 {@code translatableWithFallback}, 语言键缺失时
     * 仍显示兜底文本。</p>
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
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
