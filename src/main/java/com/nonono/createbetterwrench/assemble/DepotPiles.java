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
 * 「工作」模式的**原料堆**: 一个置物台只能渲染一个物品堆(在正中间), 所以原料被摆在
 * 置物台**西北角**的掉落物实体里。于是台面附近只有两个位置:
 *
 * <ul>
 *   <li><b>原料堆(RAW)</b> —— 置物台的西北角;**不可被玩家拾取**, 解锁置物台时才释放。</li>
 *   <li><b>正在加工</b> —— 正中间, 就是置物台本身持有的那 1 个物品(由 Create 正常渲染)。</li>
 * </ul>
 *
 * <p><b>已经没有「成品堆」了</b>: 加工产出(含注液批量产出)一律作为**普通掉落物**直接落地 ——
 * 不打任何持久化标记、不 {@code setUnlimitedLifetime()}, 因此会像普通掉落物一样被正常拾取,
 * 也会正常消失(见 {@code AssembleLogic} 里的落物 helper)。本类因此只管原料堆一件事。</p>
 *
 * <p>通过实体自身的持久化数据打标记(置物台坐标 + 料堆种类)来识别与自己配对的实体,
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

    private static final String TAG_POS = "cbw_depot_pos";
    private static final String TAG_KIND = "cbw_depot_pile";

    /**
     * 料堆相对置物台中心的水平偏移(置物台中心 = 方块中心 x+0.5 / z+0.5)。
     *
     * <p>⚠️ **这是"置物台角偏移"的单一来源**(复审 B-17): 原料堆摆在**西北角**({@code center − 本值}),
     * 产出落在**东南角**({@code center + 本值}, 见 {@code AssembleLogic.productDropPos}) —— 两者**必须同值**
     * 才是对角对称、产出才不会和原料混在一处, 所以那边直接引用本常量, 不再各写一份 0.27。</p>
     */
    static final double CORNER_OFFSET = 0.27;

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
     * 把一批物品并入**原料堆**(装不下的部分**继续新建实体**, 直到全部放完)。
     *
     * <p>⚠️ 审计发现 #5: 同一种料堆**可以同时存在多个实体**, 这是设计上必然的, 不是异常:</p>
     * <ul>
     *   <li>{@link ItemEntity#setUnlimitedLifetime()} 把 {@code age} 置为 -32768, 而原版
     *       {@code ItemEntity.isMergable()} 明确要求 {@code age != -32768} ⇒
     *       <b>料堆之间永远不会被原版逻辑自动合并</b>;</li>
     *   <li>本方法在"物品不同"或"超出堆叠上限"时本来就会新建实体, 一次可以造出多个同种实体。</li>
     * </ul>
     *
     * <p>旧实现用 {@code findFirst()} 找料堆、{@code releaseAll} 只释放一个,
     * 于是**只有第一个**实体被释放, 其余永久停留在 {@code pickupDelay=32767} + {@code age=-32768}
     * (既拿不走也不会消失)—— 一条明确的可永久丢失物品路径。现在全部按多实体处理。</p>
     */
    public static void deposit(Level level, BlockPos pos, ItemStack stack) {
        if (level.isClientSide || stack.isEmpty())
            return;

        ItemStack remaining = stack.copy();

        // ① 先并入**所有**已有的原料堆
        for (ItemEntity pile : findAll(level, pos)) {
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
            level.addFreshEntity(createAt(level, pos, part));
        }
    }

    /**
     * 从原料堆取出至多 max 个物品(可跨多个实体; 被取空的实体直接移除)。
     *
     * <p>⚠️ 审计 A-10: 只有当料堆与已取出部分的**物品与组件完全一致**时才允许并入。
     * 若某个料堆装的是别的东西(外部 mod / 手改存档塞进来的实体), 直接**跳过**它 ——
     * 否则 {@code ItemStack#grow} 对不同物品是空操作, 而下面的 {@code shrink}/{@code discard}
     * 却照常执行, 会**静默吞掉**那些物品。</p>
     */
    public static ItemStack take(Level level, BlockPos pos, int max) {
        if (level.isClientSide || max <= 0)
            return ItemStack.EMPTY;

        int remain = max;
        ItemStack out = ItemStack.EMPTY;
        for (ItemEntity pile : findAll(level, pos)) {
            if (remain <= 0)
                break;
            ItemStack current = pile.getItem();
            if (current.isEmpty())
                continue;
            // 已取出部分的物品/组件不一致 => 跳过, 绝不 grow + shrink 造成静默丢失
            if (!out.isEmpty() && !ItemStack.isSameItemSameComponents(out, current))
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

    public static boolean hasAny(Level level, BlockPos pos) {
        return !findAll(level, pos).isEmpty();
    }

    /**
     * 置物台被破坏 / 炸毁 / 解锁时调用: 把**全部**原料堆都释放成普通掉落物。
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
        for (ItemEntity pile : findAll(level, pos)) {
            pile.getPersistentData().remove(TAG_POS);
            pile.getPersistentData().remove(TAG_KIND);
            pile.setPickUpDelay(0);      // setNeverPickUp() 设的是 32767, 恢复拾取必须清零
            pile.setExtendedLifetime();  // 从"永生"(-32768)回到会正常消失的寿命, 同时恢复可合并
            pile.setNoGravity(false);
            Vec3 out = releasePos(pos);
            pile.setPos(out.x, out.y, out.z);
            pile.setDeltaMovement(Vec3.ZERO);
        }
    }

    /**
     * 取出该置物台原料堆的**全部**内容, 并**移除**这些实体。
     *
     * <p>供「解锁置物台时把东西返还到玩家背包」使用 —— 与 {@link #releaseAll} 不同,
     * 这里不把它们留在世界上, 而是把内容交回调用方。</p>
     *
     * <p>注意: 已经弹出、落在地上的成品是**普通掉落物**, 与本类再无关系, 因此不会被这里收走
     * (它们本来就留在世界上等玩家拾取)。</p>
     */
    public static List<ItemStack> drainAll(Level level, BlockPos pos) {
        List<ItemStack> out = new ArrayList<>();
        if (level.isClientSide)
            return out;
        for (ItemEntity pile : findAll(level, pos)) {
            ItemStack stack = pile.getItem();
            if (!stack.isEmpty())
                out.add(stack.copy());
            pile.discard();
        }
        return out;
    }

    // ---------------------------------------------------------------- 内部

    /** 原料堆的**展示**锚点(置物台西北角正上方)。 */
    private static Vec3 rawAnchor(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5 - CORNER_OFFSET, pos.getY() + 1.0, pos.getZ() + 0.5 - CORNER_OFFSET);
    }

    /** 找出与该置物台配对的**全部**原料堆实体(早期版本只取第一个, 是审计 #5 的根因)。 */
    private static List<ItemEntity> findAll(Level level, BlockPos pos) {
        long key = pos.asLong();
        AABB area = new AABB(pos).inflate(1.5);
        List<ItemEntity> found = new ArrayList<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
            CompoundTag data = entity.getPersistentData();
            if (data.contains(TAG_POS) && data.getLong(TAG_POS) == key
                && RAW.equals(data.getString(TAG_KIND)))
                found.add(entity);
        }
        return found;
    }

    private static ItemEntity createAt(Level level, BlockPos pos, ItemStack stack) {
        Vec3 anchor = rawAnchor(pos);
        ItemEntity pile = new ItemEntity(level, anchor.x, anchor.y, anchor.z, stack);
        pile.getPersistentData().putLong(TAG_POS, pos.asLong());
        pile.getPersistentData().putString(TAG_KIND, RAW);
        pile.setUnlimitedLifetime();
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
        pile.setDeltaMovement(Vec3.ZERO);
        return pile;
    }

    /** 料堆的**释放**位置: 沿同一对角再推出一格, 保证落点不在置物台的 1×1 投影内。 */
    private static Vec3 releasePos(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5 - RELEASE_OFFSET, pos.getY() + 1.0, pos.getZ() + 0.5 - RELEASE_OFFSET);
    }
}
