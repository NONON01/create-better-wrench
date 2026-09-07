package com.example.createbetterwrench.connect;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

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
 * 「连接」模式的服务端核心: 判断 A→(拐点)→B 两段直线能否铺传动结构, 并在玩家背包足够时
 * 铺设(直线段铺轴, 拐点放齿轮箱)并精确扣料。
 *
 * <p>规则(用户确认 2026-09-07):
 * <ul>
 *   <li>A、B 必须是可接轴端的机械动力方块(IRotate + KineticBlockEntity, 且面向路径的面 hasShaftTowards);</li>
 *   <li>拐点为"普通方块"(非机械动力方块), 此处放齿轮箱, 拐弯必须成 90° 直角;</li>
 *   <li>只允许直线段 + 最多一次拐弯; 两段各自严格轴线对齐, 不齐则拒连;</li>
 *   <li>默认材料: 轴 create:shaft + 齿轮箱 create:gearbox; 副手若持传动杆(轴变体)则优先用副手;</li>
 *   <li>放置前先校验背包足量, 不足则整体拒绝、不放置、不扣料(不做部分放块);</li>
 *   <li>双源(两端已各自独立供力且方向冲突)会触发 Create 炸块, 连前预校验后拒连。</li>
 * </ul>
 */
public final class ConnectLogic {

    /** 单段轴的最大格数(安全上限, 防止极端长路径)。 */
    private static final int MAX_LEG = 64;

    private ConnectLogic() {
    }

    /** 判定结果(供服务端提示/客户端预览用)。 */
    public enum Result {
        SUCCESS,
        UNLOADED,
        NOT_KINETIC,
        AXIS_MISMATCH,
        BAD_TURN,
        PATH_BLOCKED,
        CONFLICTING_SOURCE,
        MATERIALS,
        TOO_LONG
    }

    /** 落块计划: 若干轴格 + 可能的一个齿轮箱格。 */
    public static final class Plan {
        public final List<BlockPos> shafts = new ArrayList<>();
        public final List<Axis> shaftAxes = new ArrayList<>();
        public final BlockPos gearboxPos;   // null = 纯直线, 无齿轮箱
        public final Axis gearboxAxis;      // 仅 gearboxPos != null 时有效
        public final boolean verticalGearbox;

        Plan(List<BlockPos> shafts, List<Axis> shaftAxes, BlockPos gearboxPos, Axis gearboxAxis,
             boolean verticalGearbox) {
            this.shafts.addAll(shafts);
            this.shaftAxes.addAll(shaftAxes);
            this.gearboxPos = gearboxPos;
            this.gearboxAxis = gearboxAxis;
            this.verticalGearbox = verticalGearbox;
        }
    }

    /** 计划 + 判定结果。result != SUCCESS 时 plan 可能为空。 */
    public static final class ResultOutcome {
        public final Result result;
        public final Plan plan;

        ResultOutcome(Result result, Plan plan) {
            this.result = result;
            this.plan = plan;
        }
    }

    /** 玩家是否为"可接轴端"的机械动力方块(A/B 端点判定)。 */
    public static boolean isKineticEnd(Level world, BlockPos pos, Direction toward) {
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (!world.isLoaded(pos) || !(block instanceof IRotate rot))
            return false;
        if (!(world.getBlockEntity(pos) instanceof KineticBlockEntity))
            return false;
        // 端点朝路径方向的这一面必须能接轴(真正的轴口/齿轮箱口才可连)
        return rot.hasShaftTowards(world, pos, state, toward);
    }

