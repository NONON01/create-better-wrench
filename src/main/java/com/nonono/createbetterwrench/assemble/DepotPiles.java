package com.nonono.createbetterwrench.assemble;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 「工作」模式的**料堆**: 一个置物台只能渲染一个物品堆(在正中间), 所以为了让
 * 「原料 / 正在加工 / 成品」三者同时可见, 我们在置物台的**两个对角**各放掉落物实体作为料堆:
 *
 * <ul>
 *   <li><b>原料堆(RAW)</b> —— 置物台的西北角;**不可被玩家拾取**, 解锁置物台时才释放。</li>
 *   <li><b>正在加工</b> —— 正中间, 就是置物台本身持有的那 1 个物品(由 Create 正常渲染)。</li>
 *   <li><b>成品堆(DONE)</b> —— 置物台的东南角;可被玩家正常拾取。</li>
 * </ul>
 *
 * <p>通过实体自身的持久化数据打标记(置物台坐标 + 料堆种类)来识别与自己配对的那两个实体,
 * 不需要额外注册实体类型, 也不占用区块外的存储。</p>
 *
 * <h2>⚠️ 两个必须理解的前提</h2>
 * <ol>
 *   <li><b>同一种料堆可以同时存在多个实体</b>, 所以本类所有操作都必须遍历**全部**匹配实体,
 *       绝不能像早期版本那样用 {@code findFirst()} 只认第一个 —— 见 {@link #deposit} 的详细说明。</li>
 *   <li><b>料堆带正常重力, 会自己落到置物台台面上停住</b>。
 *       它**不会**被置物台吸走, 因为普通置物台的合并是关闭的 —— 详见 {@link #createAt} 的说明。</li>
 * </ol>
 */
public final class DepotPiles {

    public static final String RAW = "raw";
    public static final String DONE = "done";

    private static final String TAG_POS = "cbw_depot_pos";
    private static final String TAG_KIND = "cbw_depot_pile";

    /** 料堆相对置物台中心的水平偏移(置物台中心 = 方块中心 x+0.5 / z+0.5)。 */
    private static final double CORNER_OFFSET = 0.27;

    /**
     * 释放料堆时沿对角再推出的距离。
     * 取 1.0 是为了让落点**完全离开置物台所在的那一格**(落在对角相邻方块的上方),
     * 这样恢复重力后它不会又掉回置物台上被吸走。
     */
    private static final double RELEASE_OFFSET = 1.0;

    private DepotPiles() {
    }

    // ---------------------------------------------------------------- 对外

    /**
     * 把一批物品并入指定料堆(装不下的部分**继续新建实体**, 直到全部放完)。
     *
     * <p>⚠️ 审计发现 #5: 同一种料堆**可以同时存在多个实体**, 这是设计上必然的, 不是异常:</p>
     * <ul>
     *   <li>{@link ItemEntity#setUnlimitedLifetime()} 把 {@code age} 置为 -32768, 而原版
     *       {@code ItemEntity.isMergable()} 明确要求 {@code age != -32768} ⇒
     *       <b>料堆之间永远不会被原版逻辑自动合并</b>;</li>
     *   <li>本方法在"物品不同"或"超出堆叠上限"时本来就会新建实体; 注液路径一次可产出 40~100 个物品
     *       ⇒ 一次就能造出 2~4 个同种实体。</li>
     * </ul>
     *
     * <p>旧实现用 {@code findFirst()} 找料堆、{@code releaseAll} 每种只释放一个,
     * 于是**只有第一个**实体被释放, 其余永久停留在 {@code pickupDelay=32767} + {@code age=-32768}
     * (既拿不走也不会消失)—— 一条明确的可永久丢失物品路径。现在全部按多实体处理。</p>
     */
    public static void deposit(Level level, BlockPos pos, String kind, ItemStack stack) {
        if (level.isClientSide || stack.isEmpty())
            return;

        ItemStack remaining = stack.copy();

        // ① 先并入**所有**已有的同种料堆
        for (ItemEntity pile : findAll(level, pos, kind)) {
            if (remaining.isEmpty())
                return;
            ItemStack current = pile.getItem();
            if (!ItemStack.isSameItemSameComponents(current, remaining))
                continue;
            int room = current.getMaxStackSize() - current.getCount();
            if (room <= 0)
                continue;
            int moved = Math.min(room, remaining.getCount());
            current.grow(moved);
            remaining.shrink(moved);
            pile.setItem(current);
        }

        if (remaining.isEmpty())
            return;

        // ② 剩下的: 一个实体装多少就建一个, 直到放完
        while (!remaining.isEmpty()) {
            int per = Math.min(remaining.getCount(), Math.max(1, remaining.getMaxStackSize()));
            ItemStack part = remaining.copyWithCount(per);
            remaining.shrink(per);
            level.addFreshEntity(createAt(level, pos, kind, part));
        }
    }

    /** 从指定料堆取出至多 max 个物品(可跨多个实体; 被取空的实体直接移除)。 */
    public static ItemStack take(Level level, BlockPos pos, String kind, int max) {
        if (level.isClientSide || max <= 0)
            return ItemStack.EMPTY;

        int remain = max;
        ItemStack out = ItemStack.EMPTY;
        for (ItemEntity pile : findAll(level, pos, kind)) {
            if (remain <= 0)
                break;
            ItemStack current = pile.getItem();
            if (current.isEmpty())
                continue;
            int moved = Math.min(remain, current.getCount());
            if (out.isEmpty())
                out = current.copyWithCount(moved);
            else
                out.grow(moved);
            current.shrink(moved);
            remain -= moved;
            if (current.isEmpty())
                pile.discard();
            else
                pile.setItem(current);
        }
        return out;
    }

    public static boolean hasAny(Level level, BlockPos pos, String kind) {
        return !findAll(level, pos, kind).isEmpty();
    }

    /**
     * 解锁置物台(或置物台被破坏/炸毁)时调用: 把**全部**料堆都释放成普通掉落物。
     *
     * <p>做三件事: ①清掉配对标记(之后不再与这个置物台关联); ②解除"不可拾取"并把寿命从
     * "永生"(-32768)恢复到会正常消失; ③**沿对角推出一格**。</p>
     *
     * <p>③ 是必要的: 释放后它就变成普通掉落物了, 如果还停在置物台台面上, 那么等台面下一次空着时
     * （例如玩家把东西取走)它就会被 `onLanded` 顺手收进置物台里 —— 那才是真的会丢东西。
     * 推到对角相邻格之后它落在**旁边**, 与置物台再无关系。</p>
     *
     * <p>{@code setNoGravity(false)} 现在只是兼容性保险: 2026-09-17 的中间版本曾把料堆设成无重力,
     * 存档里可能还留着那种实体。</p>
     */
    public static void releaseAll(Level level, BlockPos pos) {
        if (level.isClientSide)
            return;
        for (String kind : new String[] { RAW, DONE }) {
            for (ItemEntity pile : findAll(level, pos, kind)) {
                pile.getPersistentData().remove(TAG_POS);
                pile.getPersistentData().remove(TAG_KIND);
                pile.setPickUpDelay(0);      // setNeverPickUp() 设的是 32767, 恢复拾取必须清零
                pile.setExtendedLifetime();  // 从"永生"(-32768)回到会正常消失的寿命, 同时恢复可合并
                pile.setNoGravity(false);
                Vec3 out = releasePos(pos, kind);
                pile.setPos(out.x, out.y, out.z);
                pile.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    /**
     * 把物品从置物台上「**弹出**」: 生成一个从台面正上方飞出、带向上初速的**成品堆**掉落物。
     *
     * <p>与 {@link #deposit} 的唯一区别就是"弹出"这个观感(用户明确说喜欢);它**同样打成品堆标记**,
     * 因此①会被 {@link #findAll} 当成品堆看待;②解锁置物台时会被一起返还给玩家
     * (见 {@code AssembleLogic#returnHeldAndPiles})。</p>
     */
    public static void eject(Level level, BlockPos pos, ItemStack stack) {
        if (level.isClientSide || stack.isEmpty())
            return;
        ItemEntity pile = createAt(level, pos, DONE, stack.copy());
        // createAt 默认把成品堆放在东南角, 弹出改到**台面正中央**, 让它真的"从台面上弹出来"
        pile.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        pile.setDeltaMovement(
            (level.random.nextDouble() - 0.5) * 0.12,
            0.22,
            (level.random.nextDouble() - 0.5) * 0.12);
        level.addFreshEntity(pile);
    }

    /**
     * 取出该置物台配对的**全部**料堆内容, 并**移除**这些实体。
     *
     * <p>供「解锁置物台时把东西返还到玩家背包」使用 —— 与 {@link #releaseAll} 不同,
     * 这里不把它们留在世界上, 而是把内容交回调用方。</p>
     */
    public static List<ItemStack> drainAll(Level level, BlockPos pos) {
        List<ItemStack> out = new ArrayList<>();
        if (level.isClientSide)
            return out;
        for (String kind : new String[] { RAW, DONE }) {
            for (ItemEntity pile : findAll(level, pos, kind)) {
                ItemStack stack = pile.getItem();
                if (!stack.isEmpty())
                    out.add(stack.copy());
                pile.discard();
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 内部

    /** 找出与该置物台配对的**全部**指定种类料堆实体(早期版本只取第一个, 是审计 #5 的根因)。 */
    private static List<ItemEntity> findAll(Level level, BlockPos pos, String kind) {
        long key = pos.asLong();
        AABB area = new AABB(pos).inflate(1.5);
        List<ItemEntity> found = new ArrayList<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
            CompoundTag data = entity.getPersistentData();
            if (data.contains(TAG_POS) && data.getLong(TAG_POS) == key
                && kind.equals(data.getString(TAG_KIND)))
                found.add(entity);
        }
        return found;
    }

    private static ItemEntity createAt(Level level, BlockPos pos, String kind, ItemStack stack) {
        Vec3 anchor = anchor(pos, kind);
        ItemEntity pile = new ItemEntity(level, anchor.x, anchor.y, anchor.z, stack);
        pile.getPersistentData().putLong(TAG_POS, pos.asLong());
        pile.getPersistentData().putString(TAG_KIND, kind);
        pile.setUnlimitedLifetime();
        if (RAW.equals(kind))
            pile.setNeverPickUp(); // 原料堆在锁定期间不允许玩家直接拿走

        // 料堆带**正常重力**: 从锚点自然落到置物台台面上停住(置物台碰撞高度 = 13/16 格)。
        //
        // ⚠️ 这里曾经为避免"被置物台吸走"而 setNoGravity(true) 悬在半空, 那是**过度修改**:
        //    落地确实会走 DepotBlock.updateEntityAfterFallOn → SharedDepotBlockMethods.onLanded
        //    → DirectBeltInputBehaviour.handleInsertion, 但普通置物台**合并是关闭的**
        //    (DepotBehaviour.canMergeItems() = allowMerge, 而 enableMerging() 只有 EjectorBlockEntity 调),
        //    所以 DepotBehaviour.isOccupied() 里 `!getHeldItemStack().isEmpty() && !canMergeItems()`
        //    **只要台面有东西就成立** ⇒ tryInsertingFromSide 直接拒收, 掉落物只是停在台面上。
        //    只有"台面恰好空着的那一刻"落下的才会被收进去, 而且那也不是死锁(解锁即可取回)。
        //    详见 docs/03-operations.md 中本条修复的更正记录。
        pile.setDeltaMovement(Vec3.ZERO);
        return pile;
    }

    /** 料堆的**展示**位置: 置物台两对角的正上方(悬停, 靠 noGravity 保持)。 */
    private static Vec3 anchor(BlockPos pos, String kind) {
        double sign = RAW.equals(kind) ? -CORNER_OFFSET : CORNER_OFFSET;
        return new Vec3(pos.getX() + 0.5 + sign, pos.getY() + 1.0, pos.getZ() + 0.5 + sign);
    }

    /** 料堆的**释放**位置: 沿同一对角再推出一格, 保证落点不在置物台的 1×1 投影内。 */
    private static Vec3 releasePos(BlockPos pos, String kind) {
        double sign = RAW.equals(kind) ? -RELEASE_OFFSET : RELEASE_OFFSET;
        return new Vec3(pos.getX() + 0.5 + sign, pos.getY() + 1.0, pos.getZ() + 0.5 + sign);
    }
}
