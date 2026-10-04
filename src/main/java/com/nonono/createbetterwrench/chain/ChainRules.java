package com.nonono.createbetterwrench.chain;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 锁链传动轮(Chain Conveyor)的连接约束, 与 Create 同源。
 *
 * <p>判定逐条对齐 Create 的
 * {@code com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorConnectionHandler#validateAndConnect}
 * (1.21.1 / Create 6.0.10 的字节码已核对: 常量 2.5、1.5、坡度比 1.0 与语言键集合均未变化),
 * 顺序为: 距离上限 -> 距离下限 -> 水平距离为正 -> 坡度不超 1 -> 连接数上限。</p>
 *
 * <p>{@link #isValidLink} 只判定<b>连接本身</b>的合法性与两端已存在传动轮的容量, 不要求两端已经是传动轮:
 * 规划器需要对「尚未放置」的候选传动轮之间做几何判定(规格 §3.2 只要求终点已经是传动轮,
 * 起点与中间点由本模式放置)。终点是否为传动轮由调用方用 {@link #isConveyor} 单独判定;
 * 真正建立连接时 {@link ChainConnector#connect} 会再次确认两端方块实体存在。</p>
 *
 * <p>与 Create 的字面差异共两处, 均属本模式的有意放宽, 见各方法注释:
 * 连接数上限对<b>两端已存在的传动轮</b>同时判定(Create 的第二次点击只查目标, 但它的第一次点击也拒绝起点已满);
 * 已存在的连接按「复用」判为合法(Create 的连接包会拒绝重复连接, 而本规格 §4.1 要求复用既有连接)。</p>
 *
 * <p>本类不检查遮挡: 与 Create 一致, 锁链允许穿过方块。</p>
 *
 * <p>距离上限与每台连接数上限一律直接读取 Create 配置
 * {@code AllConfigs.server().kinetics.maxChainConveyorLength / maxChainConveyorConnections},
 * 本模组不另存数值。</p>
 */
public final class ChainRules {

    /** Create 侧固定的最小距离(方块角点欧氏距离, 单位: 格)。 */
    private static final double MIN_DISTANCE = 2.5;

    /** Create 侧的水平分量收缩量: 水平距离 = 两端水平分量长度 - 1.5(格)。 */
    private static final double HORIZONTAL_SHRINK = 1.5;

    /** Create 侧的坡度上限: |竖直位移| / 水平距离 不得超过 1(约 45 度)。 */
    private static final double MAX_SLOPE = 1.0;

    private ChainRules() {
    }

    /** 两轮的连接距离上限, 直接读 Create 配置 {@code maxChainConveyorLength}(默认 32)。 */
    public static int maxDistance() {
        return AllConfigs.server().kinetics.maxChainConveyorLength.get();
    }

    /** 每台传动轮的连接数上限, 直接读 Create 配置 {@code maxChainConveyorConnections}(默认 4)。 */
    public static int maxConnections() {
        return AllConfigs.server().kinetics.maxChainConveyorConnections.get();
    }

    /**
     * 该坐标是否为锁链传动轮方块({@code create:chain_conveyor})。
     *
     * <p>未加载的区块直接判否, 不触发区块加载(项目审计 A-5 口径: {@code isLoaded} 必须先于
     * {@code getBlockState})。</p>
     */
    public static boolean isConveyor(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos))
            return false;
        BlockState state = level.getBlockState(pos);
        return AllBlocks.CHAIN_CONVEYOR.has(state);
    }

    /** 两点之间能否建立一条 Create 认可的连接(等价于 {@link #violationKey} 返回 {@code null})。 */
    public static boolean isValidLink(Level level, BlockPos a, BlockPos b) {
        return violationKey(level, a, b) == null;
    }

    /**
     * 连接非法时返回 Create 语言键的后缀, 合法时返回 {@code null}。
     *
     * <p>返回的后缀与 Create 自身的 {@code create.chain_conveyor.*} 一一对应:
     * {@code too_far}、{@code too_close}、{@code cannot_connect_vertically}、{@code too_steep}、
     * {@code cannot_add_more_connections}、{@code blocks_invalid}。调用方可直接拼成
     * {@code create.chain_conveyor.<后缀>} 复用 Create 既有译文, 也可映射到本模组自己的键。</p>
     *
     * <p>坐标完全相同会落到 {@code too_close}(距离 0 小于 2.5), 与 Create「静默拒绝同点」等效, 不另造语言键。
     * {@code blocks_invalid} 只在参数为空这类编程错误时返回; 两端方块不符由 {@link #isConveyor} 判定,
     * 不在此处拦截, 否则规划器无法对尚未放置的候选传动轮做几何校验。</p>
     */
    public static String violationKey(Level level, BlockPos a, BlockPos b) {
        if (level == null || a == null || b == null)
            return "blocks_invalid";

        // ---- 几何四则: 与 Create 的判定顺序、常量逐条一致 ----

        // 1) 距离上限: !pos.closerThan(firstPos, maxChainConveyorLength)
        if (!b.closerThan(a, maxDistance()))
            return "too_far";

        // 2) 距离下限: pos.closerThan(firstPos, 2.5)
        if (b.closerThan(a, MIN_DISTANCE))
            return "too_close";

        // 3) 禁止垂直: 水平距离 = 水平分量长度 - 1.5, 必须大于 0
        Vec3 diff = Vec3.atLowerCornerOf(b.subtract(a));
        double horizontalDistance = diff.multiply(1, 0, 1)
            .length() - HORIZONTAL_SHRINK;
        if (horizontalDistance <= 0)
            return "cannot_connect_vertically";

        // 4) 坡度: |竖直位移| / 水平距离 不得超过 1
        if (Math.abs(diff.y) / horizontalDistance > MAX_SLOPE)
            return "too_steep";

        // ---- 两端「已存在」的传动轮容量(候选位置上尚无方块时跳过) ----

        ChainConveyorBlockEntity source = conveyor(level, a);
        ChainConveyorBlockEntity target = conveyor(level, b);

        // 已存在的连接视为合法复用: Create 的连接包拒绝重复连接(already_connected), 但本规格 §4.1 明确
        // 「路径上已有的传动轮, 若已有连接则保留」。真正的建立由 ChainConnector#connect 幂等完成。
        BlockPos aToB = b.subtract(a);
        BlockPos bToA = a.subtract(b);
        if (source != null && target != null
            && (source.connections.contains(aToB) || target.connections.contains(bToA)))
            return null;

        // Create 的第二次点击只查目标; 它的第一次点击同样拒绝起点已满, 故两端已存在的传动轮都需留有余量。
        if (source != null && source.connections.size() >= maxConnections())
            return "cannot_add_more_connections";
        if (target != null && target.connections.size() >= maxConnections())
            return "cannot_add_more_connections";

        return null;
    }

    /** 取该坐标的传动轮方块实体; 未加载或方块不符时返回 {@code null}(不触发区块加载)。 */
    private static ChainConveyorBlockEntity conveyor(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos))
            return null;
        return level.getBlockEntity(pos) instanceof ChainConveyorBlockEntity be ? be : null;
    }
}