    /**
     * 计算铺设计划(只读, 不改世界): 校验轴线对齐/拐弯/占位/双源, 输出要落块的轴格与齿轮箱格。
     * 客户端可用它做幽灵预览, 服务端用它做真正的放置。
     */
    public static ResultOutcome plan(Level world, BlockPos start, @Nullable BlockPos corner, BlockPos end) {
        if (!world.isLoaded(start) || !world.isLoaded(end))
            return new ResultOutcome(Result.UNLOADED, null);
        if (corner != null && !world.isLoaded(corner))
            return new ResultOutcome(Result.UNLOADED, null);

        boolean hasCorner = corner != null;

        // --- 解析两段轴的轴向与方向 ---
        Axis leg1Axis;
        Direction d1; // start -> (corner|end)
        if (hasCorner) {
            leg1Axis = axisBetween(start, corner);
            if (leg1Axis == null)
                return new ResultOutcome(Result.AXIS_MISMATCH, null);
            d1 = directionBetween(start, corner);
        } else {
            leg1Axis = axisBetween(start, end);
            if (leg1Axis == null)
                return new ResultOutcome(Result.AXIS_MISMATCH, null);
            d1 = directionBetween(start, end);
        }

        Axis leg2Axis = null;
        Direction d2 = null; // corner -> end
        if (hasCorner) {
            leg2Axis = axisBetween(corner, end);
            if (leg2Axis == null)
                return new ResultOutcome(Result.AXIS_MISMATCH, null);
            d2 = directionBetween(corner, end);
            // 拐弯必须是 90° 直角(两段垂直), 否则不是"一次拐弯"
            if (leg1Axis == leg2Axis)
                return new ResultOutcome(Result.BAD_TURN, null);
        }

        // --- 端点轴口校验 ---
        if (!isKineticEnd(world, start, d1))
            return new ResultOutcome(Result.NOT_KINETIC, null);
        Direction endIn = hasCorner ? d2.getOpposite() : d1.getOpposite();
        if (!isKineticEnd(world, end, endIn))
            return new ResultOutcome(Result.NOT_KINETIC, null);

        // --- 双源安全校验: 两端若已各自独立供力且方向冲突, Create 会在连入时炸块 ---
        if (conflicts(world, start, end))
            return new ResultOutcome(Result.CONFLICTING_SOURCE, null);

        // --- 计算全部落块位置 ---
        List<BlockPos> shaftPos = new ArrayList<>();
        List<Axis> shaftAx = new ArrayList<>();

        BlockPos c1 = hasCorner ? corner : end;
        if (!collectShafts(world, start, c1, leg1Axis, shaftPos, shaftAx))
            return new ResultOutcome(Result.PATH_BLOCKED, null);

        BlockPos gearboxPos = null;
        Axis gearboxAxis = null;
        boolean verticalGearbox = false;

        if (hasCorner) {
            // 齿轮箱轴向: 水平↔水平 => Y; 竖直↔水平 => 垂直于水平腿的那条水平轴
            if (leg1Axis.isVertical() || leg2Axis.isVertical()) {
                Axis horizontalLeg = leg1Axis.isVertical() ? leg2Axis : leg1Axis;
                gearboxAxis = horizontalLeg == Axis.X ? Axis.Z : Axis.X;
                verticalGearbox = true;
            } else {
                gearboxAxis = Axis.Y;
                verticalGearbox = false;
            }
            gearboxPos = corner;

            List<BlockPos> second = new ArrayList<>();
            List<Axis> secondAx = new ArrayList<>();
            if (!collectShafts(world, corner, end, leg2Axis, second, secondAx))
                return new ResultOutcome(Result.PATH_BLOCKED, null);
            shaftPos.addAll(second);
            shaftAx.addAll(secondAx);
        }

        Plan plan = new Plan(shaftPos, shaftAx, gearboxPos, gearboxAxis, verticalGearbox);
        return new ResultOutcome(Result.SUCCESS, plan);
    }

    /** 收集从 a 到 b(不含端点)之间沿 axis 的轴格; 若被不可替换方块挡住则返回 false。 */
    private static boolean collectShafts(Level world, BlockPos a, BlockPos b, Axis axis,
                                         List<BlockPos> out, List<Axis> outAxes) {
        Direction dir = directionBetween(a, b);
        int guard = 0;
        for (BlockPos p = a.relative(dir); !p.equals(b); p = p.relative(dir)) {
            if (++guard > MAX_LEG)
                return false;
            BlockState existing = world.getBlockState(p);
            if (!existing.canBeReplaced()
                && !(existing.getBlock() instanceof AbstractSimpleShaftBlock && existing.hasProperty(BlockStateProperties.AXIS)))
                return false;
            out.add(p.immutable());
            outAxes.add(axis);
        }
        return true;
    }

    /**
     * 真正执行连接(服务端权威): 先校验背包材料是否足够; 不足则返回 MATERIALS 且不做任何改动。
     * 成功则落块(switchToBlockState)并从背包/副手依次扣料(creative 跳过扣料)。
     */
    public static Result connect(ServerLevel world, Player player, BlockPos start, @Nullable BlockPos corner,
                                 BlockPos end) {
        ResultOutcome oc = plan(world, start, corner, end);
        if (oc.result != Result.SUCCESS)
            return oc.result;

        Plan plan = oc.plan;

        // 材料物品: 轴默认 create:shaft; 副手持"传动杆(轴变体)"则优先用副手
        Item shaftItem = resolveShaftItem(player);
        Item gearboxItem = plan.gearboxPos != null
            ? (plan.verticalGearbox ? AllItems.VERTICAL_GEARBOX.get() : AllBlocks.GEARBOX.asItem())
            : null;

        int shaftNeed = plan.shafts.size();
        int gearboxNeed = plan.gearboxPos != null ? 1 : 0;

        if (!player.isCreative()) {
            if (countItem(player, shaftItem) < shaftNeed)
                return Result.MATERIALS;
            if (gearboxNeed > 0 && countItem(player, gearboxItem) < gearboxNeed)
                return Result.MATERIALS;
        }

        // --- 放置 ---
        placeShafts(world, plan, shaftItem);
        if (plan.gearboxPos != null)
            placeGearbox(world, plan, gearboxItem);

        world.playSound(null, BlockPos.containing(
            net.createmod.catnip.math.VecHelper.getCenterOf(start.offset(end)).scale(.5f)),
            SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5F, 1F);

        // --- 扣料(creative 跳过) ---
        if (!player.isCreative()) {
            if (shaftNeed > 0)
                consumeItem(player, shaftItem, shaftNeed);
            if (gearboxNeed > 0)
                consumeItem(player, gearboxItem, gearboxNeed);
        }

        return Result.SUCCESS;
    }

