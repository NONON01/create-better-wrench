package com.nonono.createbetterwrench.chain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.config.WrenchConfig;
import com.nonono.createbetterwrench.mode.ChainSubMode;
import com.nonono.createbetterwrench.permission.WrenchPermissions;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * 「锁链传动」的服务端执行层: 放置传动轮、建立连接、扣除材料(Forge 1.20.1 版)。规格见
 * {@code docs/design/chain-mode-spec.md} 第 5 节与第 7 节; 与 1.21.1 线逐条对齐。
 *
 * <p><b>平台差异(仅这一处)</b>: 放置方块的保护事件改用 Forge 的
 * {@link ForgeEventFactory#onBlockPlace}, 其 {@code BlockSnapshot#restore()} 不带参数;
 * Create 1.20.1 的注册表取物品为 {@code AllBlocks.CHAIN_CONVEYOR.get().asItem()},
 * 本模组物品为 {@code BetterWrenchMod.BETTER_WRENCH.get()}。校验顺序、阈值与语义与 1.21.1 线一致。</p>
 *
 * <p>客户端传值(<b>子模式、路径、forced</b>)一律不信任: 服务端对每一次请求重做全部校验,
 * 客户端预览结果只当"玩家的意图"。校验与执行的顺序固定为:</p>
 * <ol>
 *   <li>功能开关: {@code chain.enabled} 关闭即拒绝(提示 {@code msg.<modid>.feature_disabled});</li>
 *   <li>路径规模: 至少 2 台, 且不超过 {@code chain.search_max_conveyors} / {@code chain.search_max_nodes};</li>
 *   <li><b>路径必须是简单路径</b>: 同一坐标出现两次即拒绝(见 {@link #hasDuplicatePositions});</li>
 *   <li>总链条长度: 全部链段长度之和不超过 {@code chain.max_total_length}(见 {@link #totalChainLength});</li>
 *   <li>资格: 旁观者/无建造权限者拒绝(冒险模式由权限层提示), 且主手必须持本模组扳手;</li>
 *   <li>所有坐标区块已加载(不触发区块生成);</li>
 *   <li>目标格: 已是锁链传动轮, 或可被替换(空气/草/水等), 绝不覆盖其它方块;</li>
 *   <li>起点与终点必须<b>已经</b>是锁链传动轮(只铺设路径中间的传动轮, 不转换其它方块);</li>
 *   <li>建造保护: 每一格都要过 {@code mayInteract}(出生点保护/世界边界/领地);</li>
 *   <li>逐段几何复验({@link ChainRules#violationKey}), 直线子模式额外要求相邻两轮在水平面内同轴; 这一步在
 *       <b>放置之前</b>完成, 因此非法路径不会产生任何瞬时改动;</li>
 *   <li>逐段连接容量: 两端已存在传动轮的连接数必须小于 {@link ChainRules#maxConnections()},
 *       并把<b>本次请求内</b>计划新建的连接累计进去(同一台轮在路径中可能出现两次);</li>
 *   <li>普通放置的材料一次性算清, 不足则<b>世界零变化</b>只回 actionbar;</li>
 *   <li>落块(普通模式原子: 任一格被保护事件拦下则整体还原)、建立连接、按实际新建的连接扣锁链;</li>
 *   <li>清理本次触碰到的传动轮上"反向记录缺失"的历史残留(见 {@link #cleanupOrphans})。</li>
 * </ol>
 *
 * <p>连接本身是幂等的: {@link ChainConnector#connect} 只补齐缺失的一侧, 已连接的一对视为"复用",
 * 既不重复建链也不扣锁链。锁链扣减由本类按 {@link ChainConnector#chainCost} 完成。</p>
 *
 * <p><b>「已双向连接的一对」的硬保证</b>: Create 的 {@code connections} 是
 * {@code Set<BlockPos>}(存相对坐标偏移), 同一个方块实体上不可能出现两条指向同一目标的记录;
 * 本类另有<b>三道闸</b>确保重复请求不会叠加第二条链、不会重复扣料: ① {@link #preflight} 用相对坐标
 * ({@code to.subtract(from)}, 与 Create 存储口径一致)判定"双向都在"并记为复用;
 * ② {@link #link} 入口再判一次, 双向都在就直接返回复用, 连 Create 都不调用;
 * ③ 扣料只按"实际新增的连接数"计({@code link} 返回值 1), 复用/失败一律不扣。</p>
 *
 * <p><b>连接前必须先让 Create 的传动轮统计就绪</b>:
 * {@code ChainConveyorBlockEntity.connectionStats} <b>没有字段初值</b>, 只在 {@code prepareStats()}
 * (由 {@code tick}/{@code lazyTick} 触发)里创建; 而 {@code addConnectionTo} 会直接调用
 * {@code calculateConnectionStats} 写这个 map。于是"在放下传动轮的同一刻就建连接"必然抛
 * {@code NullPointerException}(1.21.1 内测日志已实证, Create 6.0.8 的该类实现相同), 表现为
 * "轮放好了却没连上, 要再右键一次"。现在 {@link #link} 会在 {@link ChainConnector#connect} 之前
 * 先对两端调用 {@link ChainConveyorBlockEntity#prepareStats()}, 一次请求内即可完成放轮 + 连接;
 * 连接仍失败时也不再静默, 而是回滚并给出 actionbar。</p>
 *
 * <p>强制放置({@code forced=true}, Shift+右键)按材料实际数量<b>分两阶段</b>: 先从起点尽量铺传动轮,
 * 再按锁链数量建立连接; 缺锁链时只补传动轮(不建立连接), 缺传动轮则停止后续放置,
 * 两者都没有只回「无材料」; 已达连接上限的节点跳过该连接并继续, 被跳过的连接不扣锁链。
 * 创造模式全程免材料。</p>
 */
public final class ChainConnectServer {

    /** 本模组的 actionbar 文案前缀; 逐段违规复用的是 Create 自己的 {@code create.chain_conveyor.*}。 */
    private static final String KEY = "hint." + BetterWrenchMod.MODID + ".chain.";

    /** Create 自身语言键前缀(逐段违规直接复用, 不新增译文)。 */
    private static final String CREATE_KEY = "create.chain_conveyor.";

    /** {@link ChainRules#violationKey} 表示"连接数已满"时的返回值。 */
    private static final String CAPACITY_VIOLATION = "cannot_add_more_connections";

    private ChainConnectServer() {
    }

    /**
     * 一条路径全部链段的几何总长(格)。
     *
     * <p>度量与 Create 的 {@code ChainConveyorBlockEntity#getChainCost} 一致: 相邻两轮<b>角点</b>欧氏距离
     * (即 {@code BlockPos#distSqr} 的平方根), 逐段累加。单段长度另由 Create 的 32 格上限约束,
     * 本方法只负责"加起来有多长"。</p>
     *
     * <p>客户端规划器与服务端执行处<b>共用这一个方法</b>, 因此 {@code chain.max_total_length} 的判定口径
     * 两端一致。放在本类是因为它属于通用包({@code client/} 可以引用, 反之不行), 服务端与客户端都能调用;
     * 若日后有更合适的位置(例如 {@code chain/ChainRules}), 可以整体平移而不改口径。</p>
     */
    public static double totalChainLength(List<BlockPos> path) {
        double total = 0.0;
        for (int i = 1; i < path.size(); i++)
            total += Math.sqrt(path.get(i - 1).distSqr(path.get(i)));
        return total;
    }

    /** 客户端请求入口(由 {@code ChainConnectPayload#handle} 在服务端主线程调用)。 */
    public static void handle(ServerPlayer player, ChainSubMode sub, List<BlockPos> path, boolean forced) {
        if (player == null || path == null)
            return;
        ChainSubMode mode = sub == null ? ChainSubMode.STANDARD : sub;
        int n = path.size();
        if (n < 2)
            return;                                     // 少于两台: 没有可建立的连接, 静默忽略改包请求

        // ⓪ 功能开关: 配置里关掉「锁链传动」后, 任何请求一律拒绝(改包客户端也绕不过),
        //    提示文案与其它功能共用 msg.<modid>.feature_disabled
        if (!WrenchConfig.chainEnabled()) {
            hint(player, "msg." + BetterWrenchMod.MODID + ".feature_disabled");
            return;
        }

        // ① 路径规模: 服务端按自己的配置判定(解码层只有固定硬闸)
        if (n > WrenchConfig.chainSearchMaxConveyors() || n > WrenchConfig.chainSearchMaxNodes()) {
            hint(player, KEY + "violation.limit");
            return;
        }

        // ①b 路径必须是简单路径: 同一坐标出现两次时, 该格会被重复落块、材料会被重复计算,
        //     同一对连接也会在预检里被算两次 —— 一条正常路径不会重复经过同一台轮,
        //     因此直接拒绝(改包客户端可构造这种路径)。
        if (hasDuplicatePositions(path)) {
            hint(player, KEY + "invalid");
            return;
        }

        // ①c 总链条长度上限: 一次铺设全部链段长度之和(与客户端规划期同一口径, 见 totalChainLength)。
        //     纯几何、不读世界, 因此放在权限校验之前即可拦下"连几百格"的请求; 创造模式不豁免。
        if (totalChainLength(path) > WrenchConfig.chainMaxTotalLength()) {
            hint(player, KEY + "total_too_long");
            return;
        }

        // ② 资格与主手持扳手(自 2026-09-20 起副手的扳手不参与模式功能)
        if (WrenchPermissions.rejectIfCannotBuild(player))
            return;
        if (!player.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH.get()))
            return;

        ServerLevel level = (ServerLevel) player.level();

        // ③ 所有坐标区块必须已加载
        for (BlockPos p : path)
            if (!level.isLoaded(p)) {
                hint(player, KEY + "unloaded");
                return;
            }

        // ④ 目标格: 已是传动轮 或 可被替换; 其它方块一律不覆盖、不改变世界
        boolean[] conveyor = new boolean[n];
        for (int i = 0; i < n; i++) {
            BlockPos p = path.get(i);
            conveyor[i] = ChainRules.isConveyor(level, p);
            if (!conveyor[i] && !level.getBlockState(p).canBeReplaced()) {
                hint(player, KEY + "blocked");
                return;
            }
        }

        // ④b 起点与终点必须已经是传动轮(规格 §3.2/§4.1): 本模式只铺设路径中间的传动轮,
        //     绝不把其它方块转换为传动轮
        if (!conveyor[0]) {
            hint(player, KEY + "need_start");
            return;
        }
        if (!conveyor[n - 1]) {
            hint(player, KEY + "need_end");
            return;
        }

        // ⑤ 每一格都要有建造权限(出生点/领地/世界边界)
        for (BlockPos p : path)
            if (!level.mayInteract(player, p)) {
                hint(player, KEY + "protected");
                return;
            }

        if (forced)
            runForced(player, level, mode, path, conveyor);
        else
            runNormal(player, level, mode, path, conveyor);
    }

    // ------------------------------------------------------------------ 普通放置

    /** 普通放置: 校验全部通过且材料足量才动世界; 材料不足时只回 actionbar。 */
    private static void runNormal(ServerPlayer player, ServerLevel level, ChainSubMode mode,
                                  List<BlockPos> path, boolean[] conveyor) {
        int n = path.size();
        boolean creative = player.isCreative();
        int[] used = new int[n];
        SegmentPlan[] plans = new SegmentPlan[n - 1];

        // ⑥⑦ 放置前的完整复验(几何 + 直线子模式 + 容量): 不合法则世界零变化
        String reasonKey = preflight(level, mode, path, conveyor, used, plans, false);
        if (reasonKey != null) {
            hint(player, reasonKey);
            return;
        }

        int needConveyors = 0;
        for (int i = 0; i < n; i++)
            if (!conveyor[i])
                needConveyors++;
        int needChains = 0;
        for (SegmentPlan plan : plans)
            if (plan != null && plan.connect)
                needChains += plan.cost;

        // ⑧ 材料一次性算清: 不足则不发任何变化, 只回 actionbar
        if (!creative) {
            int missingConveyors = needConveyors - ChainMaterials.countConveyors(player);
            int missingChains = needChains - ChainMaterials.countChains(player);
            if (missingConveyors > 0 || missingChains > 0) {
                hint(player, KEY + "materials_missing",
                    Math.max(0, missingConveyors), Math.max(0, missingChains));
                return;
            }
        }

        // ⑨ 落块: 全部先放好; 任一格被保护事件拦下就整体还原, 世界不留半截
        List<BlockSnapshot> undo = new ArrayList<>();
        int placed = 0;
        for (int i = 0; i < n; i++) {
            if (conveyor[i])
                continue;
            PlaceOutcome outcome = placeConveyor(level, path.get(i), player, undo);
            if (outcome == PlaceOutcome.OK) {
                conveyor[i] = true;
                placed++;
            } else {
                revertAll(undo);
                hint(player, outcome == PlaceOutcome.DENIED ? KEY + "protected" : KEY + "blocked");
                return;
            }
        }

        // ⑩ 建立连接: 复验已通过, 这里只按计划新建; 实际新建的才扣锁链
        int linked = 0;
        int chainsUsed = 0;
        List<BlockPos[]> created = new ArrayList<>();
        for (int i = 0; i + 1 < n; i++) {
            SegmentPlan plan = plans[i];
            if (plan == null || !plan.connect)
                continue;
            BlockPos a = path.get(i);
            BlockPos b = path.get(i + 1);
            int outcome = link(level, a, b);
            if (outcome == 1) {
                created.add(new BlockPos[] { a, b });
                linked++;
                chainsUsed += plan.cost;
            } else if (outcome < 0) {
                // 连接失败: 逆序撤销本次已建连接并还原已放方块, 世界回到请求前; 但仍给玩家可见反馈
                rollback(level, created, undo);
                hint(player, KEY + "connect_failed");
                return;
            }
        }

        consume(player, placed, chainsUsed, creative);
        cleanupOrphans(level, path, conveyor);
        if (placed > 0)
            playPlaceSound(level, path.get(0));
        hint(player, KEY + "done");
    }

    // ------------------------------------------------------------------ 强制放置

    /**
     * 强制放置(Shift+右键): 按材料实际数量<b>分两阶段</b> —— 先从起点尽量铺传动轮, 再按锁链数量建立连接;
     * 缺锁链时只补传动轮(不建立连接), 已放部分保留, 可再次执行补全。
     */
    private static void runForced(ServerPlayer player, ServerLevel level, ChainSubMode mode,
                                  List<BlockPos> path, boolean[] conveyor) {
        int n = path.size();
        boolean creative = player.isCreative();
        int[] used = new int[n];
        SegmentPlan[] plans = new SegmentPlan[n - 1];

        // ⑥⑦ 几何非法仍然拒绝(规格 §8.2: 非法路径不产生任何变化); 容量已满改为"跳过"
        String reasonKey = preflight(level, mode, path, conveyor, used, plans, true);
        if (reasonKey != null) {
            hint(player, reasonKey);
            return;
        }

        int needConveyors = 0;
        for (int i = 0; i < n; i++)
            if (!conveyor[i])
                needConveyors++;
        int needChains = 0;
        boolean skipped = false;
        for (SegmentPlan plan : plans) {
            if (plan == null)
                continue;
            if (plan.connect)
                needChains += plan.cost;
            if (plan.skipped)
                skipped = true;
        }

        if (needConveyors == 0 && needChains == 0) {
            hint(player, KEY + (skipped ? "partial" : "done"));
            return;
        }

        int availableConveyors = creative ? Integer.MAX_VALUE : ChainMaterials.countConveyors(player);
        int availableChains = creative ? Integer.MAX_VALUE : ChainMaterials.countChains(player);
        if (availableConveyors <= 0 && availableChains <= 0) {
            hint(player, KEY + "no_materials");         // 两者都没有
            return;
        }

        boolean incomplete = skipped;

        // 第一阶段: 从起点开始尽量放置; 缺传动轮后停止后续放置(已存在的传动轮仍可复用)
        boolean canPlace = creative || availableConveyors > 0;
        List<BlockSnapshot> undo = new ArrayList<>();   // 强制放置不回滚: 已放部分保留, 可再次补全
        int placed = 0;
        for (int i = 0; i < n; i++) {
            if (conveyor[i])
                continue;
            if (!canPlace) {
                incomplete = true;
                continue;
            }
            PlaceOutcome outcome = placeConveyor(level, path.get(i), player, undo);
            if (outcome == PlaceOutcome.OK) {
                conveyor[i] = true;
                placed++;
                if (!creative && --availableConveyors <= 0)
                    canPlace = false;
            } else {
                incomplete = true;                      // 该格被拦下: 跳过, 继续下一个位置
            }
        }

        // 第二阶段: 建立连接。两端都已是传动轮才可能; 缺锁链只放轮不连; 满节点跳过且不扣锁链。
        // 连接一律从起点连续进行: 一旦锁链不够, 后续连接全部放弃(不跳过一段去连更便宜的后段,
        // 否则会留下断开的链)。
        int linked = 0;
        int chainsUsed = 0;
        boolean chainExhausted = false;
        for (int i = 0; i + 1 < n; i++) {
            SegmentPlan plan = plans[i];
            if (plan == null || !plan.connect)
                continue;
            if (chainExhausted || plan.cost > availableChains) {
                chainExhausted = true;                  // 缺锁链: 保留已放传动轮, 不建立连接
                incomplete = true;
                continue;
            }
            int outcome = link(level, path.get(i), path.get(i + 1));
            if (outcome == 1) {
                linked++;
                chainsUsed += plan.cost;
                availableChains -= plan.cost;
            } else if (outcome < 0) {
                unlink(level, path.get(i), path.get(i + 1));   // 失败不留单边连接(强制模式不回滚方块)
                incomplete = true;
            }
        }

        consume(player, placed, chainsUsed, creative);
        cleanupOrphans(level, path, conveyor);
        if (placed > 0)
            playPlaceSound(level, path.get(0));
        hint(player, KEY + (incomplete ? "partial" : "done"));
    }

    // ------------------------------------------------------------------ 复验

    /** 一段路径的连接计划(由 {@link #preflight} 填写)。 */
    private static final class SegmentPlan {
        /** 已存在连接: 复用, 合法, 不占新容量也不扣锁链。 */
        boolean reuse;
        /** 需要新建连接(容量与几何都已确认), {@link #cost} 为锁链成本。 */
        boolean connect;
        /** 强制放置时因连接数已满被跳过的连接: 不建立、不扣锁链。 */
        boolean skipped;
        /** 新建该连接需要的锁链数量。 */
        int cost;
    }

    /**
     * 放置前的逐段复验, 返回首个非法的<b>完整语言键</b>(逐段几何违规直接复用
     * {@code create.chain_conveyor.<后缀>}), 全部通过返回 {@code null}。
     *
     * <p>同时填写 {@code plans}(每段要做什么)并把本次计划新建的连接累计进 {@code used},
     * 使同一台传动轮在一次请求里被连接多次时也能正确判定容量。</p>
     *
     * @param forced {@code true} 时"连接数已满"记为 {@link SegmentPlan#skipped} 而不是错误
     */
    private static String preflight(Level level, ChainSubMode mode, List<BlockPos> path, boolean[] conveyor,
                                    int[] used, SegmentPlan[] plans, boolean forced) {
        int n = path.size();
        int maxConnections = ChainRules.maxConnections();

        // 容量基数: 已存在的传动轮读实时连接数, 待放置的从 0 起算
        for (int i = 0; i < n; i++)
            used[i] = conveyor[i] ? ChainConnector.usedConnections(level, path.get(i)) : 0;

        // 直线子模式: 相邻两轮在水平面内只沿一个轴前进(允许 Y 随坡度变化)
        if (mode == ChainSubMode.STRAIGHT) {
            for (int i = 0; i + 1 < n; i++) {
                BlockPos a = path.get(i);
                BlockPos b = path.get(i + 1);
                if (a.getX() != b.getX() && a.getZ() != b.getZ())
                    return KEY + "straight_axis";
            }
        }

        for (int i = 0; i + 1 < n; i++) {
            BlockPos a = path.get(i);
            BlockPos b = path.get(i + 1);
            SegmentPlan plan = new SegmentPlan();
            plans[i] = plan;

            boolean aSide = linkedTo(level, a, b);      // a 的记录里已经有 b
            boolean bSide = linkedTo(level, b, a);      // b 的记录里已经有 a
            if (aSide && bSide) {
                plan.reuse = true;                      // 双向都在: 复用既有连接, 不扣料
                continue;
            }

            // 几何复验在放置前完成(ChainRules 不要求两端已是传动轮)
            String violation = ChainRules.violationKey(level, a, b);
            if (violation != null && !CAPACITY_VIOLATION.equals(violation))
                return createViolationKey(violation);

            // 连接容量: 只补缺失的那一侧(单边残留时另一侧已占槽位, 不重复计), 并把本次计划新建的连接累计进去
            int addA = aSide ? 0 : 1;
            int addB = bSide ? 0 : 1;
            if (used[i] + addA > maxConnections || used[i + 1] + addB > maxConnections) {
                if (!forced)
                    return CREATE_KEY + CAPACITY_VIOLATION;
                plan.skipped = true;                    // 强制放置: 跳过, 继续下一个可用位置
                continue;
            }

            used[i] += addA;
            used[i + 1] += addB;
            plan.connect = true;
            plan.cost = Math.max(0, ChainConnector.chainCost(a, b));
        }
        return null;
    }

    /**
     * 路径里是否有重复坐标。
     *
     * <p>一条正常的传动路径不会重复经过同一台轮; 出现重复说明客户端构造异常(或改包),
     * 若继续执行会让同一格重复落块/重复计料, 也会让同一对连接在预检里被算两次, 因此直接拒绝。</p>
     */
    private static boolean hasDuplicatePositions(List<BlockPos> path) {
        Set<BlockPos> seen = new HashSet<>(path.size() * 2);
        for (BlockPos pos : path)
            if (!seen.add(pos))
                return true;
        return false;
    }

    /** 该传动轮的记录里是否已有指向 {@code to} 的连接(Create 的连接按相对坐标存储)。 */
    private static boolean linkedTo(Level level, BlockPos from, BlockPos to) {
        return level.getBlockEntity(from) instanceof ChainConveyorBlockEntity be
            && be.connections.contains(to.subtract(from));
    }

    /**
     * 建立一段连接并判断是否真的新建。
     *
     * <p>调用 {@link ChainConnector#connect} 之前先对两端调用
     * {@link ChainConveyorBlockEntity#prepareStats()}: Create 的 {@code connectionStats} 没有字段初值,
     * 刚放下的传动轮在本刻还没执行过 {@code tick}/{@code lazyTick}, 直接建连接会因它为 null 抛 NPE。
     * 这一步同时把上一次异常留下的单边连接补齐。</p>
     *
     * @return {@code 1} 新建/补齐成功(需要扣锁链); {@code 0} 两端本来就有该连接(复用); {@code -1} 失败(已尽力清理)
     */
    private static int link(Level level, BlockPos a, BlockPos b) {
        // 硬保证: 两端都已记录这条连接时直接判为复用 —— 不调用 Create、不写入、不扣锁链。
        // preflight 已经先判过一次; 这里是第二道闸, 使该保证与调用方/预检状态无关。
        if (linkedTo(level, a, b) && linkedTo(level, b, a))
            return 0;
        if (!ensureStats(level, a) || !ensureStats(level, b))
            return -1;
        try {
            int before = ChainConnector.usedConnections(level, a) + ChainConnector.usedConnections(level, b);
            if (!ChainConnector.connect(level, a, b))
                return -1;
            int after = ChainConnector.usedConnections(level, a) + ChainConnector.usedConnections(level, b);
            return after > before ? 1 : 0;
        } catch (RuntimeException e) {
            // Create 内部异常: 立即撤销可能写了一半的连接, 绝不留下"只有单边"或"只有轮没有链"的状态
            BetterWrenchMod.LOGGER.error("chain: 建立连接异常 {} -> {}", a, b, e);
            unlink(level, a, b);
            return -1;
        }
    }

    /** 让传动轮的 Create 统计就绪({@link ChainConveyorBlockEntity#prepareStats()}); 方块实体缺失或初始化异常时返回 false。 */
    private static boolean ensureStats(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ChainConveyorBlockEntity be))
            return false;
        try {
            be.prepareStats();
            return true;
        } catch (RuntimeException e) {
            BetterWrenchMod.LOGGER.error("chain: 初始化传动轮统计失败 {}", pos, e);
            return false;
        }
    }

    /** 撤销 a 与 b 之间的连接(仅当该侧记录里确实有这条连接时才移除)。 */
    private static void unlink(Level level, BlockPos a, BlockPos b) {
        removeOneSide(level, a, b);
        removeOneSide(level, b, a);
    }

    private static void removeOneSide(Level level, BlockPos from, BlockPos to) {
        if (!(level.getBlockEntity(from) instanceof ChainConveyorBlockEntity be))
            return;
        if (!be.connections.contains(to.subtract(from)))
            return;
        try {
            be.prepareStats();          // removeConnectionTo 会写 connectionStats, 同样需要先就绪
            be.removeConnectionTo(to);
        } catch (RuntimeException e) {
            BetterWrenchMod.LOGGER.error("chain: 撤销连接失败 {} -> {}", from, to, e);
        }
    }

    /** 普通放置遇到连接失败: 逆序撤销本次新建的连接, 再还原本次放置的方块, 世界回到请求前。 */
    private static void rollback(Level level, List<BlockPos[]> created, List<BlockSnapshot> undo) {
        for (int i = created.size() - 1; i >= 0; i--) {
            BlockPos[] pair = created.get(i);
            unlink(level, pair[0], pair[1]);
        }
        revertAll(undo);
    }

    /**
     * {@link ChainRules#violationKey} 的后缀映射到<b>完整语言键</b>。
     *
     * <p>逐段几何违规直接复用 Create 自己的 {@code create.chain_conveyor.<后缀>}, 不新增译文
     * (Create 6.0.8 的 en_us/zh_cn 都有这几条); {@code blocks_invalid} 只在本类传入 null
     * 这类编程错误时出现, 落到本模组的通用「路径不合法」提示。</p>
     */
    private static String createViolationKey(String suffix) {
        return switch (suffix) {
            case "too_far", "too_close", "cannot_connect_vertically", "too_steep",
                 "cannot_add_more_connections" -> CREATE_KEY + suffix;
            default -> KEY + "invalid";
        };
    }

    // ------------------------------------------------------------------ 世界改动

    /** 单格放置的结果。 */
    private enum PlaceOutcome {
        /** 落上了且没被保护事件拦下。 */
        OK,
        /** 被 {@code EntityPlaceEvent} 取消(已还原该格)。 */
        DENIED,
        /** 方块没落上(极端情形; 已还原该格)。 */
        FAILED
    }

    /**
     * 在空格上放置一台锁链传动轮, 并补发原版的「实体放置方块」事件让领地/保护插件能拦住。
     * 失败时把该格还原(调用方决定是整体回滚还是跳过)。
     *
     * <p>1.20.1 平台差异: 事件走 Forge 的 {@link ForgeEventFactory#onBlockPlace},
     * {@code BlockSnapshot#restore()} 不带参数。</p>
     */
    private static PlaceOutcome placeConveyor(ServerLevel level, BlockPos pos, ServerPlayer player,
                                              List<BlockSnapshot> undo) {
        BlockState state = AllBlocks.CHAIN_CONVEYOR.getDefaultState();
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (ForgeEventFactory.onBlockPlace(player, snapshot, Direction.UP)) {
            snapshot.restore();
            return PlaceOutcome.DENIED;
        }
        if (level.getBlockState(pos).getBlock() != state.getBlock()) {
            snapshot.restore();
            return PlaceOutcome.FAILED;
        }
        undo.add(snapshot);
        return PlaceOutcome.OK;
    }

    /** 把本次已放置的方块按逆序还原(普通放置被拦下时, 世上不留半截传动结构)。 */
    private static void revertAll(List<BlockSnapshot> undo) {
        for (int i = undo.size() - 1; i >= 0; i--) {
            BlockSnapshot snapshot = undo.get(i);
            snapshot.restore();
        }
    }

    /**
     * 历史残留清理: 对本次请求触碰到的传动轮, 删除"反向记录缺失"的连接。
     *
     * <p>判据就是 Create 自己的 {@link ChainConveyorBlockEntity#removeInvalidConnections()}:
     * 目标未加载则保留; 目标不是传动轮, 或目标端没有反向记录, 则删除。因此它只会清掉真正的单边残留,
     * <b>不会</b>碰本次新建的连接(它们都写成了双向), 也不会碰任何合法的双向连接。
     * 放在请求完成之后执行, 这样"本次要补齐的单边连接"不会被提前删掉。</p>
     */
    private static void cleanupOrphans(Level level, List<BlockPos> path, boolean[] conveyor) {
        for (int i = 0; i < path.size(); i++) {
            if (!conveyor[i])
                continue;
            if (!(level.getBlockEntity(path.get(i)) instanceof ChainConveyorBlockEntity be))
                continue;
            try {
                be.prepareStats();
                be.removeInvalidConnections();
            } catch (RuntimeException e) {
                BetterWrenchMod.LOGGER.error("chain: 清理无效连接失败 {}", path.get(i), e);
            }
        }
    }

    /** 扣料: 只按实际放置的传动轮与实际新建的连接扣, 创造模式跳过。 */
    private static void consume(ServerPlayer player, int conveyors, int chains, boolean creative) {
        if (creative)
            return;
        if (conveyors > 0)
            ChainMaterials.consume(player, AllBlocks.CHAIN_CONVEYOR.get().asItem(), conveyors);
        if (chains > 0)
            ChainMaterials.consume(player, Items.CHAIN, chains);
    }

    private static void playPlaceSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6F, 1.0F);
    }

    // ------------------------------------------------------------------ 反馈

    private static void hint(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }
}
