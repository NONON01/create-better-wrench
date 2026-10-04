package com.nonono.createbetterwrench.client.chain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import com.nonono.createbetterwrench.chain.ChainConnectServer;
import com.nonono.createbetterwrench.chain.ChainConnector;
import com.nonono.createbetterwrench.chain.ChainRules;
import com.nonono.createbetterwrench.chain.ChainScanner;
import com.nonono.createbetterwrench.config.WrenchConfig;
import com.nonono.createbetterwrench.mode.ChainSubMode;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 「锁链传动」三种子模式的路径规划器(纯客户端计算, 只读世界, 不放置方块, 不订阅事件)。
 *
 * <h2>与 Create 的关系</h2>
 * <p>本类不复制 Create 的任何约束数值。两轮的距离上限与连接数上限经 {@link ChainRules#maxDistance()}
 * 与 {@link ChainRules#maxConnections()} 读取; 每一段的合法性与失败原因经 {@link ChainRules#violationKey}
 * 判定; 锁链消耗经 {@link ChainConnector#chainCost} 计算; 既有传动轮经 {@link ChainScanner#conveyorsNear}
 * 检索。因此本规格第 3 节所列全部约束(最大距离, 最小距离, 禁止垂直, 坡度上限, 每台连接数上限)
 * 均以 Create 侧实现为准。</p>
 *
 * <h2>计划内的连接槽位累计</h2>
 * <p>{@link ChainRules#violationKey} 只按两端传动轮<b>当前</b>的连接数判定余量, 同一计划内为同一台既有
 * 传动轮新增的多条连接不会自动累加。本类因此为路径上的每个节点记录本计划已占用的槽位:
 * 一条计划连接在两端各占一个槽位; 该连接若已存在(经传动轮方块实体的连接集合判定), 则不占新槽位。
 * 容量校验要求「该节点当前连接数 + 本计划已占槽位 + 本次新增」不超过
 * {@link ChainRules#maxConnections()}。</p>
 *
 * <p>{@link ChainRules#violationKey} 不要求两端已经是传动轮(规格 §3.2 的终点判定由本类用
 * {@link ChainRules#isConveyor} 单独完成), 因此尚未放置的候选坐标可以直接参与几何校验。</p>
 *
 * <h2>三种子模式</h2>
 * <ul>
 *   <li><b>标准</b>: 有界 A* 搜索。优选次序为 新增传动轮数量最少 高于 经过的既有轮越多 高于 链总长最短
 *       高于 高差平滑(2026-10-04 内测确认)。新增轮数量的启发函数为剩余直线距离所需的最少额外传动轮,
 *       即 {@code ceil(剩余距离 / 单段上限) - 1}, 该值不高于真实所需, 因此可采纳; 复用台数只统计走廊带宽内
 *       检索到的既有轮, 因此不会为了多复用把路线拉远。硬性化简: 某轮若能与终点直接成段且不存在可零新增轮
 *       接入的既有轮, 立即在其处收束; 存在时允许把该段拆开以多复用一台。</li>
 *   <li><b>直线</b>: 同一套 A*, 但每次展开只允许沿 X 或 Z 一个水平轴前进, Y 在该轴步进内随坡度取值;
 *       同样复用既有传动轮, 且只复用与本次步进同向的轴对齐既有轮。该模式的全部连接(含终点直连)都必须与
 *       X 或 Z 轴平行, 因此对角线上的两点会按轴对齐步进拆分, 不会出现斜线。</li>
 *   <li><b>半自动</b>: 中间点的 X 与 Z 由调用方给定, 本类只求解 Y; 每列的高度由相邻的已确定节点按水平
 *       进度推出来(终点已知时用起终点插值, 否则沿"起点到上一列"的方向外推), 玩家点击方块的高度只作同等
 *       合法时的偏好; 每段必须直接合法, 不插入额外传动轮, 始终无解时返回 {@code valid=false}。终点可以为
 *       {@code null}, 表示玩家仍在逐个指定中间点, 此时只校验起点到最后一个中间点的各段(末段待终点确定后
 *       再校验), 供选区过程中的即时红框提示。</li>
 * </ul>
 *
 * <h2>搜索规模与最坏情况</h2>
 * <p>展开节点数上限取 {@link WrenchConfig#chainSearchMaxNodes()}(默认 1024), 新增传动轮数上限取
 * {@link WrenchConfig#chainSearchMaxConveyors()}(默认 256); 任一超限即返回 {@code valid=false} 与键
 * {@code limit}。单次展开的候选点上限为 {@link #MAX_SUCCESSORS}; 既有传动轮检索沿「可能的路线形状」
 * 采样: 标准模式为起终点直线段, 直线模式为两个 L 形走法, 采样间隔为单次半径的
 * {@link #REUSE_SAMPLE_STEP_RATIO} 倍(相邻立方体仍重叠, 不留下覆盖空档), 采样点合计不超过
 * {@link #MAX_REUSE_SAMPLES}, 单次半径不超过 {@link ChainRules#maxDistance()} 的一半。</p>
 *
 * <p>以新增传动轮为主序后, 主键相同的节点增多, 搜索会更充分地考察可复用的既有传动轮。该启发是可采纳的,
 * 但不保证一致(接入一台既有传动轮不增加新增轮数, 却可能减少剩余的估计新增轮数), 因此本类允许对同一坐标
 * 重新展开更优的标签以保持最优性; 复用台数一档为最大化目标的贪心次序, 只影响展开先后, 不参与最优性证明,
 * 可能进一步增加展开数量, 仍由节点上限约束。单节点计算量不变。</p>
 *
 * <p>新建落点避让: 生成新建传动轮的候选位置时, 排除与既有传动轮同列或水平间距不足
 * {@link #MIN_EXISTING_SPACING} 格的落点, 使绕行走开而不是贴着既有链再叠一层; 该排除只作用于新建落点,
 * 复用既有轮本身不受影响。若该口径在搜索上限内无解, 本类放宽一次(允许新落点再次贴近既有轮),
 * 以免因避让而完全无法铺设。</p>
 *
 * <p>新链不压过既有轮: 硬性直连条件、A* 后继候选与半自动的每一段都会检查线段是否压过既有轮(线段到
 * 既有轮的水平距离小于 {@link #MIN_EXISTING_SPACING} 即拒绝), 命中时改走该轮或另选落点; 半自动因此直接
 * 判非法并显示红框, 而不是把新链盖在旧链上。该守门在放宽轮中同样保留: 放宽只针对「新落点无替代位置」,
 * 而叠链正是用户可见的问题, 不应通过放宽重新引入。</p>
 *
 * <p>不检查遮挡(规格第 3.2 节): 本类不判定路径是否穿过方块, 只要求候选坐标已加载且位于世界建筑高度内。</p>
 */
public final class ChainPlanner {

    /** 起点到终点的传动轮序列, 以及其中需要新建的传动轮与相邻连接。 */
    public record Link(net.minecraft.core.BlockPos a, net.minecraft.core.BlockPos b) {
    }

    /**
     * 一次规划的完整结果。
     *
     * @param path         起点到终点的传动轮序列(非法时为空列表);
     * @param placed       其中需要新建的传动轮(合法时与 path 的差集一致);
     * @param links        相邻两轮之间的连接(非法时为空列表);
     * @param valid        整条路径是否合法;
     * @param violationKey 非法时的语言键后缀, 合法时为 {@code null};
     * @param conveyorCost 需要新建的传动轮数量;
     * @param chainCost    全部连接消耗的锁链数量;
     * @param nodesVisited 本次规划展开的搜索节点数。
     */
    public record Plan(java.util.List<net.minecraft.core.BlockPos> path,
                       java.util.List<BlockPos> placed,
                       java.util.List<Link> links,
                       boolean valid, String violationKey,
                       int conveyorCost, int chainCost,
                       int nodesVisited) {
    }

    /** 失败原因: 至少一端不是传动轮, 或所在区块未加载。 */
    private static final String KEY_BLOCKS_INVALID = "blocks_invalid";
    /**
     * 失败原因: 达到搜索上限, 或在上限内没有找到完整路径。
     *
     * <p>该键不是 Create 的既有后缀, 由客户端语言键 {@code hint.create_better_wrench.chain.violation.limit}
     * 承担; 其余失败原因直接沿用 {@link ChainRules#violationKey} 返回的后缀, 可复用 Create 的既有译文。</p>
     */
    private static final String KEY_LIMIT = "limit";
    /**
     * 失败原因: 新连接线段会压过既有传动轮。
     *
     * <p>线路与既有轮重叠时应改走该轮(复用)或由玩家调整选点, 而不是把新链盖在旧链上。该键指向本模组
     * 已有的通用不合法文案; 若需要专门提示, 由语言文件另加键后替换此处取值。</p>
     */
    private static final String KEY_OVERLAP = "hint.create_better_wrench.chain.invalid";
    /**
     * 失败原因: 一次铺设的全部链段长度之和超过 {@code chain.max_total_length}。
     *
     * <p>该键指向本模组的专用文案(含点号, 会被 {@code ChainSelectionHandler#violationKey} 原样透传);
     * 判定与服务端 {@code chain/ChainConnectServer} 共用 {@link ChainConnectServer#totalChainLength} 与
     * {@link WrenchConfig#chainMaxTotalLength()}, 两端口径一致。</p>
     */
    private static final String KEY_TOTAL_TOO_LONG = "hint.create_better_wrench.chain.total_too_long";

    /** 单次展开允许返回的候选坐标数量上限, 用于约束客户端的单次计算量。 */
    private static final int MAX_SUCCESSORS = 48;
    /** 标准模式沿目标方向采样的第二档步长比例(相对单段最大距离)。 */
    private static final double CORRIDOR_LENGTH_RATIO = 0.5;
    /** 复用采样点相对单次检索半径的间隔比例: 1.5 倍时相邻扫描立方体仍重叠半个半径。 */
    private static final double REUSE_SAMPLE_STEP_RATIO = 1.5;
    /** 复用检索的采样点数量上限, 直线模式下为两个 L 形走法合计的采样点。 */
    private static final int MAX_REUSE_SAMPLES = 16;
    /** 新建落点与既有传动轮的最小水平间距(格): 同列或水平距离更近都算过近。 */
    private static final double MIN_EXISTING_SPACING = 2.5;
    /** 直线模式每个水平步进允许的 Y 偏移范围(以目标方向插值高度为中心)。 */
    private static final int STRAIGHT_VERTICAL_WINDOW = 3;
    /** 半自动模式每列允许的 Y 候选数量上限。 */
    private static final int SEMI_MAX_CANDIDATES = 12;

    /** 标准模式在理想落点周围的搜索偏移, 用于绕开已满或几何不合法的相邻格。 */
    private static final int[][] CORRIDOR_OFFSETS = {
        { 0, 0, 0 },
        { 1, 0, 0 }, { -1, 0, 0 },
        { 0, 1, 0 }, { 0, -1, 0 },
        { 0, 0, 1 }, { 0, 0, -1 },
        { 2, 0, 0 }, { -2, 0, 0 },
        { 0, 2, 0 }, { 0, -2, 0 },
        { 0, 0, 2 }, { 0, 0, -2 }
    };

    /** 半自动模式每列尝试的 Y 偏移, 按与插值高度的接近程度排列。 */
    private static final int[] SEMI_VERTICAL_DELTAS = {
        0, 1, -1, 2, -2, 3, -3, 4, -4, 6, -6, 8, -8, 12, -12
    };

    /** 半自动参考高度相对上一个节点的外推夹取幅度, 让排序偏好不偏离上一个节点太远(与候选邻域同幅度)。 */
    private static final int SEMI_REFERENCE_WINDOW = 12;

    /**
     * 队列次序: 第一键为「已用新增轮 + 剩余最少新增轮」, 第二键为「已复用的既有轮数量(多者优先)」,
     * 第三键为「已走链长 + 剩余直线距离」, 第四键为剩余直线距离(收敛用)。
     *
     * <p>第一键使用可采纳下界, 因此首个被取出的目标点新增轮数最少; 第二键只统计走廊带宽内检索到的
     * 既有轮(集合由 {@code reuseCandidates} 收集), 因此不会为了多复用把路线拉到带外; 第三键保证同等
     * 复用台数下链总长最短。第二键对「最大化复用台数」不设上界启发(取 0), 属于偏好多复用的贪心次序,
     * 不是可采纳启发; 其代价是节点可能被更多地展开, 仍由搜索上限约束。</p>
     */
    private static final Comparator<SearchNode> ORDER = (a, b) -> {
        int byCount = Integer.compare(a.priorityCount, b.priorityCount);
        if (byCount != 0)
            return byCount;
        int byReuse = Integer.compare(b.reusedWheels, a.reusedWheels);
        if (byReuse != 0)
            return byReuse;
        int byLength = Double.compare(a.priorityLength, b.priorityLength);
        if (byLength != 0)
            return byLength;
        int byHeuristic = Double.compare(a.heuristic, b.heuristic);
        if (byHeuristic != 0)
            return byHeuristic;
        int byNew = Integer.compare(a.newConveyors, b.newConveyors);
        if (byNew != 0)
            return byNew;
        int byRoughness = Double.compare(a.roughness, b.roughness);
        if (byRoughness != 0)
            return byRoughness;
        int byAssigned = Integer.compare(a.assignedLinks, b.assignedLinks);
        if (byAssigned != 0)
            return byAssigned;
        return a.pos.compareTo(b.pos);
    };

    private ChainPlanner() {
    }

    /**
     * 规划一条从起点到终点的锁链传动路径(纯计算, 只读世界)。
     *
     * <p>半自动模式允许 {@code end} 为 {@code null}, 表示玩家正在逐个指定中间点、终点尚未确定;
     * 此时只校验起点到最后一个中间点的各段, 用于选区过程中的即时提示。</p>
     *
     * @param level     客户端世界;
     * @param start     起点传动轮(必须是已存在的锁链传动轮);
     * @param waypoints 半自动模式的中间点, 只有 X 与 Z 参与判定, 其它子模式忽略;
     * @param end       终点传动轮(必须是已存在的锁链传动轮); 半自动模式下可为 {@code null};
     * @param sub       子模式;
     * @return 规划结果, 非法时路径与连接均为空列表。
     */
    public static Plan plan(Level level, BlockPos start, List<BlockPos> waypoints, BlockPos end, ChainSubMode sub) {
        if (level == null || start == null || sub == null)
            return invalid(KEY_BLOCKS_INVALID, 0);
        if (!ChainRules.isConveyor(level, start) || level.isOutsideBuildHeight(start))
            return invalid(KEY_BLOCKS_INVALID, 0);

        if (end == null) {
            // 半自动选区中途: 只校验起点到最后一个中间点的各段
            if (sub != ChainSubMode.SEMI || waypoints == null || waypoints.isEmpty())
                return invalid(KEY_BLOCKS_INVALID, 0);
            return semi(level, start, waypoints, null);
        }

        if (!ChainRules.isConveyor(level, end) || level.isOutsideBuildHeight(end))
            return invalid(KEY_BLOCKS_INVALID, 0);
        if (start.equals(end))
            return invalid("too_close", 0);

        return switch (sub) {
            case STANDARD -> search(level, start, end, false);
            case STRAIGHT -> search(level, start, end, true);
            case SEMI -> semi(level, start, waypoints, end);
        };
    }

    // ------------------------------------------------------------------ 标准与直线: 有界 A*

    /**
     * 标准模式与直线模式共用的有界 A*。
     *
     * <p>先以「新建落点避开既有传动轮」的严格口径规划一次; 若该口径下无解, 再放宽一次(仅保留 Create
     * 自身的连接约束), 以免因为避让而完全无法铺设。</p>
     *
     * @param axisAligned 为真时每次展开只允许沿 X 或 Z 一个水平轴前进(直线模式)。
     */
    private static Plan search(Level level, BlockPos start, BlockPos end, boolean axisAligned) {
        List<BlockPos> reuse = reuseCandidates(level, start, end, axisAligned);
        Set<BlockPos> reuseSet = new HashSet<>(reuse);

        Plan strict = searchRoute(level, start, end, axisAligned, reuse, reuseSet, true);
        if (strict.valid())
            return strict;
        // 放宽条件: 严格口径在搜索上限内找不到合法路径时, 允许新落点再次贴近既有轮
        return searchRoute(level, start, end, axisAligned, reuse, reuseSet, false);
    }

    /**
     * 单次 A* 搜索。
     *
     * @param avoidExisting 为真时新建落点不得与既有传动轮过近(判据见 {@link #isTooCloseToExisting});
     *                      为假时只保留 Create 自身的连接约束。
     */
    private static Plan searchRoute(Level level, BlockPos start, BlockPos end, boolean axisAligned,
                                    List<BlockPos> reuse, Set<BlockPos> reuseSet, boolean avoidExisting) {
        int maxNodes = WrenchConfig.chainSearchMaxNodes();
        int maxConveyors = WrenchConfig.chainSearchMaxConveyors();
        int maxDistance = ChainRules.maxDistance();

        PriorityQueue<SearchNode> open = new PriorityQueue<>(ORDER);
        Map<BlockPos, SearchNode> best = new HashMap<>();
        SearchNode root = new SearchNode(start, 0.0, 0, 0, 0.0, null, end, 0, 0, maxDistance);
        open.add(root);
        best.put(start, root);

        int visited = 0;
        String failureKey = null;
        double failureDistance = Double.MAX_VALUE;
        // 已经能走到终点、但"全部链段长度之和"超上限时的原因: 不直接返回, 继续搜索更短的合法路径
        String capFailure = null;

        while (!open.isEmpty()) {
            SearchNode current = open.poll();
            if (best.get(current.pos) != current)
                continue;                       // 同坐标已有更优节点, 该条目作废
            visited++;
            if (visited > maxNodes)
                return invalid(KEY_LIMIT, visited);
            if (current.pos.equals(end)) {
                List<BlockPos> reached = reconstruct(current);
                if (exceedsTotalLength(reached)) {
                    capFailure = KEY_TOTAL_TOO_LONG;    // 超总长: 不作为结果, 继续找合计更短的路
                    continue;
                }
                return buildFromPath(level, reached, maxConveyors, visited);
            }

            // 硬性化简: 当前轮若能与终点直接成段, 立即在此收束, 不再展开下一步。
            // 例外: 走廊带宽内仍有可零新增轮接入的既有轮时先不收束, 允许把本可直连的一段拆开以多复用一台;
            // 拆分只经过既有轮, 新增轮数量不变。标准模式不要求同轴; 直线模式要求水平面内与终点同轴。
            // 若该段会压过其它既有轮, 同样不收束, 让搜索改走该轮或另选落点, 避免新链盖在旧链上。
            if (canLinkDirectly(level, current, end, axisAligned)
                && !hasReusableDetour(level, current, end, axisAligned, reuseSet)
                && !runsOverExisting(level, current.pos, end, reuseSet)) {
                List<BlockPos> path = reconstruct(current);
                path.add(end);
                if (!exceedsTotalLength(path))
                    return buildFromPath(level, path, maxConveyors, visited);
                capFailure = KEY_TOTAL_TOO_LONG;    // 直连会超总长: 落回后继展开, 让搜索另找更短的路
            }

            List<BlockPos> candidates = axisAligned
                ? straightSuccessors(current.pos, end, maxDistance, reuse)
                : standardSuccessors(current.pos, end, maxDistance, reuse);

            for (BlockPos candidate : candidates) {
                if (candidate.equals(current.pos) || candidate.equals(start))
                    continue;
                if (!level.isLoaded(candidate) || level.isOutsideBuildHeight(candidate))
                    continue;

                int added = linkAddedSlots(level, current.pos, candidate);
                String key = linkKey(level, current.pos, candidate, current.assignedLinks, added);
                if (key != null) {
                    double distanceToGoal = current.pos.distSqr(end);
                    if (distanceToGoal < failureDistance) {
                        failureDistance = distanceToGoal;
                        failureKey = key;
                    }
                    continue;
                }

                boolean isNew = !ChainRules.isConveyor(level, candidate);
                if (isNew && avoidExisting && isTooCloseToExisting(candidate, reuseSet))
                    continue;                   // 绕行时把新落点放在既有轮近旁会叠出平行链
                if (runsOverKnown(current.pos, candidate, reuseSet)) {
                    // 该段会压过既有轮: 改走该轮或另选落点。放宽轮同样保留该守门, 不允许新链盖在旧链上。
                    double distanceToGoal = current.pos.distSqr(end);
                    if (distanceToGoal < failureDistance) {
                        failureDistance = distanceToGoal;
                        failureKey = KEY_OVERLAP;
                    }
                    continue;
                }
                int newCount = current.newConveyors + (isNew ? 1 : 0);
                if (newCount > maxConveyors) {
                    double distanceToGoal = current.pos.distSqr(end);
                    if (distanceToGoal < failureDistance) {
                        failureDistance = distanceToGoal;
                        failureKey = KEY_LIMIT;
                    }
                    continue;
                }

                double length = current.chainLength
                    + Vec3.atCenterOf(current.pos).distanceTo(Vec3.atCenterOf(candidate));
                int vertical = candidate.getY() - current.pos.getY();
                double roughness = current.parent == null
                    ? Math.abs(vertical)
                    : current.roughness + Math.abs(vertical - current.lastVertical);
                boolean reusedHere = !candidate.equals(end) && reuseSet.contains(candidate);

                SearchNode next = new SearchNode(candidate, length, newCount,
                    current.reusedWheels + (reusedHere ? 1 : 0), roughness, current, end,
                    vertical, added, maxDistance);
                SearchNode previous = best.get(candidate);
                if (previous != null && !next.betterThan(previous))
                    continue;
                best.put(candidate, next);
                open.add(next);
            }
        }

        // 能走到终点却因"总链条长度"受限时, 优先报该原因(它比某个死角的几何失败更能说明问题)
        if (capFailure != null)
            return invalid(capFailure, visited);
        return invalid(failureKey != null ? failureKey : KEY_LIMIT, visited);
    }

    /**
     * 标准模式的候选点: 目标点本身, 沿目标方向的理想落点及其邻域偏移, 以及走廊附近可复用的既有传动轮。
     *
     * <p>走廊落点先于既有传动轮加入, 避免候选数量达到 {@link #MAX_SUCCESSORS} 时挤掉必要的直行落点。
     * 既有传动轮中, 与当前轮同列(该段垂直)或与终点同列(只能垂直连到终点)的都不作为候选, 前者无法连接,
     * 后者只会把路径引向死路并虚报复用台数。</p>
     */
    private static List<BlockPos> standardSuccessors(BlockPos from, BlockPos goal, int maxDistance,
                                                     List<BlockPos> reuse) {
        Set<BlockPos> candidates = new LinkedHashSet<>();
        candidates.add(goal);

        Vec3 a = Vec3.atCenterOf(from);
        Vec3 b = Vec3.atCenterOf(goal);
        double remaining = a.distanceTo(b);
        Vec3 direction = b.subtract(a);
        if (direction.lengthSqr() > 1.0e-6) {
            direction = direction.normalize();
            double[] lengths = {
                Math.min(maxDistance, remaining),
                Math.min(maxDistance * CORRIDOR_LENGTH_RATIO, remaining)
            };
            for (double length : lengths) {
                if (length <= 0.0 || candidates.size() >= MAX_SUCCESSORS)
                    continue;
                BlockPos base = BlockPos.containing(a.add(direction.scale(length)));
                for (int[] offset : CORRIDOR_OFFSETS) {
                    if (candidates.size() >= MAX_SUCCESSORS)
                        break;
                    candidates.add(base.offset(offset[0], offset[1], offset[2]));
                }
            }
        }

        for (BlockPos pos : reuse) {
            if (candidates.size() >= MAX_SUCCESSORS)
                break;
            if (pos.equals(from) || sharesColumn(pos, from) || sharesColumn(pos, goal)
                || !pos.closerThan(from, maxDistance))
                continue;                       // 同列竖直叠放无法连接, 不作为候选
            candidates.add(pos);
        }
        return new ArrayList<>(candidates);
    }

    /**
     * 直线模式的候选点: 每一步只沿 X 或 Z 一个水平轴前进, Y 由当前高度到目标高度的按比例插值决定,
     * 并在插值高度的邻域内取值, 使每一段满足 Create 的坡度约束。
     *
     * <p>该模式同样复用既有传动轮, 但只接受与本次轴对齐步进同向且不越过终点的既有轮, 保证全部连接
     * 在水平面内与 X 或 Z 轴平行; 与终点同列的既有轮不作为候选(只能垂直连到终点)。复用候选先于新建步进
     * 加入, 使候选数量达到 {@link #MAX_SUCCESSORS} 时不会被挤掉。</p>
     */
    private static List<BlockPos> straightSuccessors(BlockPos from, BlockPos goal, int maxDistance,
                                                     List<BlockPos> reuse) {
        Set<BlockPos> candidates = new LinkedHashSet<>();
        int dx = goal.getX() - from.getX();
        int dz = goal.getZ() - from.getZ();

        // 与目标同轴时允许本段直接收到终点
        if (dx == 0 || dz == 0)
            candidates.add(goal);

        for (BlockPos pos : reuse) {
            if (candidates.size() >= MAX_SUCCESSORS)
                break;
            if (pos.equals(from) || sharesColumn(pos, goal) || !pos.closerThan(from, maxDistance))
                continue;
            if (isStraightReuse(from, pos, dx, dz))
                candidates.add(pos);
        }

        if (dx != 0)
            addStraightSteps(candidates, from, goal, Integer.signum(dx), Math.abs(dx), true, maxDistance);
        if (dz != 0)
            addStraightSteps(candidates, from, goal, Integer.signum(dz), Math.abs(dz), false, maxDistance);
        return new ArrayList<>(candidates);
    }

    /**
     * 直线模式下的复用约束: 既有轮必须与本次步进在同一水平轴上, 前进方向与目标一致, 且不越过终点。
     *
     * @param dx 当前轮到终点的 X 位移;
     * @param dz 当前轮到终点的 Z 位移。
     */
    private static boolean isStraightReuse(BlockPos from, BlockPos pos, int dx, int dz) {
        int moveX = pos.getX() - from.getX();
        int moveZ = pos.getZ() - from.getZ();
        boolean alongX = moveZ == 0 && moveX != 0
            && Integer.signum(moveX) == Integer.signum(dx) && Math.abs(moveX) <= Math.abs(dx);
        boolean alongZ = moveX == 0 && moveZ != 0
            && Integer.signum(moveZ) == Integer.signum(dz) && Math.abs(moveZ) <= Math.abs(dz);
        return alongX || alongZ;
    }

    private static void addStraightSteps(Set<BlockPos> candidates, BlockPos from, BlockPos goal,
                                         int sign, int horizontalRemaining, boolean alongX, int maxDistance) {
        int limit = Math.min(maxDistance, horizontalRemaining);
        if (limit <= 0)
            return;
        int manhattan = Math.abs(goal.getX() - from.getX()) + Math.abs(goal.getZ() - from.getZ());
        int[] stepLengths = { limit, Math.max(1, limit / 2), Math.max(1, limit / 4) };

        for (int step : stepLengths) {
            if (candidates.size() >= MAX_SUCCESSORS)
                return;
            int interpolated = from.getY();
            if (manhattan > 0) {
                double ratio = (double) step / manhattan;
                interpolated = (int) Math.round(from.getY() + (goal.getY() - from.getY()) * ratio);
            }
            for (int delta = -STRAIGHT_VERTICAL_WINDOW; delta <= STRAIGHT_VERTICAL_WINDOW; delta++) {
                if (candidates.size() >= MAX_SUCCESSORS)
                    return;
                int x = alongX ? from.getX() + sign * step : from.getX();
                int z = alongX ? from.getZ() : from.getZ() + sign * step;
                candidates.add(new BlockPos(x, interpolated + delta, z));
            }
        }
    }

    /** 采样折线的一段, 用于描述规划可能采用的路线形状。 */
    private record RouteSegment(BlockPos from, BlockPos to) {
    }

    /**
     * 规划可能采用的路线形状。
     *
     * <p>标准模式的路线是起点到终点的直线段; 直线模式的路线是两个 L 形走法(先沿 X 再沿 Z, 以及先沿 Z
     * 再沿 X), 两者合计覆盖该模式下所有只转一次弯的轴对齐走法; 起点与终点同轴时退化为该直线段。</p>
     */
    private static List<RouteSegment> routeSegments(BlockPos start, BlockPos end, boolean axisAligned) {
        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();
        if (!axisAligned || dx == 0 || dz == 0)
            return List.of(new RouteSegment(start, end));

        int manhattan = Math.abs(dx) + Math.abs(dz);
        int cornerY1 = (int) Math.round(start.getY() + (end.getY() - start.getY())
            * (Math.abs(dx) / (double) manhattan));
        int cornerY2 = (int) Math.round(start.getY() + (end.getY() - start.getY())
            * (Math.abs(dz) / (double) manhattan));
        BlockPos cornerX = new BlockPos(end.getX(), cornerY1, start.getZ());
        BlockPos cornerZ = new BlockPos(start.getX(), cornerY2, end.getZ());
        return List.of(
            new RouteSegment(start, cornerX), new RouteSegment(cornerX, end),
            new RouteSegment(start, cornerZ), new RouteSegment(cornerZ, end));
    }

    /**
     * 收集路线形状(直线段与两个 L 形走法)附近的既有传动轮, 供 A* 优先复用。
     *
     * <p>采样点按 {@code 1.5 倍检索半径}的间隔沿每条折线布置, 因此相邻采样点的立方体至少重叠半个半径,
     * 落在折线两侧一个半径内的既有轮不会被采样空档漏掉; 采样点数量上限为 {@link #MAX_REUSE_SAMPLES},
     * 超出时按折线轮流截断, 路线很长时采样间隔会随之变稀。每个采样点调用一次
     * {@link ChainScanner#conveyorsNear}, 单次半径不超过 {@link ChainRules#maxDistance()} 的一半。</p>
     *
     * <p>结果按到最近折线段的距离升序排序, 同一坐标只保留一次; 该次序使 A* 在候选数量达到上限时
     * 优先展开离路线更近的既有传动轮。</p>
     */
    private static List<BlockPos> reuseCandidates(Level level, BlockPos start, BlockPos end, boolean axisAligned) {
        int radius = Math.max(1, ChainRules.maxDistance() / 2);
        int step = Math.max(1, (int) Math.ceil(radius * REUSE_SAMPLE_STEP_RATIO));
        List<RouteSegment> route = routeSegments(start, end, axisAligned);
        List<BlockPos> samples = reuseSamples(route, step);

        Set<BlockPos> found = new LinkedHashSet<>();
        for (BlockPos sample : samples)
            found.addAll(ChainScanner.conveyorsNear(level, sample, radius));

        List<BlockPos> result = new ArrayList<>(found);
        result.sort(Comparator.comparingDouble((BlockPos pos) -> distanceToRoute(pos, route)));
        return result;
    }

    /** 沿每条折线按固定间隔取采样点, 并按折线轮流截断到 {@link #MAX_REUSE_SAMPLES}。 */
    private static List<BlockPos> reuseSamples(List<RouteSegment> route, int step) {
        List<List<BlockPos>> perSegment = new ArrayList<>(route.size());
        for (RouteSegment segment : route)
            perSegment.add(samplesAlong(segment, step));

        Set<BlockPos> samples = new LinkedHashSet<>();
        for (int index = 0; samples.size() < MAX_REUSE_SAMPLES; index++) {
            boolean added = false;
            for (List<BlockPos> segment : perSegment) {
                if (index >= segment.size() || samples.size() >= MAX_REUSE_SAMPLES)
                    continue;
                samples.add(segment.get(index));
                added = true;
            }
            if (!added)
                break;
        }
        return new ArrayList<>(samples);
    }

    /** 一段折线上的采样点, 含两端; 高度沿该段线性插值。 */
    private static List<BlockPos> samplesAlong(RouteSegment segment, int step) {
        Vec3 from = Vec3.atCenterOf(segment.from());
        Vec3 to = Vec3.atCenterOf(segment.to());
        int count = Math.max(1, (int) Math.ceil(from.distanceTo(to) / step));
        List<BlockPos> samples = new ArrayList<>(count + 1);
        for (int i = 0; i <= count; i++) {
            double ratio = (double) i / count;
            samples.add(BlockPos.containing(
                from.x + (to.x - from.x) * ratio,
                from.y + (to.y - from.y) * ratio,
                from.z + (to.z - from.z) * ratio));
        }
        return samples;
    }

    /** 点到路线各折线段的最小距离, 用于优先展开离路线更近的既有传动轮。 */
    private static double distanceToRoute(BlockPos pos, List<RouteSegment> route) {
        double best = Double.MAX_VALUE;
        for (RouteSegment segment : route)
            best = Math.min(best, distanceToSegment(pos, segment.from(), segment.to()));
        return best;
    }

    /** 点到一条线段(端点坐标取方块中心)的直线距离。 */
    private static double distanceToSegment(BlockPos pos, BlockPos from, BlockPos to) {
        Vec3 a = Vec3.atCenterOf(from);
        Vec3 ab = Vec3.atCenterOf(to).subtract(a);
        double lengthSqr = ab.lengthSqr();
        Vec3 p = Vec3.atCenterOf(pos);
        if (lengthSqr < 1.0e-6)
            return p.distanceTo(a);
        double ratio = Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / lengthSqr));
        return p.distanceTo(a.add(ab.scale(ratio)));
    }

    // ------------------------------------------------------------------ 半自动: 只解 Y

    /** 半自动模式的中间选择列: X 与 Z 由玩家指定, {@code clickedY} 为点击方块的高度(只作同等合法时的偏好)。 */
    private record Column(int x, int z, int clickedY) {
    }

    /**
     * 半自动模式求解: 中间点的 X 与 Z 已由玩家给出, 本方法只求解各点的 Y, 使每一段都满足 Create 约束。
     *
     * <p>参考高度只由已确定的节点推出来, 与玩家点击方块的高度无关: 终点已确定时在起终点之间按水平进度
     * 线性插值; 选区尚未结束时由 {@link #referenceHeight} 沿"起点到上一列"的方向外推。玩家点击的高度
     * 只作为同等合法时的偏好(见 {@link #columnCandidates}), 因此点在地面方块上不会让本来合法的列报坡度超限。</p>
     */
    private static Plan semi(Level level, BlockPos start, List<BlockPos> waypoints, BlockPos end) {
        int maxNodes = WrenchConfig.chainSearchMaxNodes();
        int maxConveyors = WrenchConfig.chainSearchMaxConveyors();

        List<Column> columns = new ArrayList<>();
        if (waypoints != null) {
            for (BlockPos waypoint : waypoints) {
                if (waypoint == null)
                    continue;
                if (waypoint.getX() == start.getX() && waypoint.getZ() == start.getZ())
                    continue;                   // 与起点同列, 不额外放置
                if (end != null && waypoint.getX() == end.getX() && waypoint.getZ() == end.getZ())
                    continue;                   // 与终点同列, 直接由终点承担
                Column column = new Column(waypoint.getX(), waypoint.getZ(), waypoint.getY());
                if (!columns.isEmpty()) {
                    Column last = columns.get(columns.size() - 1);
                    if (last.x() == column.x() && last.z() == column.z())
                        continue;               // 与上一个中间点同列, 只保留一次
                }
                columns.add(column);
                if (columns.size() > maxConveyors)
                    return invalid(KEY_LIMIT, columns.size());
            }
        }
        if (columns.isEmpty()) {
            if (end == null)
                return invalid(KEY_BLOCKS_INVALID, 0);
            return linkOnly(level, start, end);
        }

        int count = columns.size();

        // 各列已有的传动轮: 既作为该列的复用候选, 也作为新建落点的间距与"压过既有轮"守门的参照集合
        List<Set<BlockPos>> columnWheels = new ArrayList<>(count);
        Set<BlockPos> knownWheels = new HashSet<>();
        for (int i = 0; i < count; i++) {
            Set<BlockPos> found = new LinkedHashSet<>(conveyorsInColumn(level, columns.get(i)));
            columnWheels.add(found);
            knownWheels.addAll(found);
        }
        knownWheels.add(start);
        if (end != null)
            knownWheels.add(end);

        // 参考高度: 终点已确定时在起终点之间按水平进度插值; 选区尚未结束时留空, 由求解时逐列推算
        int[] referenceY = null;
        if (end != null) {
            referenceY = new int[count];
            double[] reach = new double[count];
            double total = 0.0;
            int lastX = start.getX();
            int lastZ = start.getZ();
            for (int i = 0; i < count; i++) {
                Column column = columns.get(i);
                total += horizontalDistance(lastX, lastZ, column.x(), column.z());
                reach[i] = total;
                lastX = column.x();
                lastZ = column.z();
            }
            total += horizontalDistance(lastX, lastZ, end.getX(), end.getZ());
            if (total <= 0.0)
                return invalid(KEY_LIMIT, 0);
            for (int i = 0; i < count; i++)
                referenceY[i] = (int) Math.round(
                    start.getY() + (end.getY() - start.getY()) * (reach[i] / total));
        }

        List<BlockPos> chosen = new ArrayList<>();
        int[] counter = { 0 };
        String[] failure = { null };
        boolean solved = solveColumn(level, columns, columnWheels, knownWheels, referenceY, end, 0, start, null,
            0, 0, chosen, counter, maxNodes, maxConveyors, failure);
        if (!solved)
            return invalid(failure[0] != null ? failure[0] : KEY_LIMIT, counter[0]);

        List<BlockPos> path = new ArrayList<>();
        path.add(start);
        path.addAll(chosen);
        if (end != null)
            path.add(end);
        return buildFromPath(level, path, maxConveyors, counter[0]);
    }

    /**
     * 半自动模式的按列回溯求解: 每列取一个 Y, 使相邻两轮与首尾两段都满足约束。
     *
     * <p>列的顺序与 X/Z 由调用方固定, 求解只在每列的 Y 候选之间选择, 因此不会改动玩家指定的水平位置。
     * 每列的参考高度只由已确定的节点给出: 终点已知时用 {@code referenceY} 的起终点插值, 否则用
     * {@link #referenceHeight} 沿"起点到上一列"的方向外推; 玩家点击的高度只作同等合法时的偏好。
     * 段长与坡度在每一列展开时即按 {@link #linkKey} 校验; 若某段会压过既有传动轮, 同样拒绝该候选,
     * 使预览直接判非法(红框)而不是把新链盖在既有轮上。某列没有可用落点时同样整单判非法。</p>
     *
     * @param referenceY       终点已知时各列的参考高度; 为 {@code null} 表示选区尚未结束;
     * @param previous         上一个已确定节点(起点, 或上一列选中的落点);
     * @param prevPrevious     {@code previous} 之前的那个已确定节点, 用于推算方向; 可为 {@code null};
     * @param assignedPrevious 前一个节点在本计划内已占用的新增连接槽位;
     * @param knownWheels      各列既有轮与端点的集合, 用作"压过既有轮"的守门参照。
     */
    private static boolean solveColumn(Level level, List<Column> columns, List<Set<BlockPos>> columnWheels,
                                       Set<BlockPos> knownWheels, int[] referenceY, BlockPos end, int index,
                                       BlockPos previous, BlockPos prevPrevious, int assignedPrevious, int newCount,
                                       List<BlockPos> chosen, int[] counter, int maxNodes, int maxConveyors,
                                       String[] failure) {
        if (index >= columns.size()) {
            if (end == null)
                return true;                    // 选区尚未结束: 末段在终点确定后再校验
            if (runsOverExisting(level, previous, end, knownWheels)) {
                failure[0] = KEY_OVERLAP;
                return false;
            }
            int added = linkAddedSlots(level, previous, end);
            String key = linkKey(level, previous, end, assignedPrevious, added);
            if (key == null)
                return true;
            failure[0] = key;
            return false;
        }

        Column column = columns.get(index);
        int reference = referenceY != null
            ? referenceY[index]
            : referenceHeight(previous, prevPrevious, column);
        List<BlockPos> candidates = columnCandidates(level, column, reference, previous.getY(),
            columnWheels.get(index), knownWheels);
        if (candidates.isEmpty()) {
            failure[0] = KEY_OVERLAP;           // 该列没有可用落点(被既有轮占位或与其间距不足)
            return false;
        }
        for (BlockPos candidate : candidates) {
            counter[0]++;
            if (counter[0] > maxNodes) {
                failure[0] = KEY_LIMIT;
                return false;
            }
            int added = linkAddedSlots(level, previous, candidate);
            String key = linkKey(level, previous, candidate, assignedPrevious, added);
            if (key != null) {
                failure[0] = key;
                continue;
            }
            if (runsOverExisting(level, previous, candidate, knownWheels)) {
                failure[0] = KEY_OVERLAP;
                continue;
            }
            boolean isNew = !ChainRules.isConveyor(level, candidate);
            int nextCount = newCount + (isNew ? 1 : 0);
            if (nextCount > maxConveyors) {
                failure[0] = KEY_LIMIT;
                continue;
            }
            chosen.add(candidate);
            if (solveColumn(level, columns, columnWheels, knownWheels, referenceY, end, index + 1, candidate,
                previous, added, nextCount, chosen, counter, maxNodes, maxConveyors, failure))
                return true;
            chosen.remove(chosen.size() - 1);
        }
        return false;
    }

    /**
     * 选区尚未结束时某一列的参考高度: 以上一个已确定节点为基准, 按"起点到上一列"的方向按水平进度外推,
     * 并夹在 {@link #SEMI_REFERENCE_WINDOW} 之内。
     *
     * <p>该值只用于给候选排序, 候选窗口本身以上一个节点的高度为中心(见 {@link #columnCandidates}),
     * 因此参考高度不会决定某一列有没有解。第一列(没有更早的节点)直接沿用起点高度。</p>
     */
    private static int referenceHeight(BlockPos previous, BlockPos prevPrevious, Column column) {
        int base = previous.getY();
        if (prevPrevious == null)
            return base;
        double span = horizontalDistance(prevPrevious.getX(), prevPrevious.getZ(), previous.getX(), previous.getZ());
        if (span < 1.0e-3)
            return base;
        double reach = horizontalDistance(previous.getX(), previous.getZ(), column.x(), column.z());
        double trend = (previous.getY() - prevPrevious.getY()) / span;
        int extrapolated = (int) Math.round(base + trend * reach);
        return Math.max(base - SEMI_REFERENCE_WINDOW, Math.min(base + SEMI_REFERENCE_WINDOW, extrapolated));
    }

    /**
     * 某一列的 Y 候选: 该列中可复用的既有传动轮, 随后是以上一个已确定节点高度为中心的竖直邻域。
     *
     * <p>候选窗口以 {@code anchorY}(上一个已确定节点的高度)为中心, 因此"与上一个节点等高"始终是候选,
     * 只要该 XZ 与上一个节点的水平距离本身允许连接, 这一列就不会因为别的高度而判成坡度超限。</p>
     *
     * <p>两组候选都先按与参考高度的接近程度排序, 同等接近时再按与玩家点击高度的接近程度排序 —— 点击
     * 高度因此只是"同等合法时的偏好", 不参与参考高度的计算。新建落点会跳过与任一既有轮水平距离不足
     * {@link #MIN_EXISTING_SPACING} 的位置(判据含同列), 因此不会贴着既有轮再叠一台; 若某一列因此没有
     * 候选, 整单判非法并由玩家重选该点。</p>
     *
     * @param reference   该列的参考高度(终点已知时取起终点插值, 否则由上一个节点外推), 只用于排序;
     * @param anchorY     上一个已确定节点的高度, 候选窗口以它为中心;
     * @param existing    该列已有的传动轮, 作为复用候选;
     * @param knownWheels 各列既有轮与端点的集合, 作为新建落点的间距参照。
     */
    private static List<BlockPos> columnCandidates(Level level, Column column, int reference, int anchorY,
                                                   Set<BlockPos> existing, Set<BlockPos> knownWheels) {
        Comparator<BlockPos> order = Comparator
            .comparingInt((BlockPos pos) -> Math.abs(pos.getY() - reference))
            .thenComparingInt(pos -> Math.abs(pos.getY() - column.clickedY()));
        Set<BlockPos> candidates = new LinkedHashSet<>();
        List<BlockPos> ordered = new ArrayList<>(existing);
        ordered.sort(order);
        for (BlockPos pos : ordered) {
            if (candidates.size() >= SEMI_MAX_CANDIDATES)
                break;
            if (ChainConnector.usedConnections(level, pos) < ChainRules.maxConnections())
                candidates.add(pos);
        }
        List<BlockPos> fresh = new ArrayList<>();
        for (int delta : SEMI_VERTICAL_DELTAS) {
            int y = anchorY + delta;
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight())
                continue;
            BlockPos pos = new BlockPos(column.x(), y, column.z());
            if (!level.isLoaded(pos))
                continue;                   // 未加载区块不参与规划, 避免触发区块加载
            if (isTooCloseToExisting(pos, knownWheels))
                continue;                   // 新建落点不与既有轮过近(同列或水平间距不足)
            fresh.add(pos);
        }
        fresh.sort(order);
        for (BlockPos pos : fresh) {
            if (candidates.size() >= SEMI_MAX_CANDIDATES)
                break;
            candidates.add(pos);
        }
        return new ArrayList<>(candidates);
    }

    /** 该列全部既有传动轮(按世界建筑高度逐格判定, 未加载区块由判定方法直接排除)。 */
    private static List<BlockPos> conveyorsInColumn(Level level, Column column) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
            cursor.set(column.x(), y, column.z());
            if (ChainRules.isConveyor(level, cursor))
                found.add(cursor.immutable());
        }
        return found;
    }

    /** 没有中间点时, 整条路径就是起点到终点的一段; 该段同样不得压过其它既有轮。 */
    private static Plan linkOnly(Level level, BlockPos start, BlockPos end) {
        int added = linkAddedSlots(level, start, end);
        String key = linkKey(level, start, end, 0, added);
        if (key != null)
            return invalid(key, 1);
        Set<BlockPos> known = new HashSet<>();
        known.add(start);
        known.add(end);
        if (runsOverExisting(level, start, end, known))
            return invalid(KEY_OVERLAP, 1);
        return buildFromPath(level, List.of(start, end), WrenchConfig.chainSearchMaxConveyors(), 1);
    }

    // ------------------------------------------------------------------ 公共判定与结果组装

    /**
     * 当前节点能否与终点直接成一段(规格要求的硬性化简判定)。
     *
     * <p>判定条件为 {@link #linkKey} 返回 {@code null}, 即 Create 的距离上限、距离下限、非垂直、坡度四项
     * 几何判定, 加上两端既有的连接余量与本计划已占槽位全部通过。标准模式不要求同轴; 直线模式要求该段在
     * 水平面内与终点同轴, 否则该模式会出现斜向连接。</p>
     *
     * @param axisConstrained 为真时要求水平面内与终点同轴(直线模式)。
     */
    private static boolean canLinkDirectly(Level level, SearchNode from, BlockPos end, boolean axisConstrained) {
        if (axisConstrained && from.pos.getX() != end.getX() && from.pos.getZ() != end.getZ())
            return false;
        int added = linkAddedSlots(level, from.pos, end);
        return linkKey(level, from.pos, end, from.assignedLinks, added) == null;
    }

    /**
     * 当前轮与终点可直接成段时, 是否仍有可零新增轮成本接入的既有轮。
     *
     * <p>存在这样的既有轮时不走直连捷径, 允许把本可直连的一段拆开以多复用一台。判定用到的候选来自走廊
     * 带宽内的检索集合, 并额外对「当前轮 到 终点」这一段补一次段中点检索后合并, 避免走廊采样截断造成的
     * 漏收; 接入候选不新增传动轮, 因此拆开既不会把路线拉到带外, 也不会增加新增轮数量;
     * 已经在本路径上的候选不再计入。与当前轮或终点同列的既有轮(该段只能是垂直连接)同样不计入:
     * 前者接不上, 后者接不到终点, 让它们推迟直连只会把路径引向死路。</p>
     */
    private static boolean hasReusableDetour(Level level, SearchNode from, BlockPos end,
                                             boolean axisAligned, Set<BlockPos> reuseSet) {
        Set<BlockPos> known = new HashSet<>(reuseSet);
        known.addAll(conveyorsNearSegment(level, from.pos, end));
        if (known.isEmpty())
            return false;
        int dx = end.getX() - from.pos.getX();
        int dz = end.getZ() - from.pos.getZ();
        Set<BlockPos> visited = new HashSet<>();
        for (SearchNode node = from; node != null; node = node.parent)
            visited.add(node.pos);

        for (BlockPos candidate : known) {
            if (candidate.equals(from.pos) || candidate.equals(end) || visited.contains(candidate)
                || sharesColumn(candidate, from.pos) || sharesColumn(candidate, end))
                continue;                       // 同列竖直叠放无法连接, 不计入可顺路接入的候选
            if (axisAligned && !isStraightReuse(from.pos, candidate, dx, dz))
                continue;
            int added = linkAddedSlots(level, from.pos, candidate);
            if (linkKey(level, from.pos, candidate, from.assignedLinks, added) == null)
                return true;
        }
        return false;
    }

    /**
     * 新连接线段是否压过既有传动轮。
     *
     * <p>先查已知集合 {@code known}(走廊带内的复用候选, 或半自动各列的既有轮), 再以段中点为圆心、
     * 半径取跨度一半补一次 {@link ChainScanner#conveyorsNear}, 覆盖采样截断或列外漏收的既有轮;
     * 只要存在既不是线段两端、且到线段的水平距离小于 {@link #MIN_EXISTING_SPACING} 的既有轮, 即判为压过。</p>
     *
     * <p>该判定是守门: 新链不覆盖既有轮, 而是改走该轮(复用)或让玩家调整选点; 线段两端自身不计入,
     * 与端点同列的既有轮(竖直叠放)同样不计入 —— 新链只在端点处接入该列, 并不会与它叠在一起。</p>
     */
    private static boolean runsOverExisting(Level level, BlockPos a, BlockPos b, Set<BlockPos> known) {
        if (runsOverKnown(a, b, known))
            return true;
        for (BlockPos pos : conveyorsNearSegment(level, a, b)) {
            if (pos.equals(a) || pos.equals(b))
                continue;
            if (sharesColumn(pos, a) || sharesColumn(pos, b))
                continue;
            if (horizontalDistanceToSegment(pos, a, b) < MIN_EXISTING_SPACING)
                return true;
        }
        return false;
    }

    /**
     * 只用已知集合判定线段是否压过既有轮, 不扫描世界。
     *
     * <p>用于 A* 后继循环这类高频判定点: 单次展开要对几十个候选各判一次, 逐候选扫描会把单次规划的开销
     * 放大到不可接受; 已知集合即本次规划沿路线带收集到的既有轮, 已覆盖走廊及其近旁。</p>
     *
     * <p>与线段端点同列(同 X 同 Z、高度不同)的既有轮不计入: 它只是在端点所在竖直柱上叠放, 新链在该段
     * 并不与它重叠; 否则起点或终点上下方有轮时会误判整条路线「压过既有轮」而放弃本可铺设的路径。</p>
     */
    private static boolean runsOverKnown(BlockPos a, BlockPos b, Set<BlockPos> known) {
        for (BlockPos pos : known) {
            if (pos.equals(a) || pos.equals(b))
                continue;
            if (sharesColumn(pos, a) || sharesColumn(pos, b))
                continue;
            if (horizontalDistanceToSegment(pos, a, b) < MIN_EXISTING_SPACING)
                return true;
        }
        return false;
    }

    /** 以段中点为圆心、半径取跨度一半检索既有轮, 用于补齐已知集合之外的部分。 */
    private static List<BlockPos> conveyorsNearSegment(Level level, BlockPos a, BlockPos b) {
        BlockPos middle = BlockPos.containing(
            (a.getX() + b.getX()) / 2.0,
            (a.getY() + b.getY()) / 2.0,
            (a.getZ() + b.getZ()) / 2.0);
        double span = Vec3.atCenterOf(a).distanceTo(Vec3.atCenterOf(b));
        int radius = Math.max(1, (int) Math.ceil(span / 2.0));
        return ChainScanner.conveyorsNear(level, middle, radius);
    }

    /** 点到线段在水平面内的投影距离(忽略高度), 用于判定线段是否压过既有轮。 */
    private static double horizontalDistanceToSegment(BlockPos pos, BlockPos a, BlockPos b) {
        double ax = a.getX();
        double az = a.getZ();
        double vx = b.getX() - ax;
        double vz = b.getZ() - az;
        double lengthSqr = vx * vx + vz * vz;
        double px = pos.getX() - ax;
        double pz = pos.getZ() - az;
        if (lengthSqr < 1.0e-6)
            return Math.sqrt(px * px + pz * pz);
        double ratio = Math.max(0.0, Math.min(1.0, (px * vx + pz * vz) / lengthSqr));
        double dx = px - vx * ratio;
        double dz = pz - vz * ratio;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 两个坐标是否处于同一竖直柱(同 X 同 Z, 与高度无关), 用于识别上下叠放的传动轮。 */
    private static boolean sharesColumn(BlockPos a, BlockPos b) {
        return a.getX() == b.getX() && a.getZ() == b.getZ();
    }

    /**
     * 新建落点是否与既有传动轮过近。
     *
     * <p>判据为水平面内同列(同 X 同 Z, 与高度无关)或水平距离小于 {@link #MIN_EXISTING_SPACING};
     * 同列是距离判据在水平距离为 0 时的特例, 该判据用于让绕行落点离开既有轮的柱位与近旁, 而不是贴着
     * 既有链再叠一层。参照集合为本次规划沿路线带收集到的既有轮(与复用候选同一集合), 判定因此不额外
     * 扫描世界; 该判定只作用于新建落点, 复用一台既有轮本身不受影响。</p>
     */
    private static boolean isTooCloseToExisting(BlockPos candidate, Set<BlockPos> reuseSet) {
        double limitSqr = MIN_EXISTING_SPACING * MIN_EXISTING_SPACING;
        for (BlockPos existing : reuseSet) {
            double dx = candidate.getX() - existing.getX();
            double dz = candidate.getZ() - existing.getZ();
            if (dx * dx + dz * dz < limitSqr)
                return true;
        }
        return false;
    }

    /**
     * 判断一段连接在规划语境下是否可用, 可用时返回 {@code null}。
     *
     * <p>几何与两端静态余量取自 {@link ChainRules#violationKey}: {@code too_far}、{@code too_close}、
     * {@code cannot_connect_vertically}、{@code too_steep}、{@code cannot_add_more_connections}。
     * 该判定通过后, 再按本计划在该节点已占用的槽位做累计复核, 覆盖「同一计划为同一台既有传动轮
     * 新增多条连接」的情形。</p>
     *
     * @param assignedAtA a 节点在本计划内已占用的新增连接槽位数量。
     * @param added       本段连接为两端新增的槽位数量(既有连接为 0)。
     */
    private static String linkKey(Level level, BlockPos a, BlockPos b, int assignedAtA, int added) {
        String key = ChainRules.violationKey(level, a, b);
        if (key != null)
            return key;
        if (!fitsCapacity(level, a, assignedAtA, added) || !fitsCapacity(level, b, 0, added))
            return "cannot_add_more_connections";
        return null;
    }

    /**
     * 本段连接为两端各新增的槽位数量: 两端之间已有连接时为 0, 否则为 1。
     *
     * <p>连接是否已存在经传动轮方块实体自身的连接集合判定, 与 {@link ChainConnector} 使用同一数据源;
     * 客户端侧该集合由 Create 的方块实体同步提供, 与 {@link ChainRules} 的容量判定一致。</p>
     */
    private static int linkAddedSlots(Level level, BlockPos a, BlockPos b) {
        if (hasConnection(level, a, b) || hasConnection(level, b, a))
            return 0;
        return 1;
    }

    /** 传动轮 a 是否已记录到 b 的连接。 */
    private static boolean hasConnection(Level level, BlockPos a, BlockPos b) {
        if (!level.isLoaded(a) || !level.isLoaded(b))
            return false;
        return level.getBlockEntity(a) instanceof ChainConveyorBlockEntity conveyor
            && conveyor.connections.contains(b.subtract(a));
    }

    /** 节点容量: 尚未放置的落点没有既有连接, 只需满足本计划累计不超过上限。 */
    private static boolean fitsCapacity(Level level, BlockPos pos, int assignedLinks, int added) {
        if (!ChainRules.isConveyor(level, pos))
            return true;
        return ChainConnector.usedConnections(level, pos) + assignedLinks + added
            <= ChainRules.maxConnections();
    }

    /**
     * 该路径"全部链段长度之和"是否超过配置上限 {@code chain.max_total_length}。
     *
     * <p>长度口径与服务端共用 {@link ChainConnectServer#totalChainLength}（相邻两轮角点欧氏距离之和），
     * 上限值共用 {@link WrenchConfig#chainMaxTotalLength()}，因此客户端预览与服务端执行判定一致。
     * 与单段 32 格上限、每台 4 连接上限互不冲突：那两项逐段/逐台判定，本项是整条路径的合计。</p>
     */
    private static boolean exceedsTotalLength(List<BlockPos> path) {
        return ChainConnectServer.totalChainLength(path) > WrenchConfig.chainMaxTotalLength();
    }

    /** 把起点到终点的坐标序列组装为结果, 并统计新增传动轮与锁链消耗。 */
    private static Plan buildFromPath(Level level, List<BlockPos> path, int maxConveyors, int visited) {
        if (path.size() < 2)
            return invalid(KEY_LIMIT, visited);

        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos pos : path)
            if (!ChainRules.isConveyor(level, pos))
                placed.add(pos);
        if (placed.size() > maxConveyors)
            return invalid(KEY_LIMIT, visited);

        List<Link> links = new ArrayList<>();
        int chainCost = 0;
        for (int i = 1; i < path.size(); i++) {
            links.add(new Link(path.get(i - 1), path.get(i)));
            chainCost += ChainConnector.chainCost(path.get(i - 1), path.get(i));
        }

        // 总链条长度上限: 规划期即拒绝(预览红框 + 提示), 服务端会按同一口径独立复验。
        if (exceedsTotalLength(path))
            return invalid(KEY_TOTAL_TOO_LONG, visited);

        return new Plan(List.copyOf(path), List.copyOf(placed), List.copyOf(links), true, null,
            placed.size(), chainCost, visited);
    }

    private static Plan invalid(String violationKey, int visited) {
        return new Plan(List.of(), List.of(), List.of(), false, violationKey, 0, 0, visited);
    }

    private static double horizontalDistance(int ax, int az, int bx, int bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static List<BlockPos> reconstruct(SearchNode reached) {
        List<BlockPos> path = new ArrayList<>();
        for (SearchNode node = reached; node != null; node = node.parent)
            path.add(node.pos);
        Collections.reverse(path);
        return path;
    }

    /**
     * 剩余直线距离至少还需要多少台新增传动轮。
     *
     * <p>一台新增传动轮把路径分成一段, 每段长度不超过单段上限, 因此 {@code k} 台新增轮最多覆盖
     * {@code (k + 1)} 段, 即剩余距离不超过 {@code (k + 1) * 上限}; 反之所需新增轮不少于
     * {@code ceil(剩余距离 / 上限) - 1}。该下界不会高估真实所需, 可安全用于 A* 的主序启发。</p>
     */
    private static int minAdditionalConveyors(double distance, int maxDistance) {
        if (maxDistance <= 0)
            return 0;
        return Math.max(0, (int) Math.ceil(distance / maxDistance) - 1);
    }

    /**
     * A* 的搜索节点。
     *
     * <p>{@code priorityCount} 为「已用新增轮 + 剩余最少新增轮」, 是队列第一键; {@code reusedWheels}
     * 为已复用的既有轮数量(多者优先), 是队列第二键; {@code priorityLength} 为「已走链长 + 剩余直线距离」,
     * 是队列第三键; {@code heuristic} 为剩余直线距离, 用作收敛用的第四键。新增轮启发不高于真实剩余新增轮数,
     * 满足可采纳性; 复用台数没有可采纳的上界启发, 按贪心次序处理。{@code lastVertical} 记录上一段的竖直位移,
     * 供 {@code roughness} 计算相邻两段的竖直位移变化量。</p>
     */
    private static final class SearchNode {

        private final BlockPos pos;
        private final double chainLength;
        private final int newConveyors;
        private final int reusedWheels;
        private final double roughness;
        private final SearchNode parent;
        private final int lastVertical;
        private final int assignedLinks;
        private final double heuristic;
        private final int priorityCount;
        private final double priorityLength;

        private SearchNode(BlockPos pos, double chainLength, int newConveyors, int reusedWheels,
                           double roughness, SearchNode parent, BlockPos goal, int lastVertical,
                           int assignedLinks, int maxDistance) {
            this.pos = pos;
            this.chainLength = chainLength;
            this.newConveyors = newConveyors;
            this.reusedWheels = reusedWheels;
            this.roughness = roughness;
            this.parent = parent;
            this.lastVertical = lastVertical;
            this.assignedLinks = assignedLinks;
            this.heuristic = Vec3.atCenterOf(pos).distanceTo(Vec3.atCenterOf(goal));
            this.priorityCount = newConveyors + minAdditionalConveyors(this.heuristic, maxDistance);
            this.priorityLength = chainLength + this.heuristic;
        }

        private boolean betterThan(SearchNode other) {
            if (newConveyors != other.newConveyors)
                return newConveyors < other.newConveyors;
            if (reusedWheels != other.reusedWheels)
                return reusedWheels > other.reusedWheels;
            if (priorityLength != other.priorityLength)
                return priorityLength < other.priorityLength;
            if (heuristic != other.heuristic)
                return heuristic < other.heuristic;
            if (roughness != other.roughness)
                return roughness < other.roughness;
            if (assignedLinks != other.assignedLinks)
                return assignedLinks < other.assignedLinks;
            return pos.compareTo(other.pos) < 0;
        }
    }
}
