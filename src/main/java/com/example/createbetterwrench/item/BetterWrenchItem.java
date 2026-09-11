package com.example.createbetterwrench.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * 「万能扳手」物品。
 *
 * <p>要让它被 Create 当作扳手(对 IWrenchable 方块右键转/拆),已加入 {@code c:tools/wrench} 标签。</p>
 *
 * <p>⚠️ 攻击加成(伤害 +5 / 攻速 +20)与取消无敌帧是**彩蛋**, **不再烘焙在物品里**;
 * 只有在「模组描述」模式里用 Ctrl 切到**战斗模式**且手持本扳手时才生效, 详见
 * {@link com.example.createbetterwrench.combat.WrenchCombat}。</p>
 */
public class BetterWrenchItem extends Item {

    public BetterWrenchItem(Properties properties) {
        super(properties.rarity(Rarity.COMMON).stacksTo(1));
    }
}
