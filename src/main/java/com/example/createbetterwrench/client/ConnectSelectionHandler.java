package com.example.createbetterwrench.client;

import java.util.LinkedHashSet;
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
import net.minecraft.world.level.LevelReader;
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
 *   <li>右击机械动力方块 → 选定起点 A(金色框);</li>
 *   <li>之后右击普通方块 → 视为拐弯节点(此处将放齿轮箱, 金色框);</li>
 *   <li>之后右击机械动力方块 → 选定终点 B 并把 A→拐点→B 的铺设计划发给服务端。</li>
 * </ol>
 * 只走直线 + 最多一次拐弯; 放置前服务端会校验背包材料是否足够。
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
    private static BlockPos cornerPos;

    private ConnectSelectionHandler() {
    }

    private static boolean active(Minecraft mc) {
        return mc.player != null
            && (mc.player.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH)
                || mc.player.getOffhandItem().is(BetterWrenchMod.BETTER_WRENCH))
            && WrenchModeSwitcher.current == WrenchMode.CONNECT;
    }

    /** 右键按下: 仅在连接模式下接管。返回 true 表示本 mod 消费了这次点击。 */
    private static boolean onRightClick() {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc))
            return false;
        BlockPos hit = rayTraceBlock(mc);
        if (hit == null)
            return true; // 没点到方块: 仍吃掉, 避免误触普通扳手

        boolean kinetic = isKineticBlock(mc.level, hit);

        if (startPos == null) {
            // 首次右击必须是机械动力方块
            if (kinetic)
                startPos = hit;
            return true;
        }

        if (hit.equals(startPos))
            return true; // 不能选自己为终点

        if (kinetic) {
            // 右击机械动力方块 => 终点 B, 发铺设计划
            ClientPacketListener conn = mc.getConnection();
            if (conn != null)
                PacketDistributor.sendToServer(ConnectPayload.create(startPos, cornerPos, hit));
            resetSelection();
            return true;
        }

        // 右击普通方块 => 拐弯节点(至多一个, 重复点击则替换)
        cornerPos = hit;
        return true;
    }

    private static void resetSelection() {
        startPos = null;
        cornerPos = null;
        Outliner.getInstance().remove(START_KEY);
        Outliner.getInstance().remove(CORNER_KEY);
        Outliner.getInstance().remove(HOVER_KEY);
        Outliner.getInstance().remove(GHOST_KEY);
    }

    private static BlockPos rayTraceBlock(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK)
            return ((BlockHitResult) hit).getBlockPos();
        return null;
    }

    /** 客户端侧的"机械动力方块"粗判(IRotate + KineticBlockEntity)。 */
    private static boolean isKineticBlock(LevelReader world, BlockPos pos) {
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

    /**
     * 每帧刷新连接预览: 起点/拐点金框, 悬停终点绿/红框 + 可生成时半透明幽灵传动链。
     */
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

        BlockPos hit = rayTraceBlock(mc);

        // 起点(金框)
        if (startPos == null) {
            // 未选起点: 悬停的机械动力方块用金色框提示可作为起点
            if (hit != null && isKineticBlock(mc.level, hit))
                Outliner.getInstance().chaseAABB(START_KEY, new AABB(hit))
                    .colored(GOLD).lineWidth(1 / 16f);
            else
                Outliner.getInstance().remove(START_KEY);
            // 清掉其它预览
            Outliner.getInstance().remove(CORNER_KEY);
            Outliner.getInstance().remove(HOVER_KEY);
            Outliner.getInstance().remove(GHOST_KEY);
            return;
        }

        // 起点金框(固定显示)
        Outliner.getInstance().chaseAABB(START_KEY, new AABB(startPos))
            .colored(GOLD).lineWidth(1 / 16f);

        // 拐点金框(固定显示)
        if (cornerPos == null)
            Outliner.getInstance().remove(CORNER_KEY);
        else
            Outliner.getInstance().chaseAABB(CORNER_KEY, new AABB(cornerPos))
                .colored(GOLD).lineWidth(1 / 16f);

        // 悬停提示
        if (hit == null || hit.equals(startPos)) {
            Outliner.getInstance().remove(HOVER_KEY);
            Outliner.getInstance().remove(GHOST_KEY);
            return;
        }

        boolean hoverKinetic = isKineticBlock(mc.level, hit);
        if (hoverKinetic) {
            // 悬停=终点候选: 尝试计划, 绿=可生成(并显示幽灵链), 红=不可
            ConnectLogic.ResultOutcome oc = ConnectLogic.plan(mc.level, startPos, cornerPos, hit);
            boolean ok = oc.result == ConnectLogic.Result.SUCCESS;
            Outliner.getInstance().chaseAABB(HOVER_KEY, new AABB(hit))
                .colored(ok ? GREEN : RED).lineWidth(1 / 16f);
            if (ok)
                drawGhost(oc.plan);
            else
                Outliner.getInstance().remove(GHOST_KEY);
        } else {
            // 悬停=普通方块: 作为拐点候选(金框)
            Outliner.getInstance().chaseAABB(HOVER_KEY, new AABB(hit))
                .colored(GOLD).lineWidth(1 / 16f);
            Outliner.getInstance().remove(GHOST_KEY);
        }
    }

    private static void drawGhost(Plan plan) {
        Set<BlockPos> ghost = new LinkedHashSet<>(plan.shafts);
        if (plan.gearboxPos != null)
            ghost.add(plan.gearboxPos);
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
