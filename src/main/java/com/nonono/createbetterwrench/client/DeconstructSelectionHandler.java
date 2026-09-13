package com.nonono.createbetterwrench.client;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.mode.DeconstructScope;
import com.nonono.createbetterwrench.mode.WrenchMode;
import com.nonono.createbetterwrench.network.DeconstructPayload;

import com.simibubi.create.AllSpecialTextures;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
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
 * 客户端「拆除」选框状态机(仿 Create SchematicAndQuillHandler)。
 *
 * <p>仅当:手持我们的扳手 + 当前模式为 DECONSTRUCT 时生效。
 * 通过拦截客户端鼠标右键(MouseButton.Pre)抢在 Create WrenchEventHandler 之前吃掉本次点击,
 * 从而让右键只做"选角 A / 选角 B", 不会触发普通扳手的旋转/拆除。</p>
 *
 * <p>交互: 第一次右键定 A, 之后移动视角会用 catnip Outliner 画蓝色选区框(A → 当前视线块);
 * 第二次右键定 B 并立即向服务端发送 DeconstructPayload 执行拆除。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class DeconstructSelectionHandler {

    private static final Object OUTLINE_KEY = "deconstruct_select";

    private static BlockPos cornerA;
    private static BlockPos previewB;

    private DeconstructSelectionHandler() {
    }

    private static boolean active(Minecraft mc) {
        return mc.player != null
            && (mc.player.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH)
                || mc.player.getOffhandItem().is(BetterWrenchMod.BETTER_WRENCH))
            && WrenchModeSwitcher.current == WrenchMode.DECONSTRUCT;
    }

    /** 右键按下: 仅在拆除模式下接管。返回 true 表示本 mod 消费了这次点击。 */
    private static boolean onRightClick() {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc))
            return false;
        BlockPos hit = rayTraceBlock(mc);
        if (hit == null)
            return true; // 没点到方块: 仍吃掉, 避免误触普通扳手

        if (cornerA == null) {
            cornerA = hit;
            previewB = hit;
            return true;
        }
        // 第二次右键: 定 B 并发包
        BlockPos b = hit;
        DeconstructScope scope = WrenchModeSwitcher.deconstructScope;
        ClientPacketListener conn = mc.getConnection();
        if (conn != null)
            PacketDistributor.sendToServer(DeconstructPayload.create(cornerA, b, scope));
        resetSelection();
        return true;
    }

    private static void resetSelection() {
        cornerA = null;
        previewB = null;
    }

    /** 玩家视线对某方块的射线(用于选角)。 */
    private static BlockPos rayTraceBlock(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK)
            return ((BlockHitResult) hit).getBlockPos();
        return null;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null)
            return;
        boolean pressed = event.getAction() == 1; // PRESS
        boolean isUseButton = event.getButton() == 1; // 右键
        if (!pressed || !isUseButton)
            return;
        if (active(mc) && onRightClick())
            event.setCanceled(true);
    }

    /**
     * 每帧刷新: 拆除模式下用 Outliner 画"蓝图与笔式"蓝框(蓝线 + 淡蓝格纹面)。
     * - 未选 A: 画准星所指的单个方块框;
     * - 已选 A: 画 A → 当前视线块 的区域框。
     * 由本类的 ClientTickEvent.Post 订阅驱动(仿 Create 每帧刷新 outliner 的惯用法)。
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return;

        if (!active(mc)) {
            // 不在拆除模式: 清掉残留框
            Outliner.getInstance().remove(OUTLINE_KEY);
            if (cornerA != null)
                resetSelection();
            return;
        }

        BlockPos hit = rayTraceBlock(mc);

        if (cornerA == null) {
            // 未选 A: 画准星所指单格的框
            if (hit == null) {
                Outliner.getInstance().remove(OUTLINE_KEY);
                return;
            }
            Outliner.getInstance().chaseAABB(OUTLINE_KEY, new AABB(hit))
                .colored(0x6886c5)
                .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
                .lineWidth(1 / 16f);
            return;
        }

        // 已选 A: 画 A → 当前视线块 的区域框
        previewB = hit != null ? hit : cornerA;
        AABB box = new AABB(Vec3.atLowerCornerOf(cornerA), Vec3.atLowerCornerOf(previewB))
            .expandTowards(1, 1, 1);
        Outliner.getInstance().chaseAABB(OUTLINE_KEY, box)
            .colored(0x6886c5)
            .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
            .lineWidth(1 / 16f);
    }

    /** 供其它类查询当前是否已选 A(如 HUD 是否画提示)。 */
    public static boolean hasCornerA() {
        return cornerA != null;
    }

    /** 供其它类查询已选 A 的位置。 */
    public static BlockPos getCornerA() {
        return cornerA;
    }

    /** 潜行时取消已选的 A(供后续 Esc/Shift 取消)。 */
    public static void cancel() {
        resetSelection();
    }
}
