package com.example.createbetterwrench.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * 独立新物品:"更好的扳手"。
 *
 * <p>当前骨架阶段仅是一个普通 Item 占位;核心逻辑(模式切换/连接等)由后续实现。
 * 要让它被 Create 当作扳手(对 IWrenchable 方块右键转/拆),需把它加进 {@code c:tools/wrench} 标签
 * (资源: data/c/tags/items/tools/wrench.json), Create 的 WrenchEventHandler 会自动接管。</p>
 */
public class BetterWrenchItem extends Item {

    public BetterWrenchItem(Properties properties) {
        super(properties.rarity(Rarity.COMMON).stacksTo(1));
    }
}
