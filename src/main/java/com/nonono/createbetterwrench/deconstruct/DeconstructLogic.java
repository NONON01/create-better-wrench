package com.nonono.createbetterwrench.deconstruct;

import com.nonono.createbetterwrench.mode.DeconstructScope;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 「拆除」的服务端核心: 判定一个方块是否"可用扳手拆下"、是否落在某 Ctrl 档过滤内,
 * 以及遍历一个区域批量拆除并入背包/掉落。
 *
 * <p>判据(Create 自身规则, 见 docs/07 模式二已确认规格):
 * <ul>
 *   <li>可拆 = 方块 {@code instanceof IWrenchable} 或 位于 {@code create:wrench_pickup} tag;</li>
 *   <li>仅机械动力 = 上面集合 且 命名空间 == create;</li>
 *   <li>仅红石 = 上面集合 且 属红石类(枚举 MC 红石件 + forge 红石 tag)。</li>
 * </ul></p>
 */
public final class DeconstructLogic {

    /** Create 的 wrench_pickup block tag(数据包, create 命名空间)。 */
    public static final TagKey<Block> CREATE_WRENCH_PICKUP =
        TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("create", "wrench_pickup"));

    private DeconstructLogic() {
    }

    /** 该方块是否"可被扳手拆下"(Create 定义的可拆物)。 */
    public static boolean isWrenchRemovable(BlockState state) {
        Block block = state.getBlock();
        return block instanceof IWrenchable || state.is(CREATE_WRENCH_PICKUP);
    }

    /**
     * 在"可拆"基础上, 再按拆除范围档过滤。
     * 全部→直接可拆即算; 仅机械动力→命名空间 create; 仅红石→红石类。
     */
    public static boolean matchesScope(BlockState state, DeconstructScope scope) {
        if (!isWrenchRemovable(state))
            return false;
        return switch (scope) {
            case ALL -> true;
            case CREATE_ONLY -> isNamespace(state, "create");
            case REDSTONE_ONLY -> isRedstone(state);
        };
    }

    private static boolean isNamespace(BlockState state, String ns) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key != null && ns.equals(key.getNamespace());
    }

    /** 粗略红石类判定: 方块 id 含若干红石关键字。 */
    private static boolean isRedstone(BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = key == null ? "" : key.getPath();
        // 常见 MC 红石组件关键字(按钮/拉杆/门/压力板/红石线/比较器/中继器/目标方块/红石块)
        if (path.contains("button") || path.contains("lever") || path.contains("pressure_plate")
            || path.contains("redstone") || path.contains("comparator") || path.contains("repeater")
            || path.contains("target") || path.contains("observer") || path.contains("piston"))
            return true;
        return false;
    }

    /**
     * 拆除指定的一个方块, 产物进背包(满则掉落脚下)。
     *
     * <p><b>关键</b>: 只要方块是 {@link IWrenchable}, 就把拆除**交还给 Create 自己的
     * {@link IWrenchable#onSneakWrenched}**, 而不是自己 {@code destroyBlock}。
     * 因为多方块结构的连带拆除正是写在各个子类的覆盖实现里 —— 例如
     * {@code WaterWheelStructuralBlock} 会把点击位置**重定向到主方块**再整体拆掉
     * (大型水车/大水泵)。自己 destroy 只能拆掉一半, 留下孤儿方块。
     * 走这条路还顺带获得了 {@code BlockEvent.BreakEvent}(领地保护插件)与创造模式不掉落的正确行为。</p>
     *
     * @return 是否真的把这个位置的方块拆掉了(用于计数)
     */
    public static boolean deconstructBlock(ServerLevel level, BlockPos pos, ServerPlayer player) {
        BlockState state = level.getBlockState(pos);
        if (!isWrenchRemovable(state))
            return false;

        if (state.getBlock() instanceof IWrenchable wrenchable) {
            UseOnContext context = new UseOnContext(level, player, InteractionHand.MAIN_HAND,
                player.getMainHandItem(),
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            wrenchable.onSneakWrenched(state, context);
            // 被 BreakEvent 取消时方块还在 -> 计为未拆
            return level.getBlockState(pos).isAir();
        }

        // 只靠 create:wrench_pickup tag 被纳入的普通方块: 它没有 IWrenchable 逻辑, 沿用简单路径
        Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, player.getMainHandItem())
            .forEach(stack -> player.getInventory().placeItemBackInInventory(stack));
        state.spawnAfterBreak(level, pos, ItemStack.EMPTY, true);
        level.destroyBlock(pos, false);
        return true;
    }

    /**
     * 遍历 axis-aligned 区域(两角, 含端点), 按 scope 过滤并拆除, 返回拆除数量。
     * 仅由服务端调用。
     */
    public static int deconstructRegion(ServerLevel level, BlockPos cornerA, BlockPos cornerB,
                                        DeconstructScope scope, ServerPlayer player) {
        int minX = Math.min(cornerA.getX(), cornerB.getX()), maxX = Math.max(cornerA.getX(), cornerB.getX());
        int minY = Math.min(cornerA.getY(), cornerB.getY()), maxY = Math.max(cornerA.getY(), cornerB.getY());
        int minZ = Math.min(cornerA.getZ(), cornerB.getZ()), maxZ = Math.max(cornerA.getZ(), cornerB.getZ());

        int count = 0;
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir())
                        continue;
                    if (!matchesScope(state, scope))
                        continue;
                    if (deconstructBlock(level, pos, player))
                        count++;
                }
        return count;
    }
}
