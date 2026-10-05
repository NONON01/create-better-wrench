package com.nonono.createbetterwrench.client.chain;

import java.util.ArrayList;
import java.util.List;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.chain.ChainMaterials;
import com.nonono.createbetterwrench.client.ClientFeatureGate;
import com.nonono.createbetterwrench.chain.ChainRules;
import com.nonono.createbetterwrench.client.WrenchModeSwitcher;
import com.nonono.createbetterwrench.mode.ChainSubMode;
import com.nonono.createbetterwrench.mode.WrenchMode;
import com.nonono.createbetterwrench.network.ChainConnectPayload;
import com.nonono.createbetterwrench.network.WrenchNetwork;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * 客户端「锁链传动」选择状态机。
 *
 * <p>交互(依据 docs/design/chain-mode-spec.md 第 4 节):
 * <ol>
 *   <li>右击一台锁链传动轮: 选定起点;</li>
 *   <li>标准与直线子模式: 再右击另一台锁链传动轮作为终点, 由规划器给出路径并请求服务端放置;</li>
 *   <li>半自动子模式: 按「连接」模式的口径取点(命中面外侧的空气格, 或对准开阔空气时沿视线步进的第一格),
 *       依次追加中间点(只取该点的 X 与 Z), 右击终点传动轮结束;</li>
 *   <li>潜行右击: 请求强制放置(材料不足时按服务端降级语义执行);</li>
 *   <li>左击: 清除当前选择。</li>
 * </ol>
 *
 * <p>半自动模式的动态选区预览(2026-10-04 内测): 终点尚未确定时, 准星按「连接」口径取得的候选格(可为空气格)
 * 会临时接到已选点之后做一次试算, 非法则把选择柱体画成红色, 并沿"起点到当前候选点或终点"的选中折线画出整条
 * 红色连线, 使玩家在按下右键之前就能看到整条候选路线能否使用。该试算只影响预览, 不写入已选点, 也不改变右键
 * 的接受规则(拒绝仍发生在按下右键后的正式规划里)。</p>
 *
 * <p>预览重算沿用「连接」模式的节流策略: 仅在起点、子模式、终点、中间点集合、半自动下的准星候选点变化,
 * 或缓存超过 {@link #PREVIEW_MAX_AGE_TICKS} 个客户端刻时重算, 避免每刻在主线程上重跑规划。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID, value = Dist.CLIENT)
public final class ChainSelectionHandler {

    private static final int COLOR_OK = 0x95CD41;
    private static final int COLOR_BAD = 0xEA5C2B;

    private static BlockPos startPos;
    private static final List<BlockPos> waypoints = new ArrayList<>();

    private static ChainPlanner.Plan cachedPlan;
    private static BlockPos cachedStart;
    private static BlockPos cachedEnd;
    private static BlockPos cachedCandidate;
    private static ChainSubMode cachedSub;
    private static int cachedWaypointHash;
    private static int cachedAge;
    private static final int PREVIEW_MAX_AGE_TICKS = 5;

    private ChainSelectionHandler() {
    }

    /** 仅当主手持扳手且当前模式为「锁链传动」时接管右键与预览。 */
    private static boolean active(Minecraft mc) {
        return mc.player != null
            && mc.player.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH.get())
            && WrenchModeSwitcher.current == WrenchMode.CHAIN;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null)
            return;
        if (event.getAction() != 1)
            return;
        if (!active(mc))
            return;
        // 功能开关关闭时连预览与发包都不做(与「连接」模式同一闸门)
        if (ClientFeatureGate.blockIfDisabled(WrenchMode.CHAIN))
            return;

        if (event.getButton() == 0) {
            // 左击: 清除选择
            event.setCanceled(true);
            if (startPos != null) {
                clearSelection();
                actionbar("hint.create_better_wrench.chain.selection_cleared");
            }
            return;
        }
        if (event.getButton() != 1)
            return;

        event.setCanceled(true);
        BlockHitResult hit = rayTrace(mc);
        if (hit != null) {
            onRightClick(mc, hit.getBlockPos(), mc.player.isShiftKeyDown());
            return;
        }
        // 准星未命中任何方块(开阔空气): 半自动下仍可沿视线取空气格作为中间点, 与「连接」模式一致
        if (startPos != null && WrenchModeSwitcher.chainSubMode() == ChainSubMode.SEMI)
            onRightClick(mc, null, mc.player.isShiftKeyDown());
    }

    private static void onRightClick(Minecraft mc, BlockPos pos, boolean forced) {
        if (mc.level == null)
            return;

        if (startPos == null) {
            if (!ChainRules.isConveyor(mc.level, pos)) {
                actionbar("hint.create_better_wrench.chain.need_start");
                return;
            }
            startPos = pos.immutable();
            waypoints.clear();
            invalidateCache();
            return;
        }

        ChainSubMode sub = WrenchModeSwitcher.chainSubMode();

        if (sub == ChainSubMode.SEMI) {
            // 半自动: 命中传动轮即视为终点; 否则按「连接」模式的取点口径取中间点(允许落在空气格上)
            if (ChainRules.isConveyor(mc.level, pos)) {
                submit(mc, pos, sub, forced);
                return;
            }
            BlockPos cell = waypointTargetCell(mc);
            if (cell == null || cell.equals(startPos)) {
                actionbar("hint.create_better_wrench.chain.need_end");
                return;
            }
            waypoints.add(cell.immutable());
            invalidateCache();
            return;
        }

        if (!ChainRules.isConveyor(mc.level, pos)) {
            actionbar("hint.create_better_wrench.chain.need_end");
            return;
        }
        submit(mc, pos, sub, forced);
    }

    /** 规划并把计划交给服务端; 规划不合法时只提示, 不发送任何请求。 */
    private static void submit(Minecraft mc, BlockPos end, ChainSubMode sub, boolean forced) {
        ChainPlanner.Plan plan = ChainPlanner.plan(mc.level, startPos, List.copyOf(waypoints), end, sub);
        cachedPlan = plan;
        cachedStart = startPos;
        cachedEnd = end;
        cachedSub = sub;
        cachedWaypointHash = waypoints.hashCode();
        cachedAge = 0;

        if (!plan.valid()) {
            actionbar(violationKey(plan));
            return;
        }

        // 普通模式按服务端同一口径预检材料: 不足时只提示, 且不清除已选路径(规格 §5)。
        // 预检通过才发送请求, 并让选择复位 —— 一次完整铺设即结束本次选择。
        boolean creative = mc.player.getAbilities().instabuild;
        int conveyors = ChainMaterials.countConveyors(mc.player);
        int chains = ChainMaterials.countChains(mc.player);
        if (!forced && !creative && (conveyors < plan.conveyorCost() || chains < plan.chainCost())) {
            actionbar("hint.create_better_wrench.chain.materials_missing",
                Math.max(0, plan.conveyorCost() - conveyors), Math.max(0, plan.chainCost() - chains));
            return;
        }

        WrenchNetwork.sendToServer(new ChainConnectPayload(sub, List.copyOf(plan.path()), forced));
        clearSelection();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // 1.20.1(Forge)的 ClientTickEvent 每客户端刻触发 START 与 END 两次, 与 1.21.1 的
        // ClientTickEvent.Post 对应的是 END; 不过滤会让预览刷新与节流计数每刻各推进两次。
        // 过滤写法与既有的连接、拆除两个选择状态机一致。
        if (event.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return;

        if (!active(mc)) {
            if (startPos != null)
                clearSelection();
            else
                ChainPreview.clear();
            return;
        }

        BlockHitResult hit = rayTrace(mc);
        BlockPos hovered = hit != null ? hit.getBlockPos() : null;

        if (startPos == null) {
            ChainPreview.clear();
            clearMarkers();
            if (hovered != null && ChainRules.isConveyor(mc.level, hovered))
                ChainPreview.show(null, hovered);
            return;
        }

        // 留存标记: 起点画方框; 半自动下每次右键选中的中间点画"覆盖世界高度的金色竖柱",
        // 使已选中间点在整段铺设过程中持续可见(键名固定, 每刻覆盖重画)。
        Outliner.getInstance().chaseAABB("cbw_chain_start", new AABB(startPos))
            .colored(0xE8B54C).lineWidth(1 / 16f);
        int minY = mc.level.getMinBuildHeight();
        int maxY = mc.level.getMaxBuildHeight();
        int wi = 0;
        for (BlockPos wp : waypoints)
            Outliner.getInstance().chaseAABB("cbw_chain_wp|" + (wi++),
                new AABB(wp.getX(), minY, wp.getZ(), wp.getX() + 1, maxY, wp.getZ() + 1))
                .colored(0xE8B54C).lineWidth(1 / 16f);

        ChainSubMode sub = WrenchModeSwitcher.chainSubMode();
        BlockPos end = hovered != null && !hovered.equals(startPos) && ChainRules.isConveyor(mc.level, hovered)
            ? hovered
            : null;
        // 半自动且终点未确定时, 准星候选中间点按「连接」模式的空气口径取: 命中面外侧的格, 或未命中任何
        // 方块时沿视线步进的第一格。试算只用于预览, 不写入 waypoints, 也不改变右键的接受规则。
        BlockPos aimCell = end == null && sub == ChainSubMode.SEMI ? waypointTargetCell(mc) : null;
        BlockPos candidate = aimCell != null && !aimCell.equals(startPos) ? aimCell : null;

        cachedAge++;
        boolean stale = cachedPlan == null || cachedAge > PREVIEW_MAX_AGE_TICKS
            || !startPos.equals(cachedStart) || !java.util.Objects.equals(end, cachedEnd)
            || !java.util.Objects.equals(candidate, cachedCandidate)
            || sub != cachedSub || waypoints.hashCode() != cachedWaypointHash;

        // 半自动在选区中途也实时预览: 终点尚未确定时, 把准星候选点临时接到已选点之后逐段校验,
        // 某段超出单段上限、过近或会压过既有轮时立即红框, 便于就地调整, 而不是等选完终点才发现。
        boolean previewable = end != null
            || (sub == ChainSubMode.SEMI && (!waypoints.isEmpty() || candidate != null));
        if (stale && previewable) {
            List<BlockPos> trial = new ArrayList<>(waypoints);
            if (end == null && candidate != null)
                trial.add(candidate);
            cachedPlan = ChainPlanner.plan(mc.level, startPos, List.copyOf(trial), end, sub);
            cachedStart = startPos;
            cachedEnd = end;
            cachedCandidate = candidate;
            cachedSub = sub;
            cachedWaypointHash = waypoints.hashCode();
            cachedAge = 0;
        }

        if (candidate != null)
            ChainPreview.showColumn(candidate, cachedPlan != null && cachedPlan.valid());

        // 非法时沿"起点 -> 已选中间点 -> 当前候选点/终点"这条折线画整条红色连线(规格 §3.2 的红色路径),
        // 使玩家看到整条候选路线不可用, 而不是只看准星那一格; 该折线随准星实时变化, 复用上面的节流缓存。
        List<BlockPos> selectionPath = new ArrayList<>();
        selectionPath.add(startPos);
        selectionPath.addAll(waypoints);
        if (end != null)
            selectionPath.add(end);
        else if (candidate != null)
            selectionPath.add(candidate);


        ChainPreview.show(cachedPlan, candidate != null ? candidate : hovered, selectionPath);
    }

    /**
     * 规划器给出的违规键可能是 Create 的既有后缀(例如 too_far), 统一补齐为完整键。
     *
     * <p>补齐后可直接复用 Create 自己的译文(create.chain_conveyor.*), 无需为本模组重复维护一套违规文案。</p>
     */
    private static String violationKey(ChainPlanner.Plan plan) {
        String key = plan == null ? null : plan.violationKey();
        if (key == null || key.isBlank())
            return "hint.create_better_wrench.chain.invalid";
        if (key.indexOf('.') >= 0)
            return key;
        // 搜索越限没有对应的 Create 键, 用本模组自己的文案; 其余后缀直接复用 Create 译文
        if ("limit".equals(key))
            return "hint.create_better_wrench.chain.violation.limit";
        return "create.chain_conveyor." + key;
    }

    private static BlockHitResult rayTrace(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK)
            return bhr;
        return null;
    }

    /**
     * 半自动中间点的取点格, 口径与「连接」模式的拐点完全一致。
     *
     * <p>准星命中方块时: 命中的是锁链传动轮则返回该传动轮(半自动把它当作终点); 命中的是其它方块则取该命中面
     * 外侧的格子, 即玩家视线来向的空气格。准星未命中任何方块(开阔空气)时, 沿视线步进取第一个空气或可替换格。
     * 起点与终点仍必须是已存在的锁链传动轮, 空气取点只用于中间点。</p>
     */
    private static BlockPos waypointTargetCell(Minecraft mc) {
        BlockHitResult hit = rayTrace(mc);
        if (hit != null) {
            BlockPos hitPos = hit.getBlockPos();
            if (ChainRules.isConveyor(mc.level, hitPos))
                return hitPos;
            return hitPos.relative(hit.getDirection());
        }
        return airCellFromLook(mc);
    }

    /** 从玩家视线沿朝向步进, 取第一个非自身的空气格; 范围与步长与「连接」模式保持一致。 */
    private static BlockPos airCellFromLook(Minecraft mc) {
        if (mc.player == null || mc.level == null)
            return null;
        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 look = mc.player.getLookAngle();
        BlockPos eyeBlock = BlockPos.containing(eye);
        double range = 6.5;
        double step = 0.25;
        for (double t = step; t <= range; t += step) {
            BlockPos p = BlockPos.containing(eye.x + look.x * t, eye.y + look.y * t, eye.z + look.z * t);
            if (p.equals(eyeBlock))
                continue;
            if (!mc.level.isLoaded(p))
                continue;                   // 不触碰未加载区块(项目审计 A-5 口径)
            BlockState state = mc.level.getBlockState(p);
            if (state.isAir() || state.canBeReplaced())
                return p.immutable();
        }
        return null;
    }

    private static void invalidateCache() {
        cachedPlan = null;
        cachedCandidate = null;
        cachedAge = 0;
    }

    private static void clearSelection() {
        startPos = null;
        waypoints.clear();
        invalidateCache();
        ChainPreview.clear();
    }



    /** 清除起点与中间点的留存标记(键名固定, 覆盖 0 到 15 号中间点即可)。 */
    private static void clearMarkers() {
        Outliner.getInstance().remove("cbw_chain_start");
        for (int i = 0; i <= 15; i++)
            Outliner.getInstance().remove("cbw_chain_wp|" + i);
    }

    private static void actionbar(String key) {
        actionbar(key, new Object[0]);
    }

    /** actionbar 提示, 支持带占位符的文案(例如材料缺口的数量)。 */
    private static void actionbar(String key, Object... args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.displayClientMessage(Component.translatable(key, args), true);
    }

    /** 供其它客户端组件读取当前选中的起点(工具提示用)。 */
    public static BlockPos startPos() {
        return startPos;
    }

    /** 预览配色: 供预览模块与工具提示共用。 */
    public static int colorOk() {
        return COLOR_OK;
    }

    /** 预览配色: 供预览模块与工具提示共用。 */
    public static int colorBad() {
        return COLOR_BAD;
    }
}
