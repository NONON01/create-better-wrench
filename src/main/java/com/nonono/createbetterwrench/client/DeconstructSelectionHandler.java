package com.nonono.createbetterwrench.client;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.config.WrenchConfig;
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
@EventBusSubscriber(modid = BetterWrenchMod.MODID, value = Dist.CLIENT)
public final class DeconstructSelectionHandler {

    private static final Object OUTLINE_KEY = "deconstruct_select";

    /** 正常选区框颜色(蓝图蓝)。 */
    private static final int COLOR_OK = 0x6886c5;

    /** 选区超限时的颜色(红)—— 服务端会拒绝这种选区, 画红让玩家一眼看出"选大了"。 */
    private static final int COLOR_TOO_LARGE = 0xE0392B;

    /**
     * 单轴最大边长 —— 与服务端**读同一份配置**({@code config/WrenchConfig} → {@code deconstruct.max_edge})。
     *
     * <p>以前这里是各写一份的常量(必须手工与服务端同步, 否则会出现"框还是蓝的、服务端却拒绝"的割裂体验);
     * 现在两端同一个来源, 这个隐患从根上消除(docs/reference/01-hardcoded-data.md 的 E-1)。</p>
     */
    private static int maxEdge() {
        return WrenchConfig.deconstructMaxEdge();
    }

    private static BlockPos cornerA;
    private static BlockPos previewB;

    private DeconstructSelectionHandler() {
    }

    /** 两角围出的选区是否超过单轴上限(与服务端同一套判据)。 */
    private static boolean tooLarge(BlockPos a, BlockPos b) {
        int limit = maxEdge();
        return Math.abs(a.getX() - b.getX()) + 1 > limit
            || Math.abs(a.getY() - b.getY()) + 1 > limit
            || Math.abs(a.getZ() - b.getZ()) + 1 > limit;
    }

    /**
     * 仅当**主手**持扳手且当前模式为「拆除」时才接管。
     *
     * <p>⚠️ 2026-09-20(用户约定): 扳手在**副手**时"只作普通扳手" ⇒ 本模式不生效, 右键原样交给 Create。</p>
     */
    private static boolean active(Minecraft mc) {
        return mc.player != null
            && mc.player.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH)
            && WrenchModeSwitcher.current == WrenchMode.DECONSTRUCT;
    }

    /** 右键按下: 仅在拆除模式下接管。返回 true 表示本 mod 消费了这次点击。 */
    private static boolean onRightClick() {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc))
            return false;
        // 功能被配置关掉: 提示「此功能未启用」, 吃掉这次点击(什么都不做)
        if (ClientFeatureGate.blockIfDisabled(WrenchMode.DECONSTRUCT))
            return true;
        // 潜行 + 右键 = 放弃当前选区(cancel() 本来就有, 这里把输入接上; 见 docs/reference/03-known-issues.md A-14)
        if (mc.player != null && mc.player.isShiftKeyDown()) {
            cancel();
            return true;
        }
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
        // 配置里"不允许破坏机械动力/红石方块"时范围档被强制锁定 ⇒ 发出去的也是那个档
        // (服务端同样会用 effectiveScope() 覆盖一次, 改包也绕不过去)
        DeconstructScope scope = WrenchConfig.effectiveScope(WrenchModeSwitcher.deconstructScope);
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
                .colored(COLOR_OK)
                .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
                .lineWidth(1 / 16f);
            return;
        }

        // 已选 A: 画 A → 当前视线块 的区域框;超限则整框变红
        previewB = hit != null ? hit : cornerA;
        AABB box = new AABB(Vec3.atLowerCornerOf(cornerA), Vec3.atLowerCornerOf(previewB))
            .expandTowards(1, 1, 1);
        Outliner.getInstance().chaseAABB(OUTLINE_KEY, box)
            .colored(tooLarge(cornerA, previewB) ? COLOR_TOO_LARGE : COLOR_OK)
            .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
            .lineWidth(1 / 16f);
    }

    /**
     * 丢掉当前未完成的选区(角 A + 预览框)。
     *
     * <p>两个调用方: ①客户端登出/切维度时的统一清理(`client/ClientStateReset`);
     * ②玩家在拆除模式下 **Shift + 右键** 主动取消(见 docs/reference/03-known-issues.md A-14)。</p>
     */
    public static void cancel() {
        resetSelection();
    }
}
