package com.nonono.createbetterwrench.assemble;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 「加工」模式的<b>两个料堆</b>: 一个置物台只能渲染一个物品堆(在正中间), 所以台面四周用掉落物实体
 * 摆两个堆, 位置以<b>锁定置物台时玩家的水平朝向</b>为基准({@link AssembleLock#facing}):
 *
 * <ul>
 *   <li><b>原料堆(RAW)</b> —— 玩家<b>右手侧的前角</b>(面朝北锁定时为东北角);</li>
 *   <li><b>半成品堆(SEMI)</b> —— 玩家<b>左手侧的前角</b>(面朝北锁定时为西北角), 只放序列装配与注液的
 *       <b>中间产物</b>;</li>
 *   <li><b>正在加工</b> —— 正中间, 就是置物台本身持有的那 1 个物品(由 Create 正常渲染)。</li>
 * </ul>
 *
 * <p>锁定后玩家转身不再改变布局: 朝向在锁定那一刻写进数据附件, 随存档保存。</p>
 *
 * <p><b>续料优先级</b>: {@link #take} <b>先取半成品堆, 取空后才取原料堆</b> —— 因此序列装配每一步的
 * 中间产物放进半成品堆后, 下一次交互会自动把它送回台面继续推进。</p>
 *
 * <p><b>「成品堆」已不存在</b>: 加工产出(含注液批量产出)一律作为<b>普通掉落物</b>直接落地 ——
 * 不打任何持久化标记、不 {@code setUnlimitedLifetime()}; 序列与注液的中间产物则进半成品堆。</p>
 *
 * <p>通过实体自身的持久化数据打标记(置物台坐标 + 料堆种类)来识别与自己配对的实体,
 * 不需要额外注册实体类型, 也不占用区块外的存储。</p>
 *
 * <h2>两个必须理解的前提</h2>
 * <ol>
 *   <li><b>同一种料堆可以同时存在多个实体</b>, 所以本类所有操作都必须遍历<b>全部</b>匹配实体,
 *       不能像早期版本那样用 {@code findFirst()} 只认第一个 —— 见 {@link #deposit} 的详细说明。</li>
 *   <li><b>料堆带正常重力, 会自己落到置物台台面上停住</b>。
 *       它<b>不会</b>被置物台吸走, 因为普通置物台的合并是关闭的 —— 详见 {@link #createAt} 的说明。</li>
 * </ol>
 */
public final class DepotPiles {

    public static final String RAW = "raw";
    public static final String SEMI = "semi";

    private static final String TAG_POS = "cbw_depot_pos";
    private static final String TAG_KIND = "cbw_depot_pile";

    /**
     * 料堆相对置物台中心的<b>单轴</b>水平偏移(置物台中心 = 方块中心 x+0.5 / z+0.5)。
     *
     * <p>2026-10-03: 两个料堆各自占"前方的一个角", 因此实际偏移是<b>两个轴向分量之和</b>
     * (前方 + 侧向, 见 {@link #cornerOffset}); 产出落点用同一常量摆在玩家背后, 三者共用这一个来源。</p>
     */
    static final double CORNER_OFFSET = 0.27;

    /**
     * 释放料堆时沿自身对角再推出的距离。
     * 取 1.0 是为了让落点<b>完全离开置物台所在的那一格</b>(落在对角相邻方块的上方),
     * 这样恢复重力后它不会又掉回置物台上被吸走。
     */
    private static final double RELEASE_OFFSET = 1.0;

    private DepotPiles() {
    }

    // ---------------------------------------------------------------- 对外

    /** 把一批物品并入<b>原料堆</b>。 */
    public static void deposit(Level level, BlockPos pos, ItemStack stack) {
        deposit(level, pos, stack, RAW);
    }

    /** 把一批物品并入<b>半成品堆</b>(序列装配与注液的中间产物走这里)。 */
    public static void depositSemi(Level level, BlockPos pos, ItemStack stack) {
        deposit(level, pos, stack, SEMI);
    }

    /**
     * 把一批物品并入指定种类的料堆(装不下的部分<b>继续新建实体</b>, 直到全部放完)。
     *
     * <p>注意: 审计发现 #5 —— 同一种料堆<b>可以同时存在多个实体</b>, 这是设计上必然的, 不是异常:</p>
     * <ul>
     *   <li>{@link ItemEntity#setUnlimitedLifetime()} 把 {@code age} 置为 -32768, 而原版
     *       {@code ItemEntity.isMergable()} 明确要求 {@code age != -32768}, 因此
     *       <b>料堆之间永远不会被原版逻辑自动合并</b>;</li>
     *   <li>本方法在物品不同或超出堆叠上限时本来就会新建实体, 一次可以造出多个同种实体。</li>
     * </ul>
     */
    public static void deposit(Level level, BlockPos pos, ItemStack stack, String kind) {
        if (level.isClientSide || stack.isEmpty())
            return;

        ItemStack remaining = stack.copy();

        // ① 先并入已有的同类料堆
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
        Direction facing = facingAt(level, pos);
        while (!remaining.isEmpty()) {
            int per = Math.min(remaining.getCount(), Math.max(1, remaining.getMaxStackSize()));
            ItemStack part = remaining.copyWithCount(per);
            remaining.shrink(per);
            level.addFreshEntity(createAt(level, pos, part, kind, facing));
        }
    }

    /**
     * 从料堆取出至多 max 个物品(可跨多个实体; 被取空的实体直接移除)。
     *
     * <p><b>优先取半成品堆</b>, 半成品堆空了才取原料堆(2026-10-03 起): 序列装配与注液的中间产物
     * 进入半成品堆后, 下一次续料会自动把它送回台面。</p>
     */
    public static ItemStack take(Level level, BlockPos pos, int max) {
        ItemStack fromSemi = takeFrom(level, pos, max, SEMI);
        if (!fromSemi.isEmpty() && fromSemi.getCount() >= max)
            return fromSemi;
        int missing = max - (fromSemi.isEmpty() ? 0 : fromSemi.getCount());
        ItemStack fromRaw = takeFrom(level, pos, missing, RAW);
        if (fromSemi.isEmpty())
            return fromRaw;
        if (fromRaw.isEmpty())
            return fromSemi;
        fromSemi.grow(fromRaw.getCount());
        return fromSemi;
    }

    /**
     * 从指定种类的料堆取出至多 max 个物品。
     *
     * <p>注意: 审计 A-10 —— 只有当料堆与已取出部分的<b>物品与组件完全一致</b>时才允许并入。
     * 若某个料堆装的是别的东西(外部 mod / 手改存档塞进来的实体), 直接<b>跳过</b>它,
     * 否则 {@code ItemStack#grow} 对不同物品是空操作, 而下面的 {@code shrink}/{@code discard}
     * 却照常执行, 会<b>静默吞掉</b>那些物品。</p>
     */
    public static ItemStack takeFrom(Level level, BlockPos pos, int max, String kind) {
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
            // 与已取出部分的物品/组件不一致则跳过, 不能 grow + shrink 造成静默丢失
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

    /** 该置物台是否还有任意种类的料堆。 */
    public static boolean hasAny(Level level, BlockPos pos) {
        return !findAll(level, pos, RAW).isEmpty() || !findAll(level, pos, SEMI).isEmpty();
    }

    /** 该置物台的<b>半成品堆</b>是否非空(续料优先级的判据之一)。 */
    public static boolean hasSemi(Level level, BlockPos pos) {
        return !findAll(level, pos, SEMI).isEmpty();
    }

    /**
     * 置物台被破坏 / 炸毁 / 解锁时调用: 把<b>两种料堆的全部实体</b>都释放成普通掉落物。
     *
     * <p>做三件事: ①清掉配对标记(之后不再与这个置物台关联); ②解除不可拾取状态并把寿命从
     * 永生(-32768)恢复到会正常消失; ③<b>沿自身对角推出一格</b>。</p>
     */
    public static void releaseAll(Level level, BlockPos pos) {
        if (level.isClientSide)
            return;
        for (String kind : new String[] { RAW, SEMI })
            for (ItemEntity pile : findAll(level, pos, kind)) {
                pile.getPersistentData().remove(TAG_POS);
                pile.getPersistentData().remove(TAG_KIND);
                pile.setPickUpDelay(0);      // setNeverPickUp() 设的是 32767, 恢复拾取必须清零
                pile.setExtendedLifetime();  // 从永生(-32768)回到会正常消失的寿命, 同时恢复可合并
                pile.setNoGravity(false);
                Vec3 out = releasePos(pos, kind, facingAt(level, pos));
                pile.setPos(out.x, out.y, out.z);
                pile.setDeltaMovement(Vec3.ZERO);
            }
    }

    /**
     * 取出该置物台<b>两种料堆</b>的全部内容, 并<b>移除</b>这些实体。
     *
     * <p>供「解锁置物台时把东西返还到玩家背包」使用 —— 与 {@link #releaseAll} 不同,
     * 这里不把它们留在世界上, 而是把内容交回调用方。</p>
     */
    public static List<ItemStack> drainAll(Level level, BlockPos pos) {
        List<ItemStack> out = new ArrayList<>();
        if (level.isClientSide)
            return out;
        for (String kind : new String[] { RAW, SEMI })
            for (ItemEntity pile : findAll(level, pos, kind)) {
                ItemStack stack = pile.getItem();
                if (!stack.isEmpty())
                    out.add(stack.copy());
                pile.discard();
            }
        return out;
    }

    // ---------------------------------------------------------------- 内部

    /** 读取该置物台锁定时的基准朝向(没有附件时为 NORTH)。 */
    static Direction facingAt(Level level, BlockPos pos) {
        if (level == null)
            return Direction.NORTH;
        return AssembleLock.facing(level.getBlockEntity(pos));
    }

    /**
     * 料堆相对置物台中心的水平偏移: <b>前方 + 侧向</b>两个分量之和。
     *
     * @param rightSide true = 玩家右手侧的角(原料堆), false = 左手侧的角(半成品堆)
     */
    static double[] cornerOffset(Direction facing, boolean rightSide) {
        Direction lateral = rightSide ? facing.getClockWise() : facing.getCounterClockWise();
        return new double[] {
            (facing.getStepX() + lateral.getStepX()) * CORNER_OFFSET,
            (facing.getStepZ() + lateral.getStepZ()) * CORNER_OFFSET
        };
    }

    /** 料堆的<b>展示</b>锚点(对应角的正上方)。 */
    private static Vec3 anchor(BlockPos pos, String kind, Direction facing) {
        double[] off = cornerOffset(facing, RAW.equals(kind));
        return new Vec3(pos.getX() + 0.5 + off[0], pos.getY() + 1.0, pos.getZ() + 0.5 + off[1]);
    }

    /** 找出与该置物台配对的<b>全部</b>指定种类料堆实体(早期版本只取第一个, 是审计 #5 的根因)。 */
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

    private static ItemEntity createAt(Level level, BlockPos pos, ItemStack stack, String kind, Direction facing) {
        Vec3 anchor = anchor(pos, kind, facing);
        ItemEntity pile = new ItemEntity(level, anchor.x, anchor.y, anchor.z, stack);
        pile.getPersistentData().putLong(TAG_POS, pos.asLong());
        pile.getPersistentData().putString(TAG_KIND, kind);
        pile.setUnlimitedLifetime();
        pile.setNeverPickUp(); // 料堆在锁定期间不允许玩家直接拿走

        // 料堆带正常重力: 从锚点自然落到置物台台面上停住(置物台碰撞高度 = 13/16 格)。
        //
        // 早期版本曾为避免被置物台吸走而 setNoGravity(true) 悬在半空, 那是过度修改:
        //    落地确实会走 DepotBlock.updateEntityAfterFallOn,
        //    再经 SharedDepotBlockMethods.onLanded 到 DirectBeltInputBehaviour.handleInsertion,
        //    但普通置物台合并是关闭的
        //    (DepotBehaviour.canMergeItems() = allowMerge, 而 enableMerging() 只有 EjectorBlockEntity 调),
        //    所以 DepotBehaviour.isOccupied() 里 {@code !getHeldItemStack().isEmpty() && !canMergeItems()}
        //    只要台面有东西就成立, tryInsertingFromSide 直接拒收, 掉落物只是停在台面上。
        //    只有台面恰好空着的那一刻落下的才会被收进去, 而且那也不是死锁(解锁即可取回)。
        pile.setDeltaMovement(Vec3.ZERO);
        return pile;
    }

    /** 料堆的<b>释放</b>位置: 沿自身那个角的方向再推出一格, 保证落点不在置物台的 1×1 投影内。 */
    private static Vec3 releasePos(BlockPos pos, String kind, Direction facing) {
        double[] off = cornerOffset(facing, RAW.equals(kind));
        double scale = (CORNER_OFFSET + RELEASE_OFFSET) / Math.max(0.0001, Math.hypot(off[0], off[1]));
        return new Vec3(pos.getX() + 0.5 + off[0] * scale, pos.getY() + 1.0, pos.getZ() + 0.5 + off[1] * scale);
    }
}