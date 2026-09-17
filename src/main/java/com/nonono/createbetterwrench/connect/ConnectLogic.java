package com.nonono.createbetterwrench.connect;

import java.util.ArrayList;
import java.util.List;

import com.nonono.createbetterwrench.mode.ConnectCorner;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.AbstractSimpleShaftBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 「连接」模式的服务端核心 —— 拐点数量不限, 每段边按几何自动路由。
 *
 * <p>交互(用户 2026-09-07 重定义):
 * 起点 S(机械动力方块) → 若干普通方块拐点 c1..ck(数量不限) → 终点 E(机械动力方块)。</p>
 *
 * <p>每段相邻节点 a→b 的边按三档自动路由:
 * <ul>
 *   <li>差 1 个坐标(同轴)→ 直线铺轴, 无额外齿轮箱;</li>
 *   <li>差 2 个坐标(同一平面)→ 自动一次 90° 拐弯(拐点先对齐 a 的轴再沿 b 的轴, 角点放齿轮箱);</li>
 *   <li>差 3 个坐标(非同一平面, 需 ≥2 次拐弯)→ 拒连(NON_PLANAR)。</li>
 * </ul></p>
 *
 * <p>每个方向改变处(被点拐点 + 每条拐弯边的自动角点)放一个齿轮箱。默认材料: 轴 create:shaft +
 * 齿轮箱 create:gearbox(水平转) / create:vertical_gearbox(涉竖直转); 副手持轴变体则优先用副手。
 * 放置先校验材料足量, 不足整拒不部分放; 双源方向冲突预检防炸块。</p>
 */
public final class ConnectLogic {

    private static final int MAX_LEG = 64;

    private ConnectLogic() {
    }

    public enum Result {
        SUCCESS,
        UNLOADED,
        NOT_KINETIC,
        AXIS_MISMATCH,
        BAD_TURN,
        NON_PLANAR,
        SAME_POS,
        PATH_BLOCKED,
        CORNER_NO_ROOM,
        CONFLICTING_SOURCE,
        MATERIALS,
        TOO_LONG
    }

    /** 一截直线轴段(from→to, 不含两端, 沿 axis), to 若为节点则其上是齿轮箱。 */
    private static final class Leg {
        final Axis axis;
        final BlockPos from;
        final BlockPos to;
        Leg(Axis axis, BlockPos from, BlockPos to) {
            this.axis = axis;
            this.from = from;
            this.to = to;
        }
    }

    /** 一个齿轮箱放置点。 */
    public static final class GearboxPlace {
        public final BlockPos pos;
        public final Axis axis;
        public final boolean vertical;
        GearboxPlace(BlockPos pos, Axis axis, boolean vertical) {
            this.pos = pos;
            this.axis = axis;
            this.vertical = vertical;
        }
    }

    /** 一个大齿轮放置点(用于「大齿轮」拐角: 两块斜对角换轴)。 */
    public static final class CogPlace {
        public final BlockPos pos;
        public final Axis axis;
        CogPlace(BlockPos pos, Axis axis) {
            this.pos = pos;
            this.axis = axis;
        }
    }

    /** 铺设计划(只读几何, 不含材料物品; 客户端可据此做幽灵预览)。 */
    public static final class Plan {
        public final List<BlockPos> shaftPositions = new ArrayList<>();
        public final List<Axis> shaftAxes = new ArrayList<>();
        public final List<GearboxPlace> gearboxes = new ArrayList<>();
        public final List<CogPlace> cogs = new ArrayList<>();
    }

    public static final class ResultOutcome {
        public final Result result;
        public final Plan plan;
        ResultOutcome(Result result, Plan plan) {
            this.result = result;
            this.plan = plan;
        }
    }

