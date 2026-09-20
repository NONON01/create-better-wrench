package com.nonono.createbetterwrench.client;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import org.lwjgl.glfw.GLFW;

/**
 * 客户端: **把万能扳手放大画在纯色背景上, 再用原版截图管线导出成 PNG**(开发/发布素材用)。
 *
 * <p>用途: 给 mod 图标 / 平台宣传图提供一张**高分辨率、背景干净**的物品渲染图。
 * 图标合成器见 {@code scripts/gen_mod_icon.ps1}(它把这张 PNG 抠出来当主体)。</p>
 *
 * <h2>为什么不自己建离屏帧缓冲</h2>
 * <p>"离屏渲染" 的常规做法是自建 {@code TextureTarget} + 手写正交投影 + {@code glReadPixels} 回读,
 * 但那一整套 GL 状态管理一旦有偏差, 表现是**静默黑图或直接崩**, 而且无法在没有实机的情况下验证。
 * 这里改成等效但稳得多的路线:</p>
 * <ol>
 *   <li>注册一个**最顶层的 GUI 层**, 在需要时把屏幕铺成**纯洋红 {@code #FF00FF}**, 再把物品放大居中画上去;</li>
 *   <li>连画两帧(第二帧同时)调用原版 {@link Screenshot#grab} —— 它内部就是
 *       {@code takeScreenshot(RenderTarget)} → {@code NativeImage.downloadTexture} → 写 PNG,
 *       是**原版 F2 用的同一套已验证代码**。</li>
 * </ol>
 * <p>⇒ 对使用者来说同样是"按一个键得到一张 PNG", 但**没有一行自写的帧缓冲/投影/回读代码**。</p>
 *
 * <p>洋红之所以能当键控背景: 本模组物品贴图 {@code textures/item/better_wrench.png} 的 20 个不透明颜色里
 * **既没有 {@code #FF00FF} 也没有 {@code #00FF00}**(已实测), 所以洋红不会出现在物品本体上。</p>
 *
 * <p>⚠️ 这是**开发/发布素材工具**, 不属于玩法。**2026-09-20 起只在开发环境启用**:
 * 正式版里连按键都不注册(见 {@link #enabled()}), 所以玩家不会多出 F9 绑定与按键分类项, 也没有顶层 HUD 层
 * (见 docs/07 §6 B-5)。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ItemIconExporter {

    /** 触发导出的按键(默认 F9, 可在“按键设置”里改)。 */
    public static final KeyMapping EXPORT_KEY = new KeyMapping(
        "key." + BetterWrenchMod.MODID + ".export_icon",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9,
        "key.categories." + BetterWrenchMod.MODID);

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "icon_export");

    /** 导出用的键控背景色(纯洋红, ARGB)。 */
    private static final int KEY_BACKGROUND = 0xFFFF00FF;

    /** 物品占屏幕短边的比例。 */
    private static final double ITEM_FRACTION = 0.72;

    /**
     * 截图文件名前缀。
     *
     * <p>⚠️ 实测: 传给 {@code Screenshot.grab} 的名字会被**原样当作文件名**, 原版**不会**再补时间戳或 {@code .png}
     * (第一次导出就得到了一个没有后缀、叫 {@code cbw_wrench_icon} 的文件)。所以这里自己拼上时间戳与后缀。</p>
     */
    private static final String FILE_PREFIX = "cbw_wrench_icon_";

    /**
     * 剩余需要绘制的帧数。0 = 空闲。
     * 用两帧是为了确保 {@code Screenshot.grab} 排队的那一帧**已经画上了覆盖层**。
     */
    private static int framesLeft;

    private ItemIconExporter() {
    }

    /**
     * 是否启用这个开发工具(见 docs/07 §6 B-5)。
     *
     * <p>{@code FMLEnvironment.production} 为 true 即"玩家的正式版" ⇒ **默认完全不启用**:
     * 不注册按键、不注册 HUD 层、tick 里也不响应。它只是做素材用的, 不该出现在玩家的按键设置里。
     * 开发环境({@code gradle runClient}, production=false)自动可用; 想在正式版临时一用时加
     * JVM 参数 {@code -Dcbw.iconExport=true}。</p>
     */
    public static boolean enabled() {
        return !FMLEnvironment.production || Boolean.getBoolean("cbw.iconExport");
    }

    // ---------------------------------------------------------------- 注册

    /** MOD 总线: 注册按键。 */
    @EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        private Keys() {
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            if (!enabled())
                return; // 正式版不注册: 玩家不会看到这个按键/分类项
            event.register(EXPORT_KEY);
        }
    }

    /** MOD 总线: 把覆盖层注册到**最顶层**(要盖住 HUD 与世界)。 */
    @EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Layer {
        private Layer() {
        }

        @SubscribeEvent
        public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
            if (!enabled())
                return;
            event.registerAboveAll(LAYER_ID, ItemIconExporter::render);
        }
    }

    /** GAME 总线: 收到按键就排两帧导出。 */
    @EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class Tick {
        private Tick() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            if (!enabled())
                return;
            Minecraft mc = Minecraft.getInstance();
            while (EXPORT_KEY.consumeClick()) {
                if (mc.player == null || mc.level == null)
                    return; // 不在世界里就不响应(按键已被消费, 不会堆积)
                framesLeft = 2;
                mc.player.displayClientMessage(
                    Component.translatable("msg." + BetterWrenchMod.MODID + ".icon_export.running"), false);
            }
        }
    }

    // ---------------------------------------------------------------- 绘制

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!enabled() || framesLeft <= 0)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        int w = graphics.guiWidth();
        int h = graphics.guiHeight();

        // 1) 铺满纯色键控背景(画在物品之前)
        graphics.fill(0, 0, w, h, KEY_BACKGROUND);

        // 2) 把物品放大居中画上去
        int box = (int) (Math.min(w, h) * ITEM_FRACTION);
        float scale = box / 16.0f;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate((w - box) / 2.0f, (h - box) / 2.0f, 0.0f);
        pose.scale(scale, scale, 1.0f);
        graphics.renderItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance(), 0, 0);
        pose.popPose();

        // 3) 最后一帧触发原版截图(它在帧末执行 -> 抓到的就是上面这一屏)
        framesLeft--;
        if (framesLeft == 0) {
            String fileName = FILE_PREFIX
                + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".png";
            Screenshot.grab(mc.gameDirectory, fileName, mc.getMainRenderTarget(),
                message -> {
                    if (mc.player != null)
                        mc.player.displayClientMessage(message, false);
                });
        }
    }
}
