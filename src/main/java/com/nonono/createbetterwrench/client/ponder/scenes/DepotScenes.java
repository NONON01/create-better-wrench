package com.nonono.createbetterwrench.client.ponder.scenes;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * **归在置物台、同时关联到万能扳手**的 7 段场景(用户 2026-09-23 定的顺序与标题)。
 *
 * <pre>
 *   使用万能扳手进行加工 → {@link #process}     (总述: 六种加工在这几段里逐一演示)
 *   进行装配             → {@link #assembly}    (以精密构件为例)
 *   进行注液             → {@link #filling}     (以烈焰蛋糕为例)
 *   进行洗涤             → {@link #splash}      (以沙砾 / 混凝土粉末为例)
 *   进行熔炼             → {@link #blasting}    (以粗铁→铁锭、圆石→石头→平滑石为例)
 *   进行烤制             → {@link #smoking}     (以生牛肉→熟牛肉为例)
 *   进行缠魂             → {@link #haunting}    (以沙子→灵魂沙为例)
 * </pre>
 *
 * <p><b>结构文件</b>都是 5×5 底板 + 一个 {@code create:depot}(坐标 (2,1,2); 缠魂那段底板里换成灵魂沙)。
 * 台面上的物品不是写在结构里, 而是用 Create 自己的办法**直接改方块实体 NBT**
 * (Create 的 {@code FanScenes.processing} 就是这么做的, 已 javap 核对):
 * {@code modifyBlockEntityNBT(select().position(depot), DepotBlockEntity.class,
 *   nbt -> nbt.put("HeldItem", new TransportedItemStack(stack).serializeNBT(provider)))}。
 * 于是"物品变化"= 再调一次 {@link #hold} 换掉它。</p>
 *
 * <p>粒子用 {@code effects().emitParticles(...)} 复刻我们游戏内那次加工的反馈
 * (洗涤=蓝色尘+SPIT, 熔炼=大烟, 烤制=POOF, 缠魂=灵魂火焰+烟 —— 见 {@code AssembleLogic#playFanFeedback})。</p>
 */
public final class DepotScenes {

    /** 置物台的位置(所有加工场景的结构都是 5×5 底板 + 这里一个置物台)。 */
    private static final BlockPos DEPOT = new BlockPos(2, 1, 2);

    private DepotScenes() {
    }

    // ------------------------------------------------------------------ 使用万能扳手进行加工(总述)

    /**
     * 加工总述: 先锁定置物台, 再手持工具/材料右击。
     *
     * <p>写法照 {@code FanScenes.processing} 那套(Create 的"置物台 + 加工"标准分镜):
     * 底板 → 机器 → {@code showControls(...).withItem(...)} 演"手上拿什么" → 短句陈述文字
     * ({@code .pointAt(...).placeNearTarget()}) → 粒子用 {@code amount=1, ticks=60} → 台面上的物品换掉。</p>
     */
    public static void process(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process",
            "Processing Items using the Universal Wrench");

        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 20)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance());
        scene.idle(20);
        scene.overlay().showText(70)
            .text("Right-clicking a Depot will lock it")
            .pointAt(util.vector().topOf(DEPOT))
            .placeNearTarget()
            .attachKeyFrame();
        scene.effects().indicateSuccess(DEPOT);
        scene.idle(70);

        hold(scene, util, new ItemStack(Items.RAW_IRON));
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 20)
            .withItem(new ItemStack(Items.LAVA_BUCKET));
        scene.idle(20);
        scene.overlay().showText(70)
            .text("Right-clicking it with a tool will process the item on top")
            .pointAt(util.vector().topOf(DEPOT))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(60);

        puff(scene, util, ParticleTypes.LARGE_SMOKE, 1, 60);
        hold(scene, util, new ItemStack(Items.IRON_INGOT));
        scene.effects().indicateSuccess(DEPOT);
        scene.idle(40);

        scene.overlay().showText(80)
            .text("Six kinds can be processed: Assembly, Filling, Washing, Blasting, Smoking and Haunting")
            .pointAt(util.vector().topOf(DEPOT))
            .placeNearTarget();
        scene.idle(80);
    }

    // ------------------------------------------------------------------ 进行装配

    /** 装配: 台面上放半成品, 右击投入下一件材料, 序列装配原地推进 → 精密构件。 */
    public static void assembly(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process_assembly", "Assembly");

        hold(scene, util, AllItems.GOLDEN_SHEET.asStack());
        scene.overlay().showText(80)
            .text("A half-finished item will stay on the Depot - here a Golden Sheet")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(90);

        hold(scene, util, AllItems.INCOMPLETE_PRECISION_MECHANISM.asStack());
        scene.effects().indicateSuccess(DEPOT);
        scene.overlay().showText(80)
            .text("Right-clicking with the next material will advance the sequence in place")
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 40)
            .withItem(new ItemStack(AllBlocks.COGWHEEL.get()));
        scene.idle(90);

        hold(scene, util, AllItems.PRECISION_MECHANISM.asStack());
        scene.effects().indicateSuccess(DEPOT);
        scene.overlay().showText(80)
            .text("The Golden Sheet will be worked all the way into a Precision Mechanism");
        scene.idle(90);
    }

    // ------------------------------------------------------------------ 进行注液

    /** 注液: 岩浆桶右击锁定的置物台 = 注液, 烈焰蛋糕胚 → 烈焰蛋糕。 */
    public static void filling(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process_filling", "Filling");

        hold(scene, util, AllItems.BLAZE_CAKE_BASE.asStack());
        scene.overlay().showText(70)
            .text("Right-clicking it with a Lava Bucket will pour fluid in")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 40)
            .withItem(new ItemStack(Items.LAVA_BUCKET));
        scene.idle(50);

        puff(scene, util, ParticleTypes.FLAME, 1, 60);
        puff(scene, util, ParticleTypes.LAVA, 1, 60);
        hold(scene, util, AllItems.BLAZE_CAKE.asStack());
        scene.effects().indicateSuccess(DEPOT);
        scene.idle(50);

        scene.overlay().showText(80)
            .text("A Blaze Cake Base will be filled into a Blaze Cake");
        scene.idle(60);

        scene.overlay().showText(70)
            .text("The bucket will not be consumed");
        scene.idle(80);
    }

    // ------------------------------------------------------------------ 进行洗涤

    /** 洗涤: 水桶右击 = 冲洗; 沙砾→燧石、混凝土粉末→混凝土。 */
    public static void splash(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process_splash", "Washing");

        hold(scene, util, new ItemStack(Items.GRAVEL));
        scene.overlay().showText(70)
            .text("Right-clicking it with a Water Bucket will wash it")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 40)
            .withItem(new ItemStack(Items.WATER_BUCKET));
        scene.idle(40);

        wash(scene, util);
        hold(scene, util, new ItemStack(Items.FLINT));
        scene.effects().indicateSuccess(DEPOT);
        scene.overlay().showText(70)
            .text("Gravel will be washed into Flint, and the odd Iron Nugget");
        scene.idle(80);

        hold(scene, util, new ItemStack(Items.WHITE_CONCRETE_POWDER));
        scene.idle(20);
        wash(scene, util);
        hold(scene, util, new ItemStack(Items.WHITE_CONCRETE));
        scene.effects().indicateSuccess(DEPOT);
        scene.overlay().showText(70)
            .text("Concrete Powder will be washed into Concrete");
        scene.idle(80);

        scene.overlay().showText(70)
            .text("One click will wash a whole stack, and the bucket will not be consumed");
        scene.idle(90);
    }

    // ------------------------------------------------------------------ 进行熔炼

    /** 熔炼: 岩浆桶右击 = 高温; 粗铁→铁锭, 圆石→石头→平滑石。 */
    public static void blasting(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process_blasting", "Blasting");

        hold(scene, util, new ItemStack(Items.RAW_IRON));
        scene.overlay().showText(70)
            .text("Right-clicking it with a Lava Bucket will blast it")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 40)
            .withItem(new ItemStack(Items.LAVA_BUCKET));
        scene.idle(40);

        puff(scene, util, ParticleTypes.LARGE_SMOKE, 1, 60);
        hold(scene, util, new ItemStack(Items.IRON_INGOT));
        scene.effects().indicateSuccess(DEPOT);
        scene.overlay().showText(70)
            .text("Raw Iron will be blasted into an Iron Ingot");
        scene.idle(80);

        hold(scene, util, new ItemStack(Items.COBBLESTONE));
        scene.idle(20);
        puff(scene, util, ParticleTypes.LARGE_SMOKE, 1, 60);
        hold(scene, util, new ItemStack(Items.STONE));
        scene.effects().indicateSuccess(DEPOT);
        scene.idle(30);
        puff(scene, util, ParticleTypes.LARGE_SMOKE, 1, 60);
        hold(scene, util, new ItemStack(Items.SMOOTH_STONE));
        scene.effects().indicateSuccess(DEPOT);
        scene.overlay().showText(80)
            .text("Cobblestone will be blasted into Stone, and Stone into Smooth Stone");
        scene.idle(90);
    }

    // ------------------------------------------------------------------ 进行烤制

    /** 烤制: 打火石右击 = 烟熏; 生牛肉→熟牛肉(灵魂底座上会优先缠魂, 没配方再回退烤制)。 */
    public static void smoking(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process_smoking", "Smoking");

        hold(scene, util, new ItemStack(Items.BEEF));
        scene.overlay().showText(70)
            .text("Right-clicking it with Flint and Steel will smoke it")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 40)
            .withItem(new ItemStack(Items.FLINT_AND_STEEL));
        scene.idle(40);

        puff(scene, util, ParticleTypes.POOF, 1, 60);
        hold(scene, util, new ItemStack(Items.COOKED_BEEF));
        scene.effects().indicateSuccess(DEPOT);
        scene.overlay().showText(70)
            .text("Raw Beef will be smoked into a Steak");
        scene.idle(80);

        scene.overlay().showText(85)
            .text("On a soul base Haunting will be tried first, and Smoking only as a fallback");
        scene.idle(90);
    }

    // ------------------------------------------------------------------ 进行缠魂

    /** 缠魂: 置物台架在灵魂沙/灵魂土上 + 打火石 = 缠魂; 沙子→灵魂沙; 顺便演示"锁定后缓慢冒灵魂火焰"。 */
    public static void haunting(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process_haunting", "Haunting");
        scene.world().showSection(util.select().position(2, 0, 2), Direction.DOWN);
        scene.idle(10);

        hold(scene, util, new ItemStack(Items.SAND));
        scene.overlay().showText(75)
            .text("The Depot can be placed on Soul Sand or Soul Soil")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(2, 0, 2));
        scene.idle(70);

        // 台座底下慢慢往上冒的灵魂火焰(游戏里锁定的置物台也会这样, 见 DepotSoulFlames)
        soulFlames(scene, util, 60);
        scene.overlay().showText(75)
            .text("A locked Depot on a soul base will slowly emit soul flames");
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 40)
            .withItem(new ItemStack(Items.FLINT_AND_STEEL));
        scene.overlay().showText(75)
            .text("Right-clicking it with Flint and Steel will haunt it")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(40);

        puff(scene, util, ParticleTypes.SOUL_FIRE_FLAME, 1, 60);
        puff(scene, util, ParticleTypes.SMOKE, 1, 60);
        hold(scene, util, new ItemStack(Items.SOUL_SAND));
        scene.effects().indicateSuccess(DEPOT);
        scene.overlay().showText(70)
            .text("Sand will be haunted into Soul Sand");
        scene.idle(90);
    }

    // ------------------------------------------------------------------ 公共套路

    /** 每段加工场景的共同开场: 标题 → 底板 → 置物台。 */
    private static CreateSceneBuilder start(SceneBuilder builder, SceneBuildingUtil util, String titleId, String title) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(titleId, title);
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);
        scene.world().showSection(util.select().position(DEPOT), Direction.DOWN);
        scene.idle(5);
        return scene;
    }

    /**
     * 把某件物品摆到置物台上(直接改方块实体 NBT —— Create 的 {@code FanScenes} 用的就是这个键 {@code HeldItem})。
     * 再调一次就是"台面上的物品换了"。
     */
    private static void hold(CreateSceneBuilder scene, SceneBuildingUtil util, ItemStack stack) {
        scene.world().modifyBlockEntityNBT(util.select().position(DEPOT), DepotBlockEntity.class,
            nbt -> nbt.put("HeldItem",
                new TransportedItemStack(stack.copy()).serializeNBT(scene.world().getHolderLookupProvider())));
    }

    /** 台面上方喷一小撮粒子(与游戏内加工反馈同款)。 */
    private static void puff(CreateSceneBuilder scene, SceneBuildingUtil util, ParticleOptions particle,
                             float amount, int ticks) {
        Vec3 at = util.vector().topOf(DEPOT).add(0, 0.25, 0);
        scene.effects().emitParticles(at, scene.effects().simpleParticleEmitter(particle, new Vec3(0, 0.05, 0)),
            amount, ticks);
    }

    /** 洗涤专用的蓝色尘 + SPIT(与 {@code AssembleLogic#playFanFeedback} 的洗涤分支一致)。 */
    private static void wash(CreateSceneBuilder scene, SceneBuildingUtil util) {
        puff(scene, util, new DustParticleOptions(new Vector3f(0f, 0x55 / 255f, 1f), 1f), 8, 25);
        puff(scene, util, ParticleTypes.SPIT, 1, 60);
    }

    /**
     * 缠魂场景里"从置物台下方慢慢冒出来"的灵魂火焰(现实游戏里由 {@code DepotSoulFlames} 负责)。
     *
     * <p>⚠️ 不能只在置物台**中心**喷: 置物台的模型是整格底座(0~11/16 高、横向铺满),
     * 中心处的粒子会被模型挡住 ⇒ 与游戏内一样, 沿**四条竖边外侧一丝**各喷一处。</p>
     */
    private static void soulFlames(CreateSceneBuilder scene, SceneBuildingUtil util, int ticks) {
        Vec3 center = util.vector().centerOf(DEPOT);
        double out = 0.53;                 // = 半格 + 0.03, 正好在方块面外侧
        double y = center.y - 0.42;        // 贴近台座底部(灵魂沙那一层的上沿)
        Vec3[] rims = {
            new Vec3(center.x, y, center.z - out),
            new Vec3(center.x + out, y, center.z),
            new Vec3(center.x, y, center.z + out),
            new Vec3(center.x - out, y, center.z)
        };
        for (Vec3 rim : rims) {
            scene.effects().emitParticles(rim,
                scene.effects().simpleParticleEmitter(ParticleTypes.SOUL_FIRE_FLAME, new Vec3(0, 0.01, 0)),
                0.2f, ticks);
        }
    }
}
