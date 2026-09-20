package com.nonono.createbetterwrench.connect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nonono.createbetterwrench.BetterWrenchMod;
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
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

/**
 * 「连接」模式的服务端核心 —— 拐点数量不限, 每段边按几何自动路由。
 *
 * <p>交互(用户 2026-09-07 重定义):
 * 起点 S(机械动力方块) → 若干普通方块拐点 c1..ck(最多 32 个, 见 ConnectPayload.MAX_CORNERS) → 终点 E(机械动力方块)。</p>
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
 * 放置前按 Item 聚合校验材料足量, 不足整拒不部分放; 扣料只按逐格校验通过的实际落块数。</p>
 */
public final class ConnectLogic {

    private static final int MAX_LEG = 64;

    private ConnectLogic() {
    }

    public enum Result {
        SUCCESS,
        UNLOADED,
        NOT_KINETIC,
        NON_PLANAR,
        SAME_POS,
        PATH_BLOCKED,
        PROTECTED,
        CORNER_NO_ROOM,
        MATERIALS,
        TOO_LONG
    }

    /** 计划格登记结果(见 {@link #registerCell})。 */
    private static final int CELL_ADDED = 1;
    private static final int CELL_DUPLICATE = 0;
    private static final int CELL_CONFLICT = -1;

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
                start, end, world.getBlockState(start));
            if (edge == null)
                return new ResultOutcome(edgeResult(world, chain.get(i), chain.get(i + 1)), null);
            for (Leg l : edge)
                legs.add(l);
        }

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
            // 审计 A-5: 中间格也必须已加载 —— Level#getBlockState 会阻塞式加载/生成区块,
            // 所以绝不能让它走到后面的 occupied()。节点级校验(上方 isLoaded)挡不住路径穿的未加载区。
            for (BlockPos p : cells)
                if (!world.hasChunkAt(p))
                    return new ResultOutcome(Result.UNLOADED, null);
            legCells.add(cells);
        }

        Plan plan = new Plan();
        // 审计 A-4: 记录"已规划坐标 → 该格要放什么", 用于去重/冲突检测, 并禁止任何计划格落到起点/终点上
        Map<BlockPos, String> planned = new LinkedHashMap<>();

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
                int r1 = registerCell(planned, l1, "COG:" + cur.axis, start, end);
                int r2 = registerCell(planned, l2, "COG:" + next.axis, start, end);
                if (r1 == CELL_CONFLICT || r2 == CELL_CONFLICT)
                    return new ResultOutcome(Result.PATH_BLOCKED, null);
                if (r1 != CELL_DUPLICATE)
                    plan.cogs.add(new CogPlace(l1, cur.axis));
                if (r2 != CELL_DUPLICATE)
                    plan.cogs.add(new CogPlace(l2, next.axis));
            } else {
                // 齿轮箱节点: 允许把**可替换方块/已有轴**换成齿轮箱, 但**不能无条件覆盖**别的东西
                // (审计发现 #7: 原来这里完全不校验, 角点落在箱子等机器上会直接把方块抹掉)
                if (!world.hasChunkAt(jp))
                    return new ResultOutcome(Result.UNLOADED, null);
                if (occupied(world, jp))
                    return new ResultOutcome(Result.PATH_BLOCKED, null);
                Axis gax = junctionAxis(cur.axis, next.axis);
                int r = registerCell(planned, jp, "GEARBOX:" + gax, start, end);
                if (r == CELL_CONFLICT)
                    return new ResultOutcome(Result.PATH_BLOCKED, null);
                if (r != CELL_DUPLICATE)
                    plan.gearboxes.add(new GearboxPlace(jp, gax, gax != Axis.Y));
            }
        }

        // 各轴段剩余中间格铺轴
        for (int i = 0; i < legCells.size(); i++) {
            Axis ax = legs.get(i).axis;
            for (BlockPos p : legCells.get(i)) {
                if (occupied(world, p))
                    return new ResultOutcome(Result.PATH_BLOCKED, null);
                int r = registerCell(planned, p, "SHAFT:" + ax, start, end);
                if (r == CELL_CONFLICT)
                    return new ResultOutcome(Result.PATH_BLOCKED, null);
                if (r != CELL_DUPLICATE) {
                    plan.shaftPositions.add(p);
                    plan.shaftAxes.add(ax);
                }
            }
        }

        return new ResultOutcome(Result.SUCCESS, plan);
    }

    /**
     * 登记一个计划格(审计 A-4)。
     *
     * <p>同一坐标、同一内容 → {@link #CELL_DUPLICATE}(调用方跳过: 不重复计材料、不重复放置);
     * 同一坐标、不同内容(例如先排竖直齿轮箱后又排水平齿轮箱) → {@link #CELL_CONFLICT};
     * 坐标等于起点或终点 → 同样判冲突(绝不允许把起点/终点方块换成齿轮箱或轴)。</p>
     */
    private static int registerCell(Map<BlockPos, String> planned, BlockPos pos, String kind,
                                    BlockPos start, BlockPos end) {
        if (pos.equals(start) || pos.equals(end))
            return CELL_CONFLICT;
        String prev = planned.get(pos);
        if (prev == null) {
            planned.put(pos.immutable(), kind);
            return CELL_ADDED;
        }
        return prev.equals(kind) ? CELL_DUPLICATE : CELL_CONFLICT;
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
                                   BlockPos start, BlockPos end, BlockState startState) {
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
            // 两种都可用: 先避开"自动拐点正好落在起点/终点上"的取向
            // (审计 A-4: 那样的拐点会变成齿轮箱, 把起点/终点方块覆盖掉), 再按分数与起点旋转轴挑
            boolean onEndP = cornerP.equals(start) || cornerP.equals(end);
            boolean onEndQ = cornerQ.equals(start) || cornerQ.equals(end);
            if (onEndP != onEndQ) {
                useP = !onEndP;
            } else {
                // 优先避免拐点"紧贴端点"(尤其紧贴终点方块), 再兼顾起点旋转轴
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

        // 审计 A-3: 每个将要落块的坐标都要先过原版交互权限(服务端实现里含出生点保护与世界边界)。
        // 单独给一个 PROTECTED 结果码, 免得玩家看到"路径被方块阻挡"却看不出是保护规则在拦。
        for (BlockPos p : plan.shaftPositions)
            if (!world.mayInteract(player, p))
                return Result.PROTECTED;
        for (GearboxPlace g : plan.gearboxes)
            if (!world.mayInteract(player, g.pos))
                return Result.PROTECTED;
        for (CogPlace c : plan.cogs)
            if (!world.mayInteract(player, c.pos))
                return Result.PROTECTED;

        Item shaftItem = resolveShaftItem(player);
        Item gearboxItem = AllBlocks.GEARBOX.asItem();
        Item verticalItem = AllItems.VERTICAL_GEARBOX.get();
        Item cogItem = AllBlocks.LARGE_COGWHEEL.asItem();

        // 审计 A-1: 材料需求先按 Item 聚合再一次性校验 —— 副手持"轴变体"(AbstractSimpleShaftBlock,
        // 含 create:cogwheel / create:large_cogwheel)时 shaftItem 可能与 cogItem 是同一个 Item,
        // 分头校验会造成"放 7 块只扣 5 个"的凭空造物。
        if (!hasMaterials(player, plan))
            return Result.MATERIALS;

        // 放置, 并逐格确认真的落上了(审计 A-16: switchToBlockState→setBlock 可能静默失败,
        // 所以扣料只按"校验通过的实际落块数", 不能照 plan 计数扣)。
        //
        // 每格还会**补发原版的 EntityPlaceEvent**(审计 A-3 残留修复): 只监听该事件的领地/保护插件
        // 从此也能拦住; 一旦被拦 => **逆序整体还原 + 拒连**(世上不留半截传动结构, 也不扣料)。
        BlockState shaftBase = shaftBlockState(shaftItem);
        Map<Item, Integer> placedItems = new LinkedHashMap<>();
        List<BlockSnapshot> undo = new ArrayList<>();
        for (int i = 0; i < plan.shaftPositions.size(); i++) {
            BlockPos p = plan.shaftPositions.get(i);
            BlockState st = shaftBase.hasProperty(BlockStateProperties.AXIS)
                ? shaftBase.setValue(BlockStateProperties.AXIS, plan.shaftAxes.get(i))
                : shaftBase;
            PlaceOutcome outcome = placeWithEvent(world, p, st, player, undo);
            if (outcome == PlaceOutcome.DENIED) {
                revertAll(undo);
                return Result.PROTECTED;
            }
            if (outcome == PlaceOutcome.OK)
                addDemand(placedItems, shaftItem, 1);
        }
        for (GearboxPlace g : plan.gearboxes) {
            BlockState st = AllBlocks.GEARBOX.getDefaultState()
                .setValue(BlockStateProperties.AXIS, g.axis);
            PlaceOutcome outcome = placeWithEvent(world, g.pos, st, player, undo);
            if (outcome == PlaceOutcome.DENIED) {
                revertAll(undo);
                return Result.PROTECTED;
            }
            if (outcome == PlaceOutcome.OK)
                addDemand(placedItems, g.vertical ? verticalItem : gearboxItem, 1);
        }
        for (CogPlace c : plan.cogs) {
            BlockState st = AllBlocks.LARGE_COGWHEEL.getDefaultState()
                .setValue(BlockStateProperties.AXIS, c.axis);
            PlaceOutcome outcome = placeWithEvent(world, c.pos, st, player, undo);
            if (outcome == PlaceOutcome.DENIED) {
                revertAll(undo);
                return Result.PROTECTED;
            }
            if (outcome == PlaceOutcome.OK)
                addDemand(placedItems, cogItem, 1);
        }

        world.playSound(null, BlockPos.containing(
            net.createmod.catnip.math.VecHelper.getCenterOf(start.offset(end)).scale(.5f)),
            SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5F, 1F);

        // 扣料(creative 跳过): 只按真正落下的方块数扣
        if (!player.isCreative()) {
            for (Map.Entry<Item, Integer> e : placedItems.entrySet())
                if (!consumeItem(player, e.getKey(), e.getValue()))
                    BetterWrenchMod.LOGGER.warn("connect: 扣料不足 {} x{} (玩家 {})",
                        e.getKey(), e.getValue(), player.getName().getString());
        }

        return Result.SUCCESS;
    }

    /** 单格放置的结果(见 {@link #placeWithEvent})。 */
    private enum PlaceOutcome {
        /** 落上了且没被保护事件拦下 —— 可计入扣料。 */
        OK,
        /** 方块没落上(极端情形, 例如 debug 世界) —— 不计料, 但**不**中止整次操作。 */
        FAILED,
        /** 被 {@code EntityPlaceEvent} 取消 —— 调用方必须整体还原并拒连。 */
        DENIED
    }

    /**
     * 落一块方块, 并**补发原版的「实体放置方块」事件**(审计 A-3 残留修复)。
     *
     * <p><b>为什么必须补</b>: 本模组是用 {@code KineticBlockEntity.switchToBlockState} 直接改方块的,
     * 原版那条 {@code BlockEvent.EntityPlaceEvent} 不会发出 ⇒ **只监听该事件的领地/保护插件拦不住**
     * (出生点保护与世界边界已由 {@code world.mayInteract} 覆盖, 见 {@link #connect} 开头)。</p>
     *
     * <p><b>顺序照 {@code CommonHooks} 的官方做法</b>: 先落块 → 再发事件(这样
     * {@code snapshot.getCurrentState()} 已经是新方块, 插件读到的"placedBlock"才是要放的那一块) → 被取消再还原。
     * 放置朝向取 {@code Direction.UP} —— 我们没有"点击面", 把"下方那一格"当作 placedAgainst 是最自然的近似。</p>
     *
     * <p>放置成功时把 {@code snapshot}(放置前的状态)记进 {@code undo}, 供被拒时逆序整体还原。</p>
     */
    private static PlaceOutcome placeWithEvent(ServerLevel world, BlockPos pos, BlockState st,
                                               Player player, List<BlockSnapshot> undo) {
        BlockSnapshot snapshot = BlockSnapshot.create(world.dimension(), world, pos);
        KineticBlockEntity.switchToBlockState(world, pos, st);
        if (EventHooks.onBlockPlace(player, snapshot, Direction.UP)) {
            snapshot.restore(snapshot.getFlags() | Block.UPDATE_CLIENTS);
            BetterWrenchMod.LOGGER.warn("connect: 放置被 EntityPlaceEvent 取消 {}", pos);
            return PlaceOutcome.DENIED;
        }
        if (!placed(world, pos, st)) {
            BetterWrenchMod.LOGGER.warn("connect: 方块未落下 {} (期望 {})", pos, st);
            return PlaceOutcome.FAILED;
        }
        undo.add(snapshot);
        return PlaceOutcome.OK;
    }

    /** 把本次已放置的方块按**逆序**还原(被保护插件拦下后, 世上不留半截传动结构)。 */
    private static void revertAll(List<BlockSnapshot> undo) {
        for (int i = undo.size() - 1; i >= 0; i--) {
            BlockSnapshot snapshot = undo.get(i);
            snapshot.restore(snapshot.getFlags() | Block.UPDATE_CLIENTS);
        }
    }

    /** 落块是否真的生效(Level#setBlock 在 debug 世界等情形会静默返回 false)。 */
    private static boolean placed(Level world, BlockPos pos, BlockState target) {
        BlockState now = world.getBlockState(pos);
        if (now.getBlock() != target.getBlock())
            return false;
        if (target.hasProperty(BlockStateProperties.AXIS) && now.hasProperty(BlockStateProperties.AXIS))
            return now.getValue(BlockStateProperties.AXIS) == target.getValue(BlockStateProperties.AXIS);
        return true;
    }

    /** 本单按 Item 聚合的需求量(同一 Item 的多项用途必须累加, 见审计 A-1)。 */
    private static Map<Item, Integer> demandOf(Plan plan, Item shaftItem) {
        Map<Item, Integer> demand = new LinkedHashMap<>();
        addDemand(demand, shaftItem, plan.shaftPositions.size());
        addDemand(demand, AllBlocks.GEARBOX.asItem(), gearboxCount(plan.gearboxes, false));
        addDemand(demand, AllItems.VERTICAL_GEARBOX.get(), gearboxCount(plan.gearboxes, true));
        addDemand(demand, AllBlocks.LARGE_COGWHEEL.asItem(), plan.cogs.size());
        return demand;
    }

    private static int gearboxCount(List<GearboxPlace> gearboxes, boolean vertical) {
        int count = 0;
        for (GearboxPlace g : gearboxes)
            if (g.vertical == vertical)
                count++;
        return count;
    }

    private static void addDemand(Map<Item, Integer> demand, Item item, int count) {
        if (count > 0)
            demand.merge(item, count, Integer::sum);
    }

    /** 计划所需材料是否齐备(客户端幽灵预览与服务端扣料共用同一口径)。 */
    public static boolean hasMaterials(Player player, Plan plan) {
        if (player.isCreative())
            return true;
        for (Map.Entry<Item, Integer> e : demandOf(plan, resolveShaftItem(player)).entrySet())
            if (countItem(player, e.getKey()) < e.getValue())
                return false;
        return true;
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

    /** 从背包(含副手)扣除指定物品; 扣不满返回 false(调用方须记录日志, 不静默吞掉)。 */
    private static boolean consumeItem(Player player, Item item, int count) {
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
        return remain <= 0;
    }
}
