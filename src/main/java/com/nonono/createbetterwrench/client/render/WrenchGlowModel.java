package com.nonono.createbetterwrench.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.item.WrenchGlowComponent;
import com.nonono.createbetterwrench.item.WrenchShadowSteelComponent;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 万能扳手物品模型的<b>外发光层</b>(BakedModel 多 pass 包装, 1.20.1 版)。
 *
 * <p><b>与 1.21.1 的差异只有两处</b>: ①发光开关读的是 {@code ItemStack} 的 NBT 子标签
 * {@code create_better_wrench.glow}(1.21.1 是数据组件, 语义一致: 存在且为 true = 发光);
 * ②{@code RenderType.create} 用 1.20.1 的公开重载(多两个 {@code boolean} 参数)。渲染算法、几何、
 * 着色器、pass 顺序与取值全部与 1.21.1 相同。</p>
 *
 * <p><b>为什么不用 {@code IClientItemExtensions#getCustomRenderer()}</b>: 物品渲染只在
 * {@code BakedModel#isCustomRenderer()} 为真时才调用自定义渲染器, 而本模组的扳手模型是
 * {@code forge:composite} 模型(其 {@code isCustomRenderer()} 恒为假)。多 pass 直接复用原版物品渲染循环
 * ({@code ItemRenderer#render} 遍历 {@code getRenderPasses} 与各 pass 的 {@code getRenderTypes}),
 * 因此第一人称、第三人称、掉落物、展示框与 GUI 图标全部自动覆盖。</p>
 *
 * <p><b>着色</b>: 复用原版 {@code rendertype_outline} 着色器 —— 它只按贴图 alpha 做 discard,
 * 颜色与不透明度来自顶点色与 {@code ColorModulator}; 颜色/alpha 通过 {@code TexturingStateShard}
 * 的 setup/clear 注入并还原 {@code RenderSystem.setShaderColor}
 * (见 {@code ShaderInstance#apply()} 对 {@code COLOR_MODULATOR} 的赋值), 无需自写着色器。</p>
 *
 * <p><b>外壳做法(定宽, 不自比放大)</b>: 先求每个顶点处相邻面法线的归一化和(同一坐标的 quad 顶点共享一个
 * 法线; 盒子的角点被三个面共享), 再把顶点沿该法线平移固定距离。这样每个小方块都在原地均匀长大, 光边宽度
 * 处处相同: 既不会像"逐 quad 取中心"那样把同一盒子的各个面拆开(毛刺), 也不会像"以包围盒中心等比放大"
 * 那样把远离中心的部件推得更远(上半部偏移)。</p>
 *
 * <p><b>单层</b>: 按最终取舍只保留一层, 没有层间叠加的柔化, 光边外缘是硬边(着色器只判
 * {@code alpha == 0.0}); 世界/手持视角已实机确认可用, GUI 的断续问题由下面的上下文距离解决。</p>
 *
 * <p><b>按上下文分开取外扩距离</b>: {@code getRenderPasses(ItemStack, boolean)} 本身拿不到
 * {@code ItemDisplayContext}, 但 {@code IForgeBakedModel} 的
 * {@code applyTransform(ItemDisplayContext, PoseStack, boolean)} 可以 —— 原版 {@code ItemRenderer.render}
 * 在遍历 pass 之前先经 {@code ClientHooks.handleCameraTransforms(...)} 调用它。因此本类覆写
 * {@code applyTransform} 记下当前上下文, 紧随其后的 {@code getRenderPasses} 就能按 GUI / 非 GUI 取不同的
 * 外扩距离。GUI 图标只有 16 像素且带 3D 显示旋转, 固定宽度会因透视缩短而部分面落不到像素(表现为"断断续续"),
 * 故 GUI 用更粗的值。</p>
 *
 * <p><b>开关(NBT 标记)</b>: 是否发光由 {@code ItemStack.tag.create_better_wrench.glow} 决定 —— 存在且为
 * true 才产出发光 pass; 缺失或 false 时 {@link #getRenderPasses} 直接返回原模型的 pass 列表。正常合成出来的
 * 扳手不带该键, 由铁砧升级写入(见 {@code WrenchGlowItemEvents})。</p>
 *
 * <p><b>pass 顺序</b>: 全部发光 pass 排在全部正常 pass 之前。{@code MultiBufferSource.BufferSource}
 * 在切换共享缓冲的渲染类型时立即冲批, 先请求的先绘制, 因此外圈先落盘、正常层最后覆盖物品内部, 只留外圈。</p>
 *
 * <p><b>附魔光效(按最终取舍不启用)</b>: 发光扳手只带材质标记, 不设置附魔光效, 因此
 * {@code ItemStack#hasFoil()} 为假, 外观只有外发光一圈; 一次性日志仍打印 {@code foil} 字段用于确认这一点。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class WrenchGlowModel implements BakedModel {

    /**
     * 发光色(RGB)。取 Create 的 {@code assets/create/textures/item/refined_radiance.png}
     * (光辉石物品贴图)中最亮、也是出现最多的档位: {@code #FFFFFE}(RGB 255/255/254, 31/108 个不透明像素)。
     * 同贴图的不透明像素亮度中位色为 {@code #F5FAE1}, 平均色为 {@code #E4E9D8}。
     */
    public static final int GLOW_COLOR_RGB = 0xFFFFFE;

    /**
     * 光带的不透明度。
     *
     * <p>取值理由: 光边的"边缘硬度"由着色器的 {@code alpha == 0.0} 判定决定, 与 alpha 无关, 所以 alpha
     * 只影响整条光带的实心程度。0.85 既足够清晰可见, 又保留一点透光感; 想更硬朗改成 1.0 即可。</p>
     */
    public static final float GLOW_ALPHA = 0.85f;

    /**
     * 非 GUI 上下文(手持/掉落物/展示框等)的<b>顶点外扩距离</b>(模型单位)。
     *
     * <p>盒子角点被三个面共享, 沿归一化对角线平移 d 会让盒子每条边各向外长 {@code d / sqrt(3) ≈ 0.577d}。
     * 世界/手持视角下物品被放大绘制(约 100~200 像素高), 0.010 对应每边约 0.006 模型单位、约 0.9 像素,
     * 实机确认观感合适。</p>
     */
    public static final float GLOW_OFFSET_WORLD = 0.010f;

    /**
     * GUI(物品栏/快捷栏)的<b>顶点外扩距离</b>(模型单位), 比世界上下文粗。
     *
     * <p>换算与由来: GUI 图标的 1 个模型单位约合 16 像素 x 显示变换 1.0945 ≈ 17.5 像素, 每边实得约
     * {@code 0.577 x offset x 17.5} 像素 —— 世界用的 0.010 在 GUI 里每边只有约 0.10 像素, 属于亚像素,
     * 光边只在恰好覆盖到像素中心的面才出现, 加上 GUI 显示变换带 3D 旋转(各面被透视缩短的程度不同),
     * 就表现为"部分面落不到像素"的断续, 因此 GUI 必须单独取更粗的值。</p>
     *
     * <p>取值沿革(实机逐轮调定): 0.100 -> 0.020 -> 本轮 0.030。0.030 每边约 0.30 像素, 属于"细而可见"的
     * 区间; 若希望得到稳定的整像素光边, 需要 0.05 以上。</p>
     */
    public static final float GLOW_OFFSET_GUI = 0.030f;

    /** 对照开关: 真 = 发光层排在正常层之后(整件染色观感), 假 = 排在之前(只留外圈, 默认)。 */
    public static final boolean GLOW_AFTER_NORMAL = false;

    /** 顶点记录中颜色所在的 int 下标(与原版 {@code FaceBakery.COLOR_INDEX} 一致)。 */
    private static final int COLOR_INDEX = 3;

    /** 原版物品 quad 的顶点记录长度(int 数): 位置 3 + 颜色 1 + UV 2 + 光照 1 + 法线 1。 */
    private static final int VERTEX_STRIDE = 8;

    /** 写入 quad 的顶点色(白, ABGR): 颜色完全交给着色器颜色, 顶点色只做"不改变颜色"的载体。 */
    private static final int GLOW_VERTEX_COLOR_ABGR = 0xFFFFFFFF;

    /** 顶点坐标量化精度(每模型单位 4096 份), 用于把同一坐标的顶点归并到一起求共享法线。 */
    private static final float POSITION_SCALE = 4096.0f;

    /** 取不到贴图集时的兜底(1.20.1 的物品贴图默认并入方块图集)。 */
    private static final ResourceLocation FALLBACK_ATLAS = TextureAtlas.LOCATION_BLOCKS;

    /** 按图集缓存的发光渲染类型(颜色/alpha 与上下文无关, 因此一个上下文共用一个类型)。 */
    private static final Map<ResourceLocation, RenderType> GLOW_TYPES = new HashMap<>();

    /** 设置/还原着色器颜色时暂存上一个值(渲染为单线程, 静态暂存即可)。 */
    private static final float[] SAVED_SHADER_COLOR = new float[4];

    /** 每个"上下文 + 距离"组合只打印一次诊断信息, 便于同时核对 GUI 与世界两套参数。 */
    private static final Set<String> LOGGED_CONTEXTS = new HashSet<>();

    private final BakedModel base;

    /** 每个 pass 的几何信息(包围盒 + 顶点共享法线), 只算一次。 */
    private final Map<BakedModel, PassGeometry> passGeometry = new IdentityHashMap<>();

    /** 变体模型(暗影钢外观)经 {@link #getOverrides()} 解析后包一层的缓存, 避免每帧新建包装实例。 */
    private final Map<BakedModel, BakedModel> resolvedVariants = new IdentityHashMap<>();

    /** 转发原模型 overrides 的包装, 见 {@link #getOverrides()}。 */
    private final ItemOverrides variantOverrides = new VariantOverrides(this);

    /** 最近一次渲染所用的显示上下文, 由 {@link #applyTransform} 记录、由 {@code getRenderPasses} 读取。 */
    private volatile ItemDisplayContext currentContext = ItemDisplayContext.NONE;

    private WrenchGlowModel(BakedModel base) {
        this.base = base;
    }

    /** 把目标模型包成"每个 pass 配一个发光 pass"; 已包装过或为空时原样返回。 */
    public static BakedModel wrap(BakedModel model) {
        if (model == null || model instanceof WrenchGlowModel)
            return model;
        return new WrenchGlowModel(model);
    }

    /**
     * 记录本次渲染的显示上下文, 并把显示变换原样交给原模型应用。
     *
     * <p>原版 {@code ItemRenderer.render} 在遍历 pass 之前先调用本方法(经
     * {@code ClientHooks.handleCameraTransforms}), 因此随后的 {@link #getRenderPasses} 能读到正确的上下文。
     * 变换只应用一次: 这里委托给原模型, 自己返回 {@code this} 以免重复施加。</p>
     */
    @Override
    public BakedModel applyTransform(ItemDisplayContext transformType, PoseStack poseStack, boolean applyLeftHandTransform) {
        currentContext = transformType;
        base.applyTransform(transformType, poseStack, applyLeftHandTransform);
        return this;
    }

    /**
     * 为原模型的每个 pass 生成一个发光 pass; 默认把发光 pass 全部排在正常 pass 之前。
     *
     * <p>是否发光由 NBT 标记 {@code create_better_wrench.glow} 决定(存在且 true = 发光, 缺失或 false =
     * 不发光): 判为关闭时<b>原样返回原模型的 pass 列表</b>, 不产生任何额外 pass。外扩距离按当前显示上下文
     * 选取: GUI 用 {@link #GLOW_OFFSET_GUI}, 其余上下文用 {@link #GLOW_OFFSET_WORLD}。</p>
     */
    @Override
    public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
        List<BakedModel> original = base.getRenderPasses(stack, fabulous);
        ItemDisplayContext context = currentContext;
        float distance = offsetFor(context);
        boolean glowing = WrenchGlowComponent.isGlowing(stack);
        logOnce(context, distance, stack, original);

        if (!glowing)
            return original;

        List<BakedModel> glow = new ArrayList<>(original.size());
        for (BakedModel pass : original)
            glow.add(new GlowPass(this, pass, distance));

        List<BakedModel> out = new ArrayList<>(original.size() + glow.size());
        if (GLOW_AFTER_NORMAL) {
            out.addAll(original);
            out.addAll(glow);
        } else {
            out.addAll(glow);
            out.addAll(original);
        }
        return out;
    }

    /** 当前上下文应使用的顶点外扩距离。 */
    private static float offsetFor(ItemDisplayContext context) {
        return context == ItemDisplayContext.GUI ? GLOW_OFFSET_GUI : GLOW_OFFSET_WORLD;
    }

    /** 顶层模型的渲染类型仍按原模型给(物品渲染走的是各 pass 自己的重载, 这里只为保持委托一致)。 */
    @Override
    public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        return base.getRenderTypes(stack, fabulous);
    }

    // ---- 以下全部委托给原模型, 保证未包装时的其它行为不变 ----

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
        return base.getQuads(state, side, rand);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return base.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return base.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return base.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return base.isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return base.getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return base.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        ItemOverrides delegate = base.getOverrides();
        return delegate == ItemOverrides.EMPTY ? delegate : variantOverrides;
    }

    /**
     * 把 override 的解析结果也包一层包装。
     *
     * <p>暗影钢外观是物品模型 {@code overrides} 指向的<b>另一个模型</b>: 原版 {@code ItemRenderer#getModel}
     * 会先取顶层模型再调用 {@code getOverrides().resolve(...)}, 于是渲染用的就是那个变体模型, 本包装层会被
     * 绕开。这里转发给原模型解析, 再把解析结果包一层 —— 这样"同时拥有光辉石与暗影钢"的扳手既显示暗影钢外观,
     * 又保留光辉石带来的外发光。解析结果按模型实例缓存, 不会每帧新建包装。</p>
     */
    private static final class VariantOverrides extends ItemOverrides {

        private final WrenchGlowModel owner;

        private VariantOverrides(WrenchGlowModel owner) {
            this.owner = owner;
        }

        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
            BakedModel resolved = owner.base.getOverrides().resolve(model, stack, level, entity, seed);
            if (resolved == null || resolved == model || resolved instanceof WrenchGlowModel)
                return resolved;
            synchronized (owner.resolvedVariants) {
                return owner.resolvedVariants.computeIfAbsent(resolved, WrenchGlowModel::wrap);
            }
        }

        @Override
        public ImmutableList<ItemOverrides.BakedOverride> getOverrides() {
            return owner.base.getOverrides().getOverrides();
        }
    }

    // ---------------------------------------------------------------- 诊断日志

    /**
     * 一次性诊断日志(每个"上下文 + 距离 + 附魔光效 + 标记状态"组合各一次): 列出上下文、实际外扩距离、
     * 是否启用附魔光效、<b>是否读到标记及其值、最终是否发光</b>, 以及每个 pass 的 quad 包围盒与中心。
     * 若某个 pass 的包围盒与整体差异明显(例如子模型自带平移), 这条日志会直接给出数值依据; 各 pass 中心
     * 接近则说明偏移不是"部件不在同一坐标空间"造成的。
     */
    private void logOnce(ItemDisplayContext context, float distance, ItemStack stack, List<BakedModel> passes) {
        boolean foil = stack.hasFoil();
        boolean componentPresent = WrenchGlowComponent.hasComponent(stack);
        Boolean componentValue = WrenchGlowComponent.componentValue(stack);
        boolean glowing = WrenchGlowComponent.isGlowing(stack);
        boolean shadowSteel = WrenchShadowSteelComponent.isShadowSteel(stack);
        String logKey = context.getSerializedName() + "|" + distance + "|" + foil + "|" + componentPresent
            + "|" + componentValue + "|" + glowing + "|" + shadowSteel;
        synchronized (LOGGED_CONTEXTS) {
            if (!LOGGED_CONTEXTS.add(logKey))
                return;
        }

        float[] union = {
            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY,
        };
        StringBuilder detail = new StringBuilder();
        int index = 0;
        for (BakedModel pass : passes) {
            float[] bounds = geometryOf(pass).bounds();
            union[0] = Math.min(union[0], bounds[0]);
            union[1] = Math.min(union[1], bounds[1]);
            union[2] = Math.min(union[2], bounds[2]);
            union[3] = Math.max(union[3], bounds[3]);
            union[4] = Math.max(union[4], bounds[4]);
            union[5] = Math.max(union[5], bounds[5]);
            detail.append(String.format(
                "%n  pass[%d] bbox=(%.4f, %.4f, %.4f)..(%.4f, %.4f, %.4f) center=(%.4f, %.4f, %.4f)",
                index++, bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5],
                (bounds[0] + bounds[3]) / 2.0f, (bounds[1] + bounds[4]) / 2.0f, (bounds[2] + bounds[5]) / 2.0f));
        }
        detail.append(String.format("%n  union center=(%.4f, %.4f, %.4f) size=(%.4f, %.4f, %.4f)",
            (union[0] + union[3]) / 2.0f, (union[1] + union[4]) / 2.0f, (union[2] + union[5]) / 2.0f,
            union[3] - union[0], union[4] - union[1], union[5] - union[2]));

        BetterWrenchMod.LOGGER.info(
            "wrench glow: 已进入物品渲染路径; mode=constant-width, context={}, offset={}, alpha={}, rgb=#{}, foil={}, glowMarker={}, glowMarkerValue={}, glowing={}, shadowSteel={}, passCount={}{}",
            context.getSerializedName(), distance, GLOW_ALPHA, Integer.toHexString(GLOW_COLOR_RGB),
            foil, componentPresent, componentValue, glowing, shadowSteel, passes.size(), detail);
    }

    // ---------------------------------------------------------------- 几何

    /** 某个 pass 的几何信息(包围盒 + 顶点共享法线), 首次调用时计算并缓存。 */
    private synchronized PassGeometry geometryOf(BakedModel pass) {
        PassGeometry cached = passGeometry.get(pass);
        if (cached != null)
            return cached;

        float[] bounds = {
            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY,
        };
        Map<Long, float[]> sums = new HashMap<>();
        RandomSource rand = RandomSource.create();
        for (Direction side : Direction.values()) {
            rand.setSeed(42L);
            collectNormals(pass.getQuads(null, side, rand), bounds, sums);
        }
        rand.setSeed(42L);
        collectNormals(pass.getQuads(null, null, rand), bounds, sums);

        for (float[] normal : sums.values()) {
            float length = (float) Math.sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2]);
            if (length > 1.0e-4f) {
                normal[0] /= length;
                normal[1] /= length;
                normal[2] /= length;
            } else {
                normal[0] = 0.0f;
                normal[1] = 0.0f;
                normal[2] = 0.0f;
            }
        }

        PassGeometry geometry = new PassGeometry(bounds, sums);
        passGeometry.put(pass, geometry);
        return geometry;
    }

    /**
     * 累积包围盒与"顶点坐标 -> 相邻面法线之和"。
     *
     * <p>同一个坐标上的顶点会被多个 quad 共享(盒子的角点被三个面共享), 把这些面的法线相加再归一化,
     * 得到的就是该角点的外扩方向 —— 沿它平移即把整个盒子在原地均匀放大。</p>
     */
    private static void collectNormals(List<BakedQuad> quads, float[] bounds, Map<Long, float[]> sums) {
        for (BakedQuad quad : quads) {
            int[] vertices = quad.getVertices();
            if (vertices.length < VERTEX_STRIDE * 4)
                continue;
            Direction direction = quad.getDirection();
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * VERTEX_STRIDE;
                float x = Float.intBitsToFloat(vertices[offset]);
                float y = Float.intBitsToFloat(vertices[offset + 1]);
                float z = Float.intBitsToFloat(vertices[offset + 2]);
                bounds[0] = Math.min(bounds[0], x);
                bounds[1] = Math.min(bounds[1], y);
                bounds[2] = Math.min(bounds[2], z);
                bounds[3] = Math.max(bounds[3], x);
                bounds[4] = Math.max(bounds[4], y);
                bounds[5] = Math.max(bounds[5], z);

                if (direction == null)
                    continue;
                float[] sum = sums.computeIfAbsent(key(x, y, z), ignored -> new float[3]);
                sum[0] += direction.getNormal().getX();
                sum[1] += direction.getNormal().getY();
                sum[2] += direction.getNormal().getZ();
            }
        }
    }

    /** 把顶点坐标量化成一个长整型键(21 位 x 3, 足以区分 1/16 模型单位的网格)。 */
    private static long key(float x, float y, float z) {
        return ((long) Math.round(x * POSITION_SCALE) & 0x1FFFFFL) << 42
            | ((long) Math.round(y * POSITION_SCALE) & 0x1FFFFFL) << 21
            | ((long) Math.round(z * POSITION_SCALE) & 0x1FFFFFL);
    }

    /** 某个 pass 的发光 quad 集合(顶点沿共享法线外扩给定距离, 并写白顶点色)。 */
    private List<BakedQuad> glowQuads(BakedModel pass, List<BakedQuad> source, float distance) {
        if (source.isEmpty())
            return List.of();
        Map<Long, float[]> normals = geometryOf(pass).vertexNormals();
        List<BakedQuad> out = new ArrayList<>(source.size());
        for (BakedQuad quad : source)
            out.add(offset(quad, normals, distance));
        return out;
    }

    /**
     * 把一个 quad 的四个顶点沿各自的共享法线外扩固定距离, 并把顶点色写成白色。
     *
     * <p>顶点数组按每顶点 {@value #VERTEX_STRIDE} 个 int 打包(原版物品/方块 quad 格式), 位置为前三个
     * float、颜色为第 {@value #COLOR_INDEX} 个 int; 除位置与颜色外的字节(UV、光照、法线)原样保留,
     * 因此外扩后的贴图与本体完全一致。</p>
     *
     * <p>共享法线查询失败(顶点不共享面, 例如零厚度面)时退回该 quad 自身的方向法线, 保证不会把顶点留空。</p>
     */
    private static BakedQuad offset(BakedQuad quad, Map<Long, float[]> normals, float distance) {
        int[] source = quad.getVertices();
        if (source.length < VERTEX_STRIDE * 4)
            return quad;
        int[] copy = source.clone();
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * VERTEX_STRIDE;
            copy[offset + COLOR_INDEX] = GLOW_VERTEX_COLOR_ABGR;

            float x = Float.intBitsToFloat(source[offset]);
            float y = Float.intBitsToFloat(source[offset + 1]);
            float z = Float.intBitsToFloat(source[offset + 2]);
            float nx = 0.0f;
            float ny = 0.0f;
            float nz = 0.0f;
            float[] shared = normals.get(key(x, y, z));
            if (shared != null && (shared[0] != 0.0f || shared[1] != 0.0f || shared[2] != 0.0f)) {
                nx = shared[0];
                ny = shared[1];
                nz = shared[2];
            } else if (quad.getDirection() != null) {
                nx = quad.getDirection().getNormal().getX();
                ny = quad.getDirection().getNormal().getY();
                nz = quad.getDirection().getNormal().getZ();
            }

            copy[offset] = Float.floatToRawIntBits(x + nx * distance);
            copy[offset + 1] = Float.floatToRawIntBits(y + ny * distance);
            copy[offset + 2] = Float.floatToRawIntBits(z + nz * distance);
        }
        return new BakedQuad(copy, quad.getTintIndex(), quad.getDirection(), quad.getSprite(),
            quad.isShade(), quad.hasAmbientOcclusion());
    }

    /** 取该 pass 所在贴图集的位置(物品 quad 使用图集 UV, outline 着色器的 Sampler0 必须同图集)。 */
    private static ResourceLocation atlasLocation(BakedModel model) {
        TextureAtlasSprite sprite = model.getParticleIcon();
        ResourceLocation atlas = sprite == null ? null : sprite.atlasLocation();
        return atlas == null ? FALLBACK_ATLAS : atlas;
    }

    /** 一个 pass 的几何缓存: 包围盒(minX..maxZ)与顶点共享法线。 */
    private static final class PassGeometry {

        private final float[] bounds;
        private final Map<Long, float[]> vertexNormals;

        private PassGeometry(float[] bounds, Map<Long, float[]> vertexNormals) {
            this.bounds = bounds;
            this.vertexNormals = vertexNormals;
        }

        private float[] bounds() {
            return bounds;
        }

        private Map<Long, float[]> vertexNormals() {
            return vertexNormals;
        }
    }

    // ---------------------------------------------------------------- 渲染类型

    /**
     * 发光渲染类型: 复用原版 outline 着色器(恒定色 + 贴图 alpha 遮罩), 输出到主缓冲、保留深度测试、
     * 只写颜色不写深度、双面可见, 并开启普通半透明混合。
     *
     * <p>不透明度与颜色通过 {@code TexturingStateShard} 注入: setup 时把当前着色器颜色暂存后设为发光色与
     * {@link #GLOW_ALPHA}, clear 时还原。{@code ShaderInstance#apply()} 会把
     * {@code RenderSystem.getShaderColor()} 写进 {@code ColorModulator} uniform, 因此无需自写着色器。</p>
     *
     * <p>1.20.1 的 {@code RenderType.create} 公开重载比 1.21.1 多两个 {@code boolean}
     * ({@code affectsCrumbling} / {@code sortOnUpload}), 这里都传 false, 其余参数与 1.21.1 一致。</p>
     */
    private static RenderType glowRenderType(ResourceLocation atlas) {
        RenderType cached = GLOW_TYPES.get(atlas);
        if (cached != null)
            return cached;

        RenderStateShard.TexturingStateShard colorState = new RenderStateShard.TexturingStateShard(
            "cbw_wrench_glow_color",
            () -> {
                float[] current = RenderSystem.getShaderColor();
                System.arraycopy(current, 0, SAVED_SHADER_COLOR, 0, 4);
                RenderSystem.setShaderColor(red(), green(), blue(), GLOW_ALPHA);
            },
            () -> RenderSystem.setShaderColor(SAVED_SHADER_COLOR[0], SAVED_SHADER_COLOR[1],
                SAVED_SHADER_COLOR[2], SAVED_SHADER_COLOR[3]));

        RenderType created = RenderType.create(
            "cbw_wrench_glow",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            false,
            RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeOutlineShader))
                .setTextureState(new RenderStateShard.TextureStateShard(atlas, false, false))
                .setTransparencyState(GLOW_TRANSPARENCY)
                .setTexturingState(colorState)
                .setCullState(new RenderStateShard.CullStateShard(false))
                .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, false))
                .createCompositeState(false));
        GLOW_TYPES.put(atlas, created);
        return created;
    }

    /** 发光色的红分量(0..1)。 */
    private static float red() {
        return ((GLOW_COLOR_RGB >> 16) & 0xFF) / 255.0f;
    }

    /** 发光色的绿分量(0..1)。 */
    private static float green() {
        return ((GLOW_COLOR_RGB >> 8) & 0xFF) / 255.0f;
    }

    /** 发光色的蓝分量(0..1)。 */
    private static float blue() {
        return (GLOW_COLOR_RGB & 0xFF) / 255.0f;
    }

    /** 普通半透明混合(与 Create/原版半透明同口径): setup 开混合, clear 关混合并还原默认混合函数。 */
    private static final RenderStateShard.TransparencyStateShard GLOW_TRANSPARENCY =
        new RenderStateShard.TransparencyStateShard(
            "cbw_wrench_glow_translucent",
            () -> {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            },
            () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });

    /**
     * 某一个正常 pass 的发光孪生体: quad 取自该 pass, 顶点沿共享法线外扩 {@code distance},
     * 渲染类型为发光类型。
     *
     * <p>距离在构造时固定(由 {@code getRenderPasses} 按显示上下文选定), 因此同一个 pass 在 GUI 与世界
     * 两种上下文下会得到不同的外扩距离。</p>
     *
     * <p>其余查询一律委托该 pass, 且 {@link #isCustomRenderer()} 恒为假, 因此不会被误当成需要自定义
     * 渲染器的模型; {@link #getRenderPasses} 返回自身, 不参与进一步拆分。</p>
     */
    private static final class GlowPass implements BakedModel {

        private final WrenchGlowModel owner;
        private final BakedModel base;
        private final float distance;

        private GlowPass(WrenchGlowModel owner, BakedModel base, float distance) {
            this.owner = owner;
            this.base = base;
            this.distance = distance;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
            return owner.glowQuads(base, base.getQuads(state, side, rand), distance);
        }

        @Override
        public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            return List.of(glowRenderType(atlasLocation(base)));
        }

        /** 单 pass: 发光层不再拆分。 */
        @Override
        public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            return List.of(this);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return base.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return base.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return base.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return base.getParticleIcon();
        }

        @Override
        public ItemTransforms getTransforms() {
            return base.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return base.getOverrides();
        }
    }
}
