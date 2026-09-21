package com.nonono.createbetterwrench.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.connect.ConnectLogic;
import com.nonono.createbetterwrench.connect.ConnectLogic.Plan;
import com.nonono.createbetterwrench.mode.WrenchMode;
import com.nonono.createbetterwrench.network.ConnectPayload;

import com.simibubi.create.AllSpecialTextures;
import com.simibubi.create.content.kinetics.base.IRotate;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端「连接」选择状态机(新交互, 2026-09-07 用户重定义)。
 *
 * <p>交互:
 * <ol>
 *   <li>右击机械动力方块 → 选定起点 S(金框);</li>
 *   <li>之后右击普通方块 → 追加一个拐点(金框, 数量不限);</li>
 *   <li>之后右击机械动力方块 → 选定终点 E, 把 S→拐点…→E 的铺设计划发给服务端。</li>
 * </ol>
 * 每段相邻点的边按"同轴=直线 / 同平面=自动一次 90° 拐弯 / 非平面=拒连"由服务端路由; 放置前校验背包材料。
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID, value = Dist.CLIENT)
public final class ConnectSelectionHandler {

    private static final Object START_KEY = "connect_start";
    private static final Object CORNER_KEY = "connect_corner";
    private static final Object HOVER_KEY = "connect_hover";
    private static final Object GHOST_KEY = "connect_ghost";

    private static final int GOLD = 0xE8B54C;
    private static final int GREEN = 0x95CD41;
    private static final int RED = 0xEA5C2B;

    private static BlockPos startPos;
    private static final List<BlockPos> corners = new ArrayList<>();

    private ConnectSelectionHandler() {
    }

