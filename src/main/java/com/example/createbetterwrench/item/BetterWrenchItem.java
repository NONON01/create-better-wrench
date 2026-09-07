package com.example.createbetterwrench.item;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * 「万能扳手」物品。
 *
 * <p>要让它被 Create 当作扳手(对 IWrenchable 方块右键转/拆),已加入 {@code c:tools/wrench} 标签。
 * 额外攻击属性: 攻击伤害 +5、攻击速度 +20。</p>
 */
public class BetterWrenchItem extends Item {

    public BetterWrenchItem(Properties properties) {
        super(properties.rarity(Rarity.COMMON).stacksTo(1).attributes(buildAttributes()));
    }

    /** 攻击伤害 +5, 攻击速度 +20(主手)。 */
    private static ItemAttributeModifiers buildAttributes() {
        Holder<Attribute> damage = Attributes.ATTACK_DAMAGE;
        Holder<Attribute> speed = Attributes.ATTACK_SPEED;
        return ItemAttributeModifiers.builder()
            .add(damage, new AttributeModifier(
                ResourceLocation.fromNamespaceAndPath("create_better_wrench", "attack_damage"),
                5.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(speed, new AttributeModifier(
                ResourceLocation.fromNamespaceAndPath("create_better_wrench", "attack_speed"),
                20.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .build();
    }
}