    private static void placeShafts(ServerLevel world, Plan plan, Item shaftItem) {
        BlockState base = shaftBlockState(shaftItem);
        for (int i = 0; i < plan.shafts.size(); i++) {
            BlockPos pos = plan.shafts.get(i);
            Axis axis = plan.shaftAxes.get(i);
            BlockState state = base.hasProperty(BlockStateProperties.AXIS)
                ? base.setValue(BlockStateProperties.AXIS, axis)
                : base;
            KineticBlockEntity.switchToBlockState(world, pos, state);
        }
    }

    private static void placeGearbox(ServerLevel world, Plan plan, Item gearboxItem) {
        BlockState state = AllBlocks.GEARBOX.getDefaultState()
            .setValue(BlockStateProperties.AXIS, plan.gearboxAxis == null ? Axis.Y : plan.gearboxAxis);
        KineticBlockEntity.switchToBlockState(world, plan.gearboxPos, state);
    }

    /** 轴块标称状态(带默认 AXIS, 后面逐格按轴向 override)。 */
    private static BlockState shaftBlockState(Item shaftItem) {
        if (shaftItem instanceof BlockItem bi) {
            BlockState s = bi.getBlock().defaultBlockState();
            if (s.hasProperty(BlockStateProperties.AXIS))
                return s;
        }
        return AllBlocks.SHAFT.getDefaultState();
    }

    /** 副手若持"传动杆(轴变体)"则用副手物品, 否则用 create:shaft。 */
    private static Item resolveShaftItem(Player player) {
        Item off = player.getOffhandItem().getItem();
        if (off instanceof BlockItem bi && bi.getBlock() instanceof AbstractSimpleShaftBlock)
            return off;
        return AllBlocks.SHAFT.asItem();
    }

    /** 统计玩家主背包+副手中该物品的总数。 */
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

    /** 从主背包+副手中扣掉 count 个该物品(倒序, 不足则不扣 — 调用前已校验足量)。 */
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

    /** 双源方向安全校验: 两端各自网络已有转速且方向相反时, 连入会造成 Create 炸块, 拒连。 */
    private static boolean conflicts(Level world, BlockPos start, BlockPos end) {
        float s1 = speedAt(world, start);
        float s2 = speedAt(world, end);
        if (s1 == 0 || s2 == 0)
            return false;
        return Float.compare(Math.signum(s1), Math.signum(s2)) != 0 && Math.signum(s1) != 0;
    }

    private static float speedAt(Level world, BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof KineticBlockEntity kbe))
            return 0;
        return kbe.getTheoreticalSpeed();
    }

    /** 计算 a、b 之间的差异轴; 若两格不共轴(不严格对齐)或相同则返回 null。 */
    private static Axis axisBetween(BlockPos a, BlockPos b) {
        boolean dx = a.getX() != b.getX();
        boolean dy = a.getY() != b.getY();
        boolean dz = a.getZ() != b.getZ();
        int n = (dx ? 1 : 0) + (dy ? 1 : 0) + (dz ? 1 : 0);
        if (n != 1)
            return null;
        return dx ? Axis.X : dy ? Axis.Y : Axis.Z;
    }

    /** 沿 axis 从 a 指向 b 的单位方向。 */
    private static Direction directionBetween(BlockPos a, BlockPos b) {
        Axis axis = axisBetween(a, b);
        if (axis == null)
            return Direction.UP;
        int delta = axis.choose(b.getX(), b.getY(), b.getZ()) - axis.choose(a.getX(), a.getY(), a.getZ());
        return delta >= 0 ? Direction.get(AxisDirection.POSITIVE, axis)
            : Direction.get(AxisDirection.NEGATIVE, axis);
    }
}