    /**
     * 仅当**主手**持扳手且当前模式为「连接」时才接管。
     *
     * <p>⚠️ 2026-09-20(用户约定): 扳手在**副手**时"只作普通扳手" ⇒ 本模式不生效, 右键原样交给 Create。</p>
     */
    private static boolean active(Minecraft mc) {
        return mc.player != null
            && mc.player.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH)
            && WrenchModeSwitcher.current == WrenchMode.CONNECT;
    }

    private static boolean onRightClick() {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc))
            return false;
        // 审计 A-14: Shift+右键 = 放弃当前起点与全部拐点(在 active 判定之后)
        if (mc.player.isShiftKeyDown()) {
            resetSelection();
            return true;
        }
        BlockHitResult bhr = rayTraceHit(mc);

        if (bhr == null) {
            // 对准开阔空气: 直接选该空气格作拐点(需已选起点与已有拐点语义)
            if (startPos != null) {
                BlockPos air = airCellFromLook(mc);
                if (air != null && !air.equals(startPos))
                    addCorner(mc, air);
            }
            return true;
        }

        BlockPos hit = bhr.getBlockPos();
        boolean kinetic = isKineticBlock(mc.level, hit);

        if (startPos == null) {
            if (kinetic)
                startPos = hit;
            return true;
        }

        if (hit.equals(startPos))
            return true;

        if (kinetic) {
            ClientPacketListener conn = mc.getConnection();
            if (conn != null)
                PacketDistributor.sendToServer(
                    ConnectPayload.create(startPos, corners, hit, WrenchModeSwitcher.connectCorner));
            resetSelection();
            return true;
        }

        // 拐点 = 直接选中"空气格": 取点击方块命中面旁的空气格(不选中完整方块本身)
        addCorner(mc, hit.relative(bhr.getDirection()));
        return true;
    }

    /**
     * 追加一个拐点(审计 A-2)。
     *
     * <p>拐点数量必须与 {@link ConnectPayload#MAX_CORNERS} 对齐: 客户端若不设上限, 第 33 个拐点会让
     * 服务端的载荷解码抛出 DecoderException, 直接把玩家踢下线(且此时选点已被清空, 没有任何提示)。
     * 与**最后一个拐点重复**的格也直接忽略 —— 零长边会被服务端整单判 SAME_POS 拒连。</p>
     */
    private static void addCorner(Minecraft mc, BlockPos pos) {
        if (corners.size() >= ConnectPayload.MAX_CORNERS) {
            if (mc.player != null)
                mc.player.displayClientMessage(Component.translatable(
                    "hint." + BetterWrenchMod.MODID + ".connect.corner_limit"), true);
            return;
        }
        if (!corners.isEmpty() && corners.get(corners.size() - 1).equals(pos))
            return;
        corners.add(pos.immutable());
    }

    private static void resetSelection() {
        // 审计 B-7: 拐点框用的是带索引的 key(CORNER_KEY + "|" + i), 必须逐个 remove, 否则会残留在 Outliner
        for (int i = 0; i < corners.size(); i++)
            Outliner.getInstance().remove(CORNER_KEY + "|" + i);
        startPos = null;
        corners.clear();
        Outliner.getInstance().remove(START_KEY);
        Outliner.getInstance().remove(CORNER_KEY);
        Outliner.getInstance().remove(HOVER_KEY);
        Outliner.getInstance().remove(GHOST_KEY);
    }

    /**
     * 丢弃当前未完成的连接选择(起点 + 全部拐点 + 预览框)。
     *
     * <p>供登出清理使用: 否则玩家在世界 A 选了一半起点、退出后进世界 B,
     * 那些**属于旧世界坐标**的静态状态仍在, 下一次右键会拿旧坐标去做连接。</p>
     */
    public static void cancel() {
        resetSelection();
    }

    private static BlockHitResult rayTraceHit(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK)
            return (BlockHitResult) hit;
        return null;
    }

    /** 拐点应直接落在"空气格": 点到实体方块 → 取其命中面旁的空气格; 指向开阔空气 → 取准星所看的空气格。 */
    private static BlockPos cornerTargetAirCell(Minecraft mc) {
        BlockHitResult bhr = rayTraceHit(mc);
        if (bhr != null)
            return bhr.getBlockPos().relative(bhr.getDirection());
        return airCellFromLook(mc);
    }

    /** 从玩家视线沿朝向步进, 取第一个非自身的空气格(用于直接点到开阔空气)。 */
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
            BlockState st = mc.level.getBlockState(p);
            if (st.isAir() || st.canBeReplaced())
                return p.immutable();
        }
        return null;
    }

    private static boolean isKineticBlock(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof IRotate))
            return false;
        return world.getBlockEntity(pos) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null)
            return;
        boolean pressed = event.getAction() == 1;
        boolean isUseButton = event.getButton() == 1;
        if (!pressed || !isUseButton)
            return;
        if (active(mc)) {
            event.setCanceled(true);
            onRightClick();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return;

        if (!active(mc)) {
            if (startPos != null)
                resetSelection();
            else {
                Outliner.getInstance().remove(START_KEY);
                Outliner.getInstance().remove(CORNER_KEY);
                Outliner.getInstance().remove(HOVER_KEY);
                Outliner.getInstance().remove(GHOST_KEY);
            }
            return;
        }

        BlockHitResult hitResult = rayTraceHit(mc);
        BlockPos hit = hitResult != null ? hitResult.getBlockPos() : null;

        if (startPos == null) {
            if (hit != null && isKineticBlock(mc.level, hit))
                Outliner.getInstance().chaseAABB(START_KEY, new AABB(hit))
                    .colored(GOLD).lineWidth(1 / 16f);
            else
                Outliner.getInstance().remove(START_KEY);
            Outliner.getInstance().remove(CORNER_KEY);
            Outliner.getInstance().remove(HOVER_KEY);
            Outliner.getInstance().remove(GHOST_KEY);
            return;
        }

        // 起点金框
        Outliner.getInstance().chaseAABB(START_KEY, new AABB(startPos))
            .colored(GOLD).lineWidth(1 / 16f);

        // 所有拐点金框(逐 tick 重画, 同名 key 覆盖)
        int i = 0;
        for (BlockPos c : corners)
            Outliner.getInstance().chaseAABB(CORNER_KEY + "|" + (i++), new AABB(c))
                .colored(GOLD).lineWidth(1 / 16f);

        // 悬停提示
        BlockPos aimBlock = hitResult != null ? hitResult.getBlockPos() : null;
        boolean aimKinetic = aimBlock != null && isKineticBlock(mc.level, aimBlock);

        if (aimKinetic) {
            if (aimBlock.equals(startPos)) {
                Outliner.getInstance().remove(HOVER_KEY);
                Outliner.getInstance().remove(GHOST_KEY);
                return;
            }
            ConnectLogic.ResultOutcome oc =
                ConnectLogic.plan(mc.level, startPos, corners, aimBlock, WrenchModeSwitcher.connectCorner);
            boolean ok = oc.result == ConnectLogic.Result.SUCCESS && affordable(mc, oc.plan);
            Outliner.getInstance().chaseAABB(HOVER_KEY, new AABB(aimBlock))
                .colored(ok ? GREEN : RED).lineWidth(1 / 16f);
            if (ok)
                drawGhost(oc.plan);
            else
                Outliner.getInstance().remove(GHOST_KEY);
        } else {
            // 拐点候选: 直接高亮"空气格"(点击方块命中面旁的空气格 / 开阔空气的准星格)
            BlockPos cornerCell = cornerTargetAirCell(mc);
            if (cornerCell == null) {
                Outliner.getInstance().remove(HOVER_KEY);
                Outliner.getInstance().remove(GHOST_KEY);
                return;
            }
            Outliner.getInstance().chaseAABB(HOVER_KEY, new AABB(cornerCell))
                .colored(GOLD).lineWidth(1 / 16f);
            Outliner.getInstance().remove(GHOST_KEY);
        }
    }

    /**
     * 审计 A-15: 服务端除了几何还会卡"总方块数上限"和"材料是否够", 客户端画绿框前先按同一口径自查,
     * 避免"预览是绿的、服务端却回 路径过长 / 材料不足"的割裂体验
     * (材料口径直接复用 {@link ConnectLogic#hasMaterials}, 与服务端扣料完全一致)。
     */
    private static boolean affordable(Minecraft mc, Plan plan) {
        if (plan == null || mc.player == null)
            return false;
        int total = plan.shaftPositions.size() + plan.gearboxes.size() + plan.cogs.size();
        if (total > ConnectLogic.MAX_TOTAL_BLOCKS)
            return false;
        return ConnectLogic.hasMaterials(mc.player, plan);
    }

    private static void drawGhost(Plan plan) {
        Set<BlockPos> ghost = new LinkedHashSet<>(plan.shaftPositions);
        for (ConnectLogic.GearboxPlace g : plan.gearboxes)
            ghost.add(g.pos);
        for (ConnectLogic.CogPlace c : plan.cogs)
            ghost.add(c.pos);
        if (ghost.isEmpty()) {
            Outliner.getInstance().remove(GHOST_KEY);
            return;
        }
        Outliner.getInstance().showCluster(GHOST_KEY, ghost)
            .withFaceTexture(AllSpecialTextures.THIN_CHECKERED)
            .colored(GREEN)
            .lineWidth(0);
    }
}
