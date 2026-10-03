package com.nonono.createbetterwrench.client.ponder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

/**
 * 思索注册用的"取注册名"助手。
 *
 * <p>1.21.1 线的 Create 提供 {@code net.createmod.catnip.registry.RegisteredObjectsHelper}, 而 1.20.1 的
 * Ponder(1.0.91)里该助手已移到 {@code net.createmod.catnip.platform.services} 且是"平台服务"形态,
 * 直接做方法引用不成立。这里用一行等价实现替代: 取物品注册表里的键。</p>
 */
public final class PonderKeys {

    private PonderKeys() {
    }

    /** {@code ItemLike} 到注册名; 方块会取它对应的物品(与 Ponder 的既有约定一致)。 */
    public static ResourceLocation key(ItemLike itemLike) {
        return BuiltInRegistries.ITEM.getKey(itemLike.asItem());
    }
}
