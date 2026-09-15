package com.nonono.createbetterwrench.assemble;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 「工作」模式的**料堆**: 一个置物台只能渲染一个物品堆(在正中间), 所以为了让
 * 「原料 / 正在加工 / 成品」三者同时可见, 我们在置物台的**两个对角**各放一个掉落物实体作为料堆:
 *
 * <ul>
 *   <li><b>原料堆(RAW)</b> —— 置物台的西北角;**不可被玩家拾取**, 解锁置物台时才释放。</li>
 *   <li><b>正在加工</b> —— 正中间, 就是置物台本身持有的那 1 个物品(由 Create 正常渲染)。</li>
 *   <li><b>成品堆(DONE)</b> —— 置物台的东南角;可被玩家正常拾取。</li>
 * </ul>
 *
 * <p>通过实体自身的持久化数据打标记(置物台坐标 + 料堆种类)来识别与自己配对的那两个实体,
 * 不需要额外注册实体类型, 也不占用区块外的存储。</p>
 */
public final class DepotPiles {

    public static final String RAW = "raw";
    public static final String DONE = "done";

    private static final String TAG_POS = "cbw_depot_pos";
    private static final String TAG_KIND = "cbw_depot_pile";

    /** 料堆相对置物台中心的水平偏移(置物台中心 = 方块中心 x+0.5 / z+0.5)。 */
    private static final double CORNER_OFFSET = 0.27;

    private DepotPiles() {
    }

    // ---------------------------------------------------------------- 对外

    /** 把一批物品并入指定料堆(超出堆叠上限的部分直接掉在原地)。 */
    public static void deposit(Level level, BlockPos pos, String kind, ItemStack stack) {
        if (level.isClientSide || stack.isEmpty())
            return;

        ItemStack remaining = stack.copy();
        Optional<ItemEntity> existing = find(level, pos, kind);
        if (existing.isPresent()) {
            ItemEntity pile = existing.get();
            ItemStack current = pile.getItem();
            if (ItemStack.isSameItemSameComponents(current, remaining)) {
                int room = current.getMaxStackSize() - current.getCount();
                int moved = Math.min(room, remaining.getCount());
                if (moved > 0) {
                    current.grow(moved);
                    remaining.shrink(moved);
                    pile.setItem(current);
                }
            }
        }

        if (remaining.isEmpty())
            return;

        ItemEntity pile = createAt(level, pos, kind, remaining);
        level.addFreshEntity(pile);
    }

    /** 从指定料堆取出至多 max 个物品(料堆空了就移除实体)。 */
    public static ItemStack take(Level level, BlockPos pos, String kind, int max) {
        if (level.isClientSide || max <= 0)
            return ItemStack.EMPTY;
        Optional<ItemEntity> found = find(level, pos, kind);
        if (found.isEmpty())
            return ItemStack.EMPTY;

        ItemEntity pile = found.get();
        ItemStack current = pile.getItem();
        int moved = Math.min(max, current.getCount());
        ItemStack out = current.copyWithCount(moved);
        current.shrink(moved);
        if (current.isEmpty())
            pile.discard();
        else
            pile.setItem(current);
        return out;
    }

    public static boolean hasAny(Level level, BlockPos pos, String kind) {
        return find(level, pos, kind).isPresent();
    }

    /**
     * 解锁置物台时调用: 把两个料堆都**释放成普通掉落物**(原料堆解除"不可拾取"),
     * 让玩家能正常把东西收回去。清理标记, 之后就不再与这个置物台配对。
     */
    public static void releaseAll(Level level, BlockPos pos) {
        if (level.isClientSide)
            return;
        for (String kind : new String[] { RAW, DONE }) {
            Optional<ItemEntity> found = find(level, pos, kind);
            if (found.isEmpty())
                continue;
            ItemEntity pile = found.get();
            pile.getPersistentData().remove(TAG_POS);
            pile.getPersistentData().remove(TAG_KIND);
            pile.setPickUpDelay(0); // setNeverPickUp() 是无参的, 恢复拾取要把延迟清零
            pile.setUnlimitedLifetime();
        }
    }

    /** 直接把物品弹成普通掉落物(装配失败时用)。 */
    public static void eject(Level level, BlockPos pos, ItemStack stack) {
        if (level.isClientSide || stack.isEmpty())
            return;
        Block.popResource(level, pos.above(), stack);
    }

    // ---------------------------------------------------------------- 内部

    private static Optional<ItemEntity> find(Level level, BlockPos pos, String kind) {
        long key = pos.asLong();
        AABB area = new AABB(pos).inflate(1.5);
        return level.getEntitiesOfClass(ItemEntity.class, area, e -> {
            var data = e.getPersistentData();
            return data.contains(TAG_POS) && data.getLong(TAG_POS) == key
                && kind.equals(data.getString(TAG_KIND));
        }).stream().findFirst();
    }

    private static ItemEntity createAt(Level level, BlockPos pos, String kind, ItemStack stack) {
        Vec3 anchor = anchor(pos, kind);
        ItemEntity pile = new ItemEntity(level, anchor.x, anchor.y, anchor.z, stack);
        pile.getPersistentData().putLong(TAG_POS, pos.asLong());
        pile.getPersistentData().putString(TAG_KIND, kind);
        pile.setUnlimitedLifetime();
        if (RAW.equals(kind))
            pile.setNeverPickUp(); // 原料堆在锁定期间不允许玩家直接拿走
        pile.setDeltaMovement(Vec3.ZERO);
        return pile;
    }

    private static Vec3 anchor(BlockPos pos, String kind) {
        double sign = RAW.equals(kind) ? -CORNER_OFFSET : CORNER_OFFSET;
        return new Vec3(pos.getX() + 0.5 + sign, pos.getY() + 1.0, pos.getZ() + 0.5 + sign);
    }
}