    /** 判定一个方块是否为可接轴端的机械动力方块(IRotate + KineticBlockEntity)。 */
    static boolean isKineticEnd(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!world.isLoaded(pos) || !(state.getBlock() instanceof IRotate))
            return false;
        return world.getBlockEntity(pos) instanceof KineticBlockEntity;
    }

    /** 单次连接最多允许铺设的方块总数(轴+齿轮箱+大齿轮), 服务端安全上限。 */
    public static final int MAX_TOTAL_BLOCKS = 256;

    /** 计算铺设计划(只读, 不改世界)。corners 为按序点击的拐点(可为空=直线直达); cornerType=拐角用齿轮箱还是大齿轮。 */
    public static ResultOutcome plan(Level world, BlockPos start, List<BlockPos> corners, BlockPos end,
                                     ConnectCorner cornerType) {
        if (!world.isLoaded(start) || !world.isLoaded(end))
            return new ResultOutcome(Result.UNLOADED, null);
        for (BlockPos c : corners)
            if (!world.isLoaded(c))
                return new ResultOutcome(Result.UNLOADED, null);

        if (start.equals(end))
            return new ResultOutcome(Result.SAME_POS, null);

        // 节点链: start, c1..ck, end
        List<BlockPos> chain = new ArrayList<>();
        chain.add(start);
        chain.addAll(corners);
        chain.add(end);

        // 端点必须为机械动力方块
        if (!isKineticEnd(world, start) || !isKineticEnd(world, end))
            return new ResultOutcome(Result.NOT_KINETIC, null);

        // 逐边路由成直线轴段列表
        List<Leg> legs = new ArrayList<>();
        for (int i = 0; i < chain.size() - 1; i++) {
            boolean first = i == 0;
            Leg[] edge = routeEdge(world, chain.get(i), chain.get(i + 1), first,
                start, world.getBlockState(start));
            if (edge == null)
                return new ResultOutcome(edgeResult(world, chain.get(i), chain.get(i + 1)), null);
            for (Leg l : edge)
                legs.add(l);
        }
        if (legs.isEmpty())
            return new ResultOutcome(Result.AXIS_MISMATCH, null);

        // 端点轴口校验
        Leg firstLeg = legs.get(0);
        BlockState startState = world.getBlockState(start);
        if (!portOpen(world, start, startState, firstLeg.axis,
            directionBetween(start, firstLeg.to)))
            return new ResultOutcome(Result.NOT_KINETIC, null);
        Leg lastLeg = legs.get(legs.size() - 1);
        BlockState endState = world.getBlockState(end);
        if (!portOpen(world, end, endState, lastLeg.axis,
            directionBetween(lastLeg.from, end).getOpposite()))
            return new ResultOutcome(Result.NOT_KINETIC, null);

        // 【已移除】原先的"双源方向冲突"校验: 穿过齿轮箱/拐弯本就会反转转向, 会误伤正常接入, 改由 Create 运行时合并。

        // 每段轴段的中间格(先算出来, 大齿轮拐角会从两端"吃掉"格)
        List<List<BlockPos>> legCells = new ArrayList<>();
        for (Leg leg : legs) {
            List<BlockPos> cells = interiorCells(leg.from, leg.to, leg.axis);
            if (cells == null)
                return new ResultOutcome(Result.TOO_LONG, null);
            legCells.add(cells);
        }

        Plan plan = new Plan();

        // 每个内部节点(被点拐点 / 自动角点): 按拐角类型放齿轮箱 或 两个大齿轮
        for (int i = 0; i < legs.size() - 1; i++) {
            Leg cur = legs.get(i);
            Leg next = legs.get(i + 1);
            BlockPos jp = cur.to;
            boolean perpendicular = cur.axis != next.axis;
            if (cornerType == ConnectCorner.LARGE_COG && perpendicular) {
                // 大齿轮换轴: 两块斜对角(各自落在相邻两段轴上), 等速换向。
                // L1 = 入段末格(轴=入轴), L2 = 出段首格(轴=出轴); 节点本身留空。
                List<BlockPos> c1 = legCells.get(i);
                List<BlockPos> c2 = legCells.get(i + 1);
                if (c1.isEmpty() || c2.isEmpty())
                    return new ResultOutcome(Result.CORNER_NO_ROOM, null);
                BlockPos l1 = c1.remove(c1.size() - 1);
                BlockPos l2 = c2.remove(0);
                // 审计发现 #7: 这两格是从 legCells 里"吃掉"的, 不会进入下面的 occupied 校验循环,
                // 所以必须在这里单独校验, 否则大齿轮会直接覆盖掉角上的既有方块。
                if (occupied(world, l1) || occupied(world, l2))
                    return new ResultOutcome(Result.PATH_BLOCKED, null);
                plan.cogs.add(new CogPlace(l1, cur.axis));
                plan.cogs.add(new CogPlace(l2, next.axis));
            } else {
                // 齿轮箱节点: 允许把**可替换方块/已有轴**换成齿轮箱, 但**不能无条件覆盖**别的东西
                // (审计发现 #7: 原来这里完全不校验, 角点落在箱子等机器上会直接把方块抹掉)
                if (occupied(world, jp))
                    return new ResultOutcome(Result.PATH_BLOCKED, null);
                Axis gax = junctionAxis(cur.axis, next.axis);
                plan.gearboxes.add(new GearboxPlace(jp, gax, gax != Axis.Y));
            }
        }

        // 各轴段剩余中间格铺轴
        for (int i = 0; i < legCells.size(); i++) {
            Axis ax = legs.get(i).axis;
            for (BlockPos p : legCells.get(i)) {
                if (occupied(world, p))
                    return new ResultOutcome(Result.PATH_BLOCKED, null);
                plan.shaftPositions.add(p);
                plan.shaftAxes.add(ax);
            }
        }

        return new ResultOutcome(Result.SUCCESS, plan);
    }

    private static Result edgeResult(Level world, BlockPos a, BlockPos b) {
        int n = differingCount(a, b);
        if (n == 0)
            return Result.SAME_POS;
        if (n >= 3)
            return Result.NON_PLANAR;
        // 1 或 2 维的 routeEdge 返回 null, 只可能是首段起点轴口不被满足 → 报"无法接轴"
        return Result.NOT_KINETIC;
    }

    /** 路由一条边: 返回 1(直线)或 2(一次拐弯)个轴段; 无法路由返回 null。 */
    private static Leg[] routeEdge(Level world, BlockPos a, BlockPos b, boolean first,
                                   BlockPos start, BlockState startState) {
        List<Axis> ds = differingAxes(a, b);
        int n = ds.size();
        if (n == 0 || n >= 3)
            return null;

        if (n == 1) {
            Axis ax = ds.get(0);
            return new Leg[] { new Leg(ax, a, b) };
        }

        // n == 2 (同一平面): 两种 L 取向, 选一种
        Axis p = ds.get(0);
        Axis q = ds.get(1);
        // 拐点要"先沿 a 的轴"(首段)再转 b 的轴: 即拐点保留 a 的其它坐标、把首段轴坐标换成 b 的
        BlockPos cornerP = corner(a, b, p); // 首段沿 p: a 的 p 坐标换成 b 的
        BlockPos cornerQ = corner(a, b, q); // 首段沿 q: a 的 q 坐标换成 b 的

        boolean pOk = !first || portOpen(world, start, startState, p,
            directionBetween(start, cornerP));
        boolean qOk = !first || portOpen(world, start, startState, q,
            directionBetween(start, cornerQ));

        boolean useP;
        if (pOk != qOk) {
            // 只有一种取向可用
            useP = pOk;
        } else if (!pOk) {
            return null; // 两种都不可用
        } else {
            // 两种都可用: 优先避免拐点"紧贴端点"(尤其紧贴终点方块), 再兼顾起点旋转轴
            int scoreP = orientationScore(a, b, cornerP);
            int scoreQ = orientationScore(a, b, cornerQ);
            if (scoreP != scoreQ) {
                useP = scoreP > scoreQ;
            } else if (first) {
                Axis rax = ((IRotate) startState.getBlock()).getRotationAxis(startState);
                useP = p == rax ? true : (q == rax ? false : true);
            } else {
                useP = true; // 取第一个差异轴(确定)
            }
        }

        Axis firstAxis = useP ? p : q;
        Axis secondAxis = useP ? q : p;
        BlockPos corner = useP ? cornerP : cornerQ;
        return new Leg[] { new Leg(firstAxis, a, corner), new Leg(secondAxis, corner, b) };
    }

    /**
     * L 取向打分: 避免自动拐点紧贴端点(尤其紧贴终点方块)。
     * 第二段(拐点→b)越长越优先(不在终点旁拐), 其次第一段(不在起点旁拐)。
     */
    private static int orientationScore(BlockPos a, BlockPos b, BlockPos corner) {
        int score = 0;
        if (manhattan(b, corner) >= 2)
            score += 2;
        if (manhattan(a, corner) >= 2)
            score += 1;
        return score;
    }

    private static int manhattan(BlockPos x, BlockPos y) {
        return Math.abs(x.getX() - y.getX()) + Math.abs(x.getY() - y.getY()) + Math.abs(x.getZ() - y.getZ());
    }

    /** 把 posA 中 atAxis 的坐标替换成 posB 的值, 其余保持 posA。 */
    private static BlockPos corner(BlockPos posA, BlockPos posB, Axis atAxis) {
        int x = atAxis == Axis.X ? posB.getX() : posA.getX();
        int y = atAxis == Axis.Y ? posB.getY() : posA.getY();
        int z = atAxis == Axis.Z ? posB.getZ() : posA.getZ();
        return new BlockPos(x, y, z);
    }

    /** 轴口是否朝向 dir 方向打开(只有端点锚/起点才需要; 内部齿轮箱不查)。 */
    private static boolean portOpen(Level world, BlockPos pos, BlockState state, Axis axis, Direction dir) {
        if (!(state.getBlock() instanceof IRotate rot))
            return false;
        return rot.hasShaftTowards(world, pos, state, dir);
    }

    /** 两轴段在共同节点处的齿轮箱轴向。 */
    private static Axis junctionAxis(Axis a1, Axis a2) {
        if (a1 != a2)
            return thirdAxis(a1, a2);
        // 共线(直通): 取任一垂直于 a1 的轴, 使齿轮箱可通过
        return a1 == Axis.Y ? Axis.X : Axis.Y;
    }

    private static Axis thirdAxis(Axis a1, Axis a2) {
        for (Axis ax : new Axis[] { Axis.X, Axis.Y, Axis.Z })
            if (ax != a1 && ax != a2)
                return ax;
        return Axis.Y;
    }

    private static List<Axis> differingAxes(BlockPos a, BlockPos b) {
        List<Axis> list = new ArrayList<>();
        if (a.getX() != b.getX())
            list.add(Axis.X);
        if (a.getY() != b.getY())
            list.add(Axis.Y);
        if (a.getZ() != b.getZ())
            list.add(Axis.Z);
        return list;
    }

    private static int differingCount(BlockPos a, BlockPos b) {
        return differingAxes(a, b).size();
    }

    private static Direction directionBetween(BlockPos a, BlockPos b) {
        Axis axis = axisBetween(a, b);
        if (axis == null)
            return Direction.UP;
        int delta = axis.choose(b.getX(), b.getY(), b.getZ()) - axis.choose(a.getX(), a.getY(), a.getZ());
        return delta >= 0 ? Direction.get(AxisDirection.POSITIVE, axis)
            : Direction.get(AxisDirection.NEGATIVE, axis);
    }

    private static Axis axisBetween(BlockPos a, BlockPos b) {
        List<Axis> ds = differingAxes(a, b);
        return ds.size() == 1 ? ds.get(0) : null;
    }

    /** from→to(不含两端)沿 axis 的中间格; 任一格超长无法铺完时返回 null。 */
    private static List<BlockPos> interiorCells(BlockPos from, BlockPos to, Axis axis) {
        List<BlockPos> out = new ArrayList<>();
        Direction dir = directionBetween(from, to);
        int guard = 0;
        for (BlockPos p = from.relative(dir); !p.equals(to); p = p.relative(dir)) {
            if (++guard > MAX_LEG)
                return null;
            out.add(p.immutable());
        }
        return out;
    }

    private static boolean occupied(Level world, BlockPos pos) {
        BlockState existing = world.getBlockState(pos);
        if (existing.canBeReplaced())
            return false;
        if (existing.getBlock() instanceof AbstractSimpleShaftBlock
            && existing.hasProperty(BlockStateProperties.AXIS))
            return false; // 已有同型轴可复用
        return true;
    }

    /** 真正执行连接: 校验材料足量后落块并扣料(creative 跳过扣料)。 */
    public static Result connect(ServerLevel world, Player player, BlockPos start, List<BlockPos> corners,
                                 BlockPos end, ConnectCorner cornerType) {
        ResultOutcome oc = plan(world, start, corners, end, cornerType);
        if (oc.result != Result.SUCCESS)
            return oc.result;
        Plan plan = oc.plan;

        // 服务端安全: 单次请求能铺设的方块总数上限(挡住"拐点多 × 每段最长 64"叠出的超大工程把服务端卡住)
        int totalBlocks = plan.shaftPositions.size() + plan.gearboxes.size() + plan.cogs.size();
        if (totalBlocks > MAX_TOTAL_BLOCKS)
            return Result.TOO_LONG;

        Item shaftItem = resolveShaftItem(player);
        int shaftCount = plan.shaftPositions.size();
        int gearboxCount = plan.gearboxes.size();
        int verticalCount = 0;
        for (GearboxPlace g : plan.gearboxes)
            if (g.vertical)
                verticalCount++;
        int horizontalCount = gearboxCount - verticalCount;
        int cogCount = plan.cogs.size();

        Item gearboxItem = AllBlocks.GEARBOX.asItem();
        Item verticalItem = AllItems.VERTICAL_GEARBOX.get();
        Item cogItem = AllBlocks.LARGE_COGWHEEL.asItem();

        if (!player.isCreative()) {
            if (countItem(player, shaftItem) < shaftCount)
                return Result.MATERIALS;
            if (horizontalCount > 0 && countItem(player, gearboxItem) < horizontalCount)
                return Result.MATERIALS;
            if (verticalCount > 0 && countItem(player, verticalItem) < verticalCount)
                return Result.MATERIALS;
            if (cogCount > 0 && countItem(player, cogItem) < cogCount)
                return Result.MATERIALS;
        }

        // 放置
        BlockState shaftBase = shaftBlockState(shaftItem);
        for (int i = 0; i < plan.shaftPositions.size(); i++) {
            BlockPos p = plan.shaftPositions.get(i);
            BlockState st = shaftBase.hasProperty(BlockStateProperties.AXIS)
                ? shaftBase.setValue(BlockStateProperties.AXIS, plan.shaftAxes.get(i))
                : shaftBase;
            KineticBlockEntity.switchToBlockState(world, p, st);
        }
        for (GearboxPlace g : plan.gearboxes)
            KineticBlockEntity.switchToBlockState(world, g.pos,
                AllBlocks.GEARBOX.getDefaultState()
                    .setValue(BlockStateProperties.AXIS, g.axis == null ? Axis.Y : g.axis));
        for (CogPlace c : plan.cogs)
            KineticBlockEntity.switchToBlockState(world, c.pos,
                AllBlocks.LARGE_COGWHEEL.getDefaultState()
                    .setValue(BlockStateProperties.AXIS, c.axis));

        world.playSound(null, BlockPos.containing(
            net.createmod.catnip.math.VecHelper.getCenterOf(start.offset(end)).scale(.5f)),
            SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5F, 1F);

        // 扣料(creative 跳过)
        if (!player.isCreative()) {
            if (shaftCount > 0)
                consumeItem(player, shaftItem, shaftCount);
            if (horizontalCount > 0)
                consumeItem(player, gearboxItem, horizontalCount);
            if (verticalCount > 0)
                consumeItem(player, verticalItem, verticalCount);
            if (cogCount > 0)
                consumeItem(player, cogItem, cogCount);
        }

        return Result.SUCCESS;
    }

    private static BlockState shaftBlockState(Item shaftItem) {
        if (shaftItem instanceof BlockItem bi) {
            BlockState s = bi.getBlock().defaultBlockState();
            if (s.hasProperty(BlockStateProperties.AXIS))
                return s;
        }
        return AllBlocks.SHAFT.getDefaultState();
    }

    private static Item resolveShaftItem(Player player) {
        Item off = player.getOffhandItem().getItem();
        if (off instanceof BlockItem bi && bi.getBlock() instanceof AbstractSimpleShaftBlock)
            return off;
        return AllBlocks.SHAFT.asItem();
    }

    private static int countItem(Player player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items)
            if (stack.getItem() == item)
                count += stack.getCount();
        ItemStack off = player.getOffhandItem();
        if (off.getItem() == item)
            count += off.getCount();
        return count;
    }

    private static void consumeItem(Player player, Item item, int count) {
        int remain = count;
        for (int i = player.getInventory().items.size() - 1; i >= 0 && remain > 0; i--) {
            ItemStack stack = player.getInventory().items.get(i);
            if (stack.getItem() != item)
                continue;
            int take = Math.min(remain, stack.getCount());
            stack.shrink(take);
            remain -= take;
        }
        if (remain > 0) {
            ItemStack off = player.getOffhandItem();
            if (off.getItem() == item) {
                int take = Math.min(remain, off.getCount());
                off.shrink(take);
                remain -= take;
            }
        }
    }
}
