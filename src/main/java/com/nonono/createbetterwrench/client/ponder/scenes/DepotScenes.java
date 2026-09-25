package com.nonono.createbetterwrench.client.ponder.scenes;

import java.util.ArrayList;
import java.util.List;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * **归在置物台、同时关联到万能扳手**的 7 段场景(用户 2026-09-23 定的顺序与标题)。
 *
 * <pre>
 *   使用万能扳手进行加工 → {@link #process}     (总述: 锁定 → 加工 → 六种都支持)
 *   进行装配             → {@link #assembly}    (5 轮 小齿轮/大齿轮/铁粒 ⇒ 精密构件)
 *   进行注液             → {@link #filling}     (烈焰蛋糕胚 + 岩浆桶 ⇒ 烈焰蛋糕, 多个原料按配方算)
 *   进行洗涤             → {@link #splash}      (沙砾 ⇒ 燧石; 混凝土粉末 ⇒ 混凝土)
 *   进行熔炼             → {@link #blasting}    (粗铁 ⇒ 铁锭; 圆石 ⇒ 石头 ⇒ 平滑石)
 *   进行烤制             → {@link #smoking}     (生牛肉 ⇒ 熟牛肉, 打火石掉 1 点耐久)
 *   进行缠魂             → {@link #haunting}    (灵魂沙底座 + 沙子 ⇒ 灵魂沙)
 * </pre>
 *
 * <p><b>结构文件</b>都是 5×5 底板 + 一个 {@code create:depot}(坐标 (2,1,2); 缠魂那段底板里换成灵魂沙)。
 * 台面上的物品不是写在结构里, 而是用 Create 自己的办法**直接改方块实体 NBT**
 * ({@code modifyBlockEntityNBT(..., nbt -> nbt.put("HeldItem", new TransportedItemStack(stack).serializeNBT(provider)))}),
 * 所以"物品变化"= 再调一次 {@link #hold} 换掉它; {@code ItemStack.EMPTY} 就是"台面空了"。</p>
 *
 * <p><b>文案</b>照用户 2026-09-23 给的句子: 先讲"台面上有什么样的原料", 再讲"用什么右击会发生什么";
 * 加括号的举例(如金板)是**用户要求保留**的写法。</p>
 *
 * <p><b>右击反馈</b>: 右击本身没有特效的地方(锁定置物台)给黄色选框({@link #SELECT}) +
 * {@code showControls(...).withItem(...).rightClick()} 的右击图标。</p>
 */
public final class DepotScenes {

    /** 置物台的位置(所有加工场景的结构都是 5×5 底板 + 这里一个置物台)。 */
    private static final BlockPos DEPOT = new BlockPos(2, 1, 2);

    /** “黄色选框”。⚠️ Ponder 调色板没有纯黄, {@code OUTPUT = 0xDDC166} 是唯一的金黄, 右击反馈用它。 */
    private static final PonderPalette SELECT = PonderPalette.OUTPUT;

    /** 精密构件的序列装配配方 = 5 轮 × (小齿轮 → 大齿轮 → 铁粒), 与 Create 的配方 json 一致。 */
    private static final ItemStack[] MECHANISM_SEQUENCE = {
        new ItemStack(AllBlocks.COGWHEEL.get()),
        new ItemStack(AllBlocks.LARGE_COGWHEEL.get()),
        new ItemStack(Items.IRON_NUGGET)
    };
    private static final int MECHANISM_LOOPS = 5;

    private DepotScenes() {
    }

    // ------------------------------------------------------------------ 使用万能扳手进行加工(总述)

    /** 加工总述: 右击锁定 → 锁定的台面可以加工 → 支持六种加工。 */
    public static void process(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process",
            "Processing Items using the Universal Wrench");

        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 20)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance())
            .rightClick();
        scene.idle(20);
        scene.overlay().showOutline(SELECT, "cbw_depot_lock", util.select().position(DEPOT), 60);
        scene.effects().indicateSuccess(DEPOT);
        scene.overlay().showText(70)
            .text("Right-clicking a Depot will lock it")
            .pointAt(util.vector().topOf(DEPOT))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(70);

        hold(scene, util, new ItemStack(Items.RAW_IRON));
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 20)
            .withItem(new ItemStack(Items.LAVA_BUCKET))
            .rightClick();
        scene.idle(20);
        scene.overlay().showText(70)
            .text("While locked, items on top can be processed")
            .pointAt(util.vector().topOf(DEPOT))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(30);

        puff(scene, util, ParticleTypes.LARGE_SMOKE, 1, 60);
        hold(scene, util, new ItemStack(Items.IRON_INGOT));
        scene.effects().indicateSuccess(DEPOT);
        scene.idle(60);

        scene.overlay().showText(80)
            .text("Processing covers Assembly, Filling, Washing, Blasting, Smoking and Haunting")
            .pointAt(util.vector().topOf(DEPOT))
            .placeNearTarget();
        scene.idle(80);
    }

    // ------------------------------------------------------------------ 进行装配

    /**
     * 装配: 台面上放原料(金板) → 用相应材料一件件投 → 跑完整的精密构件流程
     * (5 轮 小齿轮 / 大齿轮 / 铁粒) → 成品弹出后消失。
     */
    public static void assembly(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process_assembly", "Assembly");

        hold(scene, util, AllItems.GOLDEN_SHEET.asStack());
        scene.idle(5);
        scene.overlay().showText(80)
            .text("When a usable assembly material is on the Depot (a Golden Sheet, for example)")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(20);

        hold(scene, util, AllItems.INCOMPLETE_PRECISION_MECHANISM.asStack());
        scene.effects().indicateSuccess(DEPOT);
        scene.idle(60);

        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 20)
            .withItem(new ItemStack(AllBlocks.COGWHEEL.get()))
            .rightClick();
        scene.overlay().showText(80)
            .text("Adding the matching materials will assemble it")
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT))
            .attachKeyFrame();
        scene.idle(20);

        // 完整的精密构件流程: 5 轮 × (小齿轮 → 大齿轮 → 铁粒)
        for (int round = 0; round < MECHANISM_LOOPS; round++) {
            for (ItemStack material : MECHANISM_SEQUENCE) {
                scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 14)
                    .withItem(material)
                    .rightClick();
                scene.idle(6);
                puff(scene, util, ParticleTypes.POOF, 1, 40);
            }
            scene.effects().indicateSuccess(DEPOT);
        }

        hold(scene, util, AllItems.PRECISION_MECHANISM.asStack());
        scene.effects().indicateSuccess(DEPOT);
        scene.idle(15);

        // 成品弹出后消失
        hold(scene, util, ItemStack.EMPTY);
        scene.idle(5);
        eject(scene, util, AllItems.PRECISION_MECHANISM.asStack(), 0, 0.3, 20);
        scene.idle(20);
    }

    // ------------------------------------------------------------------ 进行注液

    /** 注液: 台面上的原料 + 相应的流体桶; 台面上有几个原料就按配方注几个。 */
    public static void filling(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process_filling", "Filling");

        hold(scene, util, AllItems.BLAZE_CAKE_BASE.asStack());
        scene.idle(5);
        scene.overlay().showText(70)
            .text("When a fillable material is on the Depot (a Blaze Cake Base, for example)")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 20)
            .withItem(new ItemStack(Items.LAVA_BUCKET))
            .rightClick();
        scene.idle(20);
        scene.overlay().showText(80)
            .text("Right-clicking it with the matching fluid bucket will fill it")
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(20);

        puff(scene, util, ParticleTypes.FLAME, 1, 60);
        puff(scene, util, ParticleTypes.LAVA, 1, 60);
        hold(scene, util, ItemStack.EMPTY);
        ejectMany(scene, util, AllItems.BLAZE_CAKE.asStack(), 3);
        scene.idle(80);

        scene.overlay().showText(80)
            .text("Several materials on the Depot will be filled according to the recipe")
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(80);
    }

    // ------------------------------------------------------------------ 进行洗涤

    /** 洗涤: 台面上的物品 + 水桶; 沙砾 ⇒ 燧石, 混凝土粉末 ⇒ 混凝土。 */
    public static void splash(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process_splash", "Washing");

        hold(scene, util, new ItemStack(Items.GRAVEL));
        scene.idle(5);
        scene.overlay().showText(70)
            .text("When a usable item is on the Depot (Gravel, for example)")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 20)
            .withItem(new ItemStack(Items.WATER_BUCKET))
            .rightClick();
        scene.idle(20);
        scene.overlay().showText(70)
            .text("Right-clicking it with a Water Bucket will wash it")
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(20);

        wash(scene, util);
        hold(scene, util, new ItemStack(Items.FLINT));
        scene.effects().indicateSuccess(DEPOT);
        scene.idle(50);

        hold(scene, util, new ItemStack(Items.WHITE_CONCRETE_POWDER));
        scene.idle(20);
        wash(scene, util);
        hold(scene, util, new ItemStack(Items.WHITE_CONCRETE));
        scene.effects().indicateSuccess(DEPOT);
        scene.idle(30);
    }

    // ------------------------------------------------------------------ 进行熔炼

    /** 熔炼: 台面上的物品 + 岩浆桶; 粗铁 ⇒ 铁锭, 圆石 ⇒ 石头 ⇒ 平滑石。 */
    public static void blasting(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process_blasting", "Blasting");

        hold(scene, util, new ItemStack(Items.RAW_IRON));
        scene.idle(5);
        scene.overlay().showText(70)
            .text("When a smeltable item is on the Depot (Raw Iron, for example)")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 20)
            .withItem(new ItemStack(Items.LAVA_BUCKET))
            .rightClick();
        scene.idle(20);
        scene.overlay().showText(70)
            .text("Right-clicking it with a Lava Bucket will blast it")
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(20);

        puff(scene, util, ParticleTypes.LARGE_SMOKE, 1, 60);
        hold(scene, util, new ItemStack(Items.IRON_INGOT));
        scene.effects().indicateSuccess(DEPOT);
        scene.idle(50);

        hold(scene, util, new ItemStack(Items.COBBLESTONE));
        scene.idle(20);
        puff(scene, util, ParticleTypes.LARGE_SMOKE, 1, 60);
        hold(scene, util, new ItemStack(Items.STONE));
        scene.effects().indicateSuccess(DEPOT);
        scene.idle(20);
        puff(scene, util, ParticleTypes.LARGE_SMOKE, 1, 60);
        hold(scene, util, new ItemStack(Items.SMOOTH_STONE));
        scene.effects().indicateSuccess(DEPOT);
        scene.idle(30);
    }

    // ------------------------------------------------------------------ 进行烤制

    /** 烤制: 台面上的物品 + 打火石(会掉 1 点耐久); 生牛肉 ⇒ 熟牛肉。 */
    public static void smoking(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process_smoking", "Smoking");

        hold(scene, util, new ItemStack(Items.BEEF));
        scene.idle(5);
        scene.overlay().showText(70)
            .text("When a cookable item is on the Depot (Raw Beef, for example)")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 20)
            .withItem(new ItemStack(Items.FLINT_AND_STEEL))
            .rightClick();
        scene.idle(20);
        scene.overlay().showText(70)
            .text("Right-clicking it with Flint and Steel will smoke it (using durability)")
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(20);

        puff(scene, util, ParticleTypes.POOF, 1, 60);
        hold(scene, util, new ItemStack(Items.COOKED_BEEF));
        scene.effects().indicateSuccess(DEPOT);
        scene.idle(50);

        // 耐久: 用过的打火石(掉 1 点)
        ItemStack used = new ItemStack(Items.FLINT_AND_STEEL);
        used.setDamageValue(1);
        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 40)
            .withItem(used)
            .rightClick();
        scene.idle(40);
    }

    // ------------------------------------------------------------------ 进行缠魂

    /** 缠魂: 置物台架在灵魂沙/灵魂土上 + 打火石; 沙子 ⇒ 灵魂沙。 */
    public static void haunting(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process_haunting", "Haunting");
        scene.world().showSection(util.select().position(2, 0, 2), Direction.DOWN);
        scene.idle(10);

        hold(scene, util, new ItemStack(Items.SAND));
        scene.overlay().showText(75)
            .text("When the Depot sits on Soul Sand or Soul Soil")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(2, 0, 2));
        scene.idle(75);

        // 台座底下慢慢往上冒的灵魂火焰(游戏里锁定的置物台也会这样, 见 DepotSoulFlames)
        soulFlames(scene, util, 60);
        scene.idle(5);
        scene.overlay().showText(75)
            .text("Haunting can be performed")
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(75);

        scene.overlay().showText(75)
            .text("When a hauntable item is on the Depot (Sand, for example)")
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(20);
        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 40)
            .withItem(new ItemStack(Items.FLINT_AND_STEEL))
            .rightClick();
        scene.idle(55);

        scene.overlay().showText(70)
            .text("Right-clicking it with Flint and Steel will haunt it")
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT))
            .attachKeyFrame();
        scene.idle(10);

        puff(scene, util, ParticleTypes.SOUL_FIRE_FLAME, 1, 60);
        puff(scene, util, ParticleTypes.SMOKE, 1, 60);
        hold(scene, util, new ItemStack(Items.SOUL_SAND));
        scene.effects().indicateSuccess(DEPOT);
        scene.idle(60);
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
     * 再调一次就是"台面上的物品换了"; 传 {@link ItemStack#EMPTY} 就是"台面空了"。
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

    /** 成品从台上弹出、停留一拍后消失。 */
    private static void eject(CreateSceneBuilder scene, SceneBuildingUtil util, ItemStack stack,
                              double dx, double dz, int ticks) {
        ElementLink<EntityElement> link = scene.world().createItemEntity(
            util.vector().topOf(DEPOT).add(dx, 0.3, dz), new Vec3(0, 0.05, 0), stack.copy());
        scene.idle(ticks);
        scene.world().modifyEntity(link, Entity::discard);
    }

    /** 一次弹出多件成品(注液: 台面上有几个原料就注出几个), 一起消失。 */
    private static void ejectMany(CreateSceneBuilder scene, SceneBuildingUtil util, ItemStack stack, int count) {
        List<ElementLink<EntityElement>> links = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double dx = 0.16 * (i - (count - 1) / 2.0);
            links.add(scene.world().createItemEntity(
                util.vector().topOf(DEPOT).add(dx, 0.3, 0.22), new Vec3(0, 0.05, 0.01), stack.copy()));
        }
        scene.idle(35);
        for (ElementLink<EntityElement> link : links) {
            scene.world().modifyEntity(link, Entity::discard);
        }
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
