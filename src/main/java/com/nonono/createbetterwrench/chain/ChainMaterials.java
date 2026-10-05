package com.nonono.createbetterwrench.chain;

import com.simibubi.create.AllBlocks;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 「锁链传动」模式的材料统计与扣除(锁链传动轮 + 锁链)。1.20.1 版与 1.21.1 版逐行对齐。
 *
 * <p>口径与「连接」模式的 {@code connect/ConnectLogic#countItem / consumeItem} 完全一致:
 * <b>只统计与扣除主背包</b>({@code Inventory#items}), 副手既不计数也不扣除 ——
 * 这是 2026-09-20 的设计约定, 客户端预览与服务端扣料共用同一口径。</p>
 *
 * <p>创造模式免材料: {@link #consume} 对创造模式玩家不做任何扣除; 计数方法仍如实返回背包数量,
 * 调用方按 {@link Player#isCreative()} 决定是否跳过材料校验。</p>
 *
 * <p>平台差异: Create 1.20.1 的 {@code AllBlocks} 是 Forge/Registrate 的延迟注册表,
 * 取物品用 {@code AllBlocks.CHAIN_CONVEYOR.get().asItem()}(1.21.1 线可直接 {@code asItem()})。</p>
 */
public final class ChainMaterials {

    private ChainMaterials() {
    }

    /** 主背包内某种物品的总数。 */
    public static int count(Player player, Item item) {
        if (player == null || item == null)
            return 0;
        int count = 0;
        for (ItemStack stack : player.getInventory().items)
            if (stack.getItem() == item)
                count += stack.getCount();
        return count;
    }

    /** 主背包内的锁链传动轮数量({@code create:chain_conveyor})。 */
    public static int countConveyors(Player player) {
        return count(player, AllBlocks.CHAIN_CONVEYOR.get().asItem());
    }

    /** 主背包内的锁链数量({@code minecraft:chain})。 */
    public static int countChains(Player player) {
        return count(player, Items.CHAIN);
    }

    /**
     * 从主背包扣除指定物品。
     *
     * <p>扣不满(背包数量不足)时不抛异常, 也<b>不改动任何已有堆叠之外的东西</b> ——
     * 调用方必须在调用前用 {@link #count} 校验足量, 否则会出现"扣了一半"。
     * 与「连接」模式一样, 这里不打印日志: 是否足量是调用方的责任。</p>
     */
    public static void consume(Player player, Item item, int amount) {
        if (player == null || item == null || amount <= 0)
            return;
        if (player.isCreative())
            return;                                  // 创造模式免材料
        int remain = amount;
        for (int i = player.getInventory().items.size() - 1; i >= 0 && remain > 0; i--) {
            ItemStack stack = player.getInventory().items.get(i);
            if (stack.getItem() != item)
                continue;
            int take = Math.min(remain, stack.getCount());
            stack.shrink(take);
            remain -= take;
        }
    }
}
