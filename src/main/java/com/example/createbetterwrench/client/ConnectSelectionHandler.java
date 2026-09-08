package com.example.createbetterwrench.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.example.createbetterwrench.BetterWrenchMod;
import com.example.createbetterwrench.connect.ConnectLogic;
import com.example.createbetterwrench.connect.ConnectLogic.Plan;
import com.example.createbetterwrench.mode.WrenchMode;
import com.example.createbetterwrench.network.ConnectPayload;

import com.simibubi.create.AllSpecialTextures;
import com.simibubi.create.content.kinetics.base.IRotate;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
@EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
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

    private static boolean active(Minecraft mc) {
        return mc.player != null
            && (mc.player.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH)
                || mc.player.getOffhandItem().is(BetterWrenchMod.BETTER_WRENCH))
            && WrenchModeSwitcher.current == WrenchMode.CONNECT;
    }

    private static boolean onRightClick() {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc))
            return false;
        BlockHitResult bhr = rayTraceHit(mc);
        if (bhr == null)
            return true;
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
                PacketDistributor.sendToServer(ConnectPayload.create(startPos, corners, hit));
            resetSelection();
            return true;
        }

        // 拐点 = 点击实体方块"旁边(准星所看那面的方向)的空气方块", 而非实体方块本身;
        // 这样齿轮箱会放到空气格里, 不会放进/替换完整方块。
        corners.add(hit.relative(bhr.getDirection()));
        return true;
    }

    private static void resetSelection() {
        startPos = null;
        corners.clear();
        Outliner.getInstance().remove(START_KEY);
        Outliner.getInstance().remove(CORNER_KEY);
        Outliner.getInstance().remove(HOVER_KEY);
        Outliner.getInstance().remove(GHOST_KEY);
    }

    private static BlockHitResult rayTraceHit(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK)
            return (BlockHitResult) hit;
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
        if (hit == null || hit.equals(startPos)) {
            Outliner.getInstance().remove(HOVER_KEY);
            Outliner.getInstance().remove(GHOST_KEY);
            return;
        }

        if (isKineticBlock(mc.level, hit)) {
            ConnectLogic.ResultOutcome oc = ConnectLogic.plan(mc.level, startPos, corners, hit);
            boolean ok = oc.result == ConnectLogic.Result.SUCCESS;
            Outliner.getInstance().chaseAABB(HOVER_KEY, new AABB(hit))
                .colored(ok ? GREEN : RED).lineWidth(1 / 16f);
            if (ok)
                drawGhost(oc.plan);
            else
                Outliner.getInstance().remove(GHOST_KEY);
        } else {
            Outliner.getInstance().chaseAABB(HOVER_KEY, new AABB(hit))
                .colored(GOLD).lineWidth(1 / 16f);
            Outliner.getInstance().remove(GHOST_KEY);
        }
    }

    private static void drawGhost(Plan plan) {
        Set<BlockPos> ghost = new LinkedHashSet<>(plan.shaftPositions);
        for (ConnectLogic.GearboxPlace g : plan.gearboxes)
            ghost.add(g.pos);
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
