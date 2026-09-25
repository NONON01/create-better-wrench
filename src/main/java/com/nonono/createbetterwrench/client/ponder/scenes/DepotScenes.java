package com.nonono.createbetterwrench.client.ponder.scenes;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import com.mojang.blaze3d.vertex.PoseStack;

import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.createmod.ponder.foundation.ui.PonderUI;
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
 *   使用万能扳手进行加工 → {@link #process}     (总述: 锁定 → 加工 → 六种都支持)
 *   进行装配             → {@link #assembly}    (图标行: 右击 / 小齿轮 / 大齿轮 / 铁粒 ⇒ 一次性出精密构件)
 *   进行注液             → {@link #filling}     (烈焰蛋糕胚 + 岩浆桶 ⇒ 烈焰蛋糕, 多个原料按配方算)
 *   进行洗涤             → {@link #splash}      (只演沙砾 ⇒ 燧石)
 *   进行冶炼             → {@link #blasting}    (只演粗铁 ⇒ 铁锭; 官方 Ponder 文案也作「冶炼」)
 *   进行烤制             → {@link #smoking}     (生牛肉 ⇒ 熟牛肉, 文案里提"消耗耐久")
 *   进行缠魂             → {@link #haunting}    (灵魂沙**单独一层**(2,1,2) + 置物台(2,2,2), 沙子 ⇒ 灵魂沙)
 * </pre>
 *
 * <p><b>结构文件</b>都是 5×5 底板 + 一个 {@code create:depot}(坐标见 {@link #DEPOT} / {@link #HAUNT_DEPOT});
 * 台面上的物品不是写在结构里, 而是用 Create 自己的办法**直接改方块实体 NBT**
 * ({@code modifyBlockEntityNBT(..., nbt -> nbt.put("HeldItem", new TransportedItemStack(stack).serializeNBT(provider)))}),
 * 所以"物品变化"= 再调一次 {@link #hold} 换掉它。**成品都留在台面上, 不弹出去**。</p>
 *
 * <p><b>文案</b>照用户给的句子: 先讲"台面上有什么样的原料", 再讲"用什么右击会发生什么";
 * 加括号的举例(如金板)是用户要求保留的写法。</p>
 *
 * <p><b>节奏</b>: 每个动作之间留足间隔; 成对出现的右击图标**各自一个位置**(不重叠),
 * 且**一起出现、一起结束**(用户 2026-09-23: 不要重叠、不要淡入淡出叠在一起)。</p>
 *
 * <p><b>右击反馈</b>: 右击本身没有特效的地方(锁定置物台 / 缠魂的灵魂沙)给黄色选框({@link #SELECT}) +
 * {@code showControls(...).withItem(...).rightClick()} 的右击图标。</p>
 */
public final class DepotScenes {

    /** 一般加工场景的置物台位置(5×5 底板 + 台座)。 */
    private static final BlockPos DEPOT = new BlockPos(2, 1, 2);
    /** 缠魂场景: **整体加高一格**, 灵魂沙单独一层(2,1,2)、置物台坐在它上面(2,2,2), 这样灵魂沙看得见。 */
    private static final BlockPos HAUNT_DEPOT = new BlockPos(2, 2, 2);
    /** 缠魂场景里那块灵魂沙(就摆在置物台正下方)。 */
    private static final BlockPos HAUNT_SOUL = new BlockPos(2, 1, 2);

    /**
     * “黄色选框”。⚠️ Ponder 调色板没有纯黄, {@code OUTPUT = 0xDDC166} 是唯一的金黄;
     * 与游戏内连接模式的选区金色({@code ConnectSelectionHandler.GOLD = 0xE8B54C})基本一致, 右击反馈用它。
     */
    private static final PonderPalette SELECT = PonderPalette.OUTPUT;

    /** 精密构件的序列装配材料 = 小齿轮 → 大齿轮 → 铁粒(配方的 `loops=5`, 这里只把材料图标列出来)。 */
    private static final ItemStack[] MECHANISM_MATERIALS = {
        new ItemStack(AllBlocks.COGWHEEL.get()),
        new ItemStack(AllBlocks.LARGE_COGWHEEL.get()),
        new ItemStack(Items.IRON_NUGGET)
    };

    /** 长图标里每一格的边长(屏幕像素; 与 Ponder 自己的 icon/item 槽一致 = 16 逻辑像素 × 1.5)。 */
    private static final int SLOT = 24;
    /** Ponder 渲染输入图标时对自定义元素施加的缩放(`javap`: `InputWindowElement.render` → `pose.scale(1.5f …)`)。 */
    private static final float INPUT_SCALE = 1.5f;
    /** 气泡盒体与锚点(尾巴尖)之间的间隙(`javap`: `renderSpeechBox` 的 DOWN 分支 = `h + 8 + 1 + 1`)。 */
    private static final int TAIL_GAP = 10;

    private DepotScenes() {
    }

    // ------------------------------------------------------------------ 使用万能扳手进行加工(总述)

    /** 加工总述: 右击锁定 → 锁定的台面可以加工 → 支持六种加工。 */
    public static void process(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process",
            "Processing Items using the Universal Wrench", DEPOT);

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

        hold(scene, util, DEPOT, new ItemStack(Items.RAW_IRON));
        scene.idle(15);
        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 20)
            .withItem(new ItemStack(Items.LAVA_BUCKET))
            .rightClick();
        scene.idle(25);
        scene.overlay().showText(70)
            .text("While locked, items on top can be processed")
            .pointAt(util.vector().topOf(DEPOT))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(30);

        puff(scene, util, DEPOT, ParticleTypes.LARGE_SMOKE, 1, 60);
        scene.effects().indicateSuccess(DEPOT);
        hold(scene, util, DEPOT, new ItemStack(Items.IRON_INGOT));
        scene.idle(70);

        scene.overlay().showText(80)
            .text("Processing covers Assembly, Filling, Washing, Blasting, Smoking and Haunting")
            .pointAt(util.vector().topOf(DEPOT))
            .placeNearTarget();
        scene.idle(80);
    }

    // ------------------------------------------------------------------ 进行装配

    /**
     * 装配: 台面上放原料(金板) → 把"要用的材料"排成一行图标
     * (鼠标右键 / 小齿轮 / 大齿轮 / 铁粒, **四个图标各自一个位置、同时出现同时结束**) → **一次性**显示成品。
     *
     * <p>⚠️ 用户 2026-09-23 定的简化版: 不再逐件演 15 次投料(那样图标会重叠/淡入淡出叠在一起), 也不要粒子。</p>
     */
    public static void assembly(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process_assembly", "Assembly", DEPOT);

        hold(scene, util, DEPOT, AllItems.GOLDEN_SHEET.asStack());
        scene.idle(10);
        scene.overlay().showText(80)
            .text("When a usable assembly material is on the Depot (a Golden Sheet, for example)")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(30);
        scene.idle(60);

        scene.overlay().showText(80)
            .text("Adding the matching materials will assemble it")
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT))
            .attachKeyFrame();
        scene.idle(30);

        // **一个长图标**: 鼠标右键 + 小齿轮 + 大齿轮 + 铁粒(自己加宽气泡, 见 materialStrip)
        scene.overlay().showControls(util.vector().topOf(DEPOT).add(0, 0.55, 0.0), Pointing.DOWN, 60)
            .showing(materialStrip(MECHANISM_MATERIALS));
        scene.idle(60);
        scene.idle(30);

        hold(scene, util, DEPOT, AllItems.PRECISION_MECHANISM.asStack());
        scene.effects().indicateSuccess(DEPOT);
        scene.idle(60);
    }

    // ------------------------------------------------------------------ 进行注液

    /** 注液: 台面上的原料 + 相应的流体桶; 台面上有几个原料就按配方注几个, 产物**留在台面上**。 */
    public static void filling(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process_filling", "Filling", DEPOT);

        hold(scene, util, DEPOT, AllItems.BLAZE_CAKE_BASE.asStack(3));
        scene.idle(10);
        scene.overlay().showText(70)
            .text("When a fillable material is on the Depot (a Blaze Cake Base, for example)")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 20)
            .withItem(new ItemStack(Items.LAVA_BUCKET))
            .rightClick();
        scene.idle(25);
        scene.overlay().showText(80)
            .text("Right-clicking it with the matching fluid bucket will fill it")
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(30);

        puff(scene, util, DEPOT, ParticleTypes.FLAME, 1, 60);
        puff(scene, util, DEPOT, ParticleTypes.LAVA, 1, 60);
        scene.effects().indicateSuccess(DEPOT);
        hold(scene, util, DEPOT, AllItems.BLAZE_CAKE.asStack(3));
        scene.idle(70);

        scene.overlay().showText(80)
            .text("Several materials on the Depot will be filled according to the recipe")
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(80);
    }

    // ------------------------------------------------------------------ 进行洗涤

    /** 洗涤: 台面上的物品 + 水桶; 只演示沙砾 ⇒ 燧石(用户要求不再演示第二个例子)。 */
    public static void splash(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process_splash", "Washing", DEPOT);

        hold(scene, util, DEPOT, new ItemStack(Items.GRAVEL));
        scene.idle(10);
        scene.overlay().showText(70)
            .text("When a usable item is on the Depot (Gravel, for example)")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 20)
            .withItem(new ItemStack(Items.WATER_BUCKET))
            .rightClick();
        scene.idle(25);
        scene.overlay().showText(70)
            .text("Right-clicking it with a Water Bucket will wash it")
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(30);

        wash(scene, util, DEPOT);
        scene.effects().indicateSuccess(DEPOT);
        hold(scene, util, DEPOT, new ItemStack(Items.FLINT));
        scene.idle(70);
    }

    // ------------------------------------------------------------------ 进行冶炼

    /** 冶炼: 台面上的物品 + 岩浆桶; 只演示粗铁 ⇒ 铁锭(用户要求不再演示圆石那条链)。 */
    public static void blasting(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process_blasting", "Blasting", DEPOT);

        hold(scene, util, DEPOT, new ItemStack(Items.RAW_IRON));
        scene.idle(10);
        scene.overlay().showText(70)
            .text("When a smeltable item is on the Depot (Raw Iron, for example)")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 20)
            .withItem(new ItemStack(Items.LAVA_BUCKET))
            .rightClick();
        scene.idle(25);
        scene.overlay().showText(70)
            .text("Right-clicking it with a Lava Bucket will blast it")
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(30);

        puff(scene, util, DEPOT, ParticleTypes.LARGE_SMOKE, 1, 60);
        scene.effects().indicateSuccess(DEPOT);
        hold(scene, util, DEPOT, new ItemStack(Items.IRON_INGOT));
        scene.idle(70);
    }

    // ------------------------------------------------------------------ 进行烤制

    /** 烤制: 台面上的物品 + 打火石(文案里提"消耗耐久"); 生牛肉 ⇒ 熟牛肉。 */
    public static void smoking(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process_smoking", "Smoking", DEPOT);

        hold(scene, util, DEPOT, new ItemStack(Items.BEEF));
        scene.idle(10);
        scene.overlay().showText(70)
            .text("When a cookable item is on the Depot (Raw Beef, for example)")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 20)
            .withItem(new ItemStack(Items.FLINT_AND_STEEL))
            .rightClick();
        scene.idle(25);
        scene.overlay().showText(70)
            .text("Right-clicking it with Flint and Steel will smoke it (using durability)")
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(30);

        puff(scene, util, DEPOT, ParticleTypes.POOF, 1, 60);
        scene.effects().indicateSuccess(DEPOT);
        hold(scene, util, DEPOT, new ItemStack(Items.COOKED_BEEF));
        scene.idle(70);
    }

    // ------------------------------------------------------------------ 进行缠魂

    /**
     * 缠魂: **灵魂沙单独一层**(结构整体加了一格, 灵魂沙一眼可见) + 置物台坐在它上面 + 打火石 ⇒ 缠魂。
     * 第一句就把灵魂沙用黄框标出来、文字也指向它。
     */
    public static void haunting(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("wrench_process_haunting", "Haunting");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);
        scene.world().showSection(util.select().position(HAUNT_SOUL), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(HAUNT_DEPOT), Direction.DOWN);
        scene.idle(10);

        hold(scene, util, HAUNT_DEPOT, new ItemStack(Items.SAND));
        // 第一句: 黄框标出灵魂沙, 文字指向它
        scene.overlay().showOutline(SELECT, "cbw_soul_sand", util.select().position(HAUNT_SOUL), 75);
        scene.overlay().showText(75)
            .text("When the Depot sits on Soul Sand or Soul Soil")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(HAUNT_SOUL));
        scene.idle(75);

        // 台座底下慢慢往上冒的灵魂火焰(游戏里锁定的置物台也会这样, 见 DepotSoulFlames)
        soulFlames(scene, util, HAUNT_DEPOT, 60);
        scene.idle(10);
        scene.overlay().showText(75)
            .text("Haunting can be performed")
            .placeNearTarget()
            .pointAt(util.vector().topOf(HAUNT_SOUL));
        scene.idle(75);

        scene.overlay().showText(75)
            .text("When a hauntable item is on the Depot (Sand, for example)")
            .placeNearTarget()
            .pointAt(util.vector().topOf(HAUNT_DEPOT));
        scene.idle(25);
        scene.overlay().showControls(util.vector().topOf(HAUNT_DEPOT), Pointing.DOWN, 40)
            .withItem(new ItemStack(Items.FLINT_AND_STEEL))
            .rightClick();
        scene.idle(50);

        scene.overlay().showText(70)
            .text("Right-clicking it with Flint and Steel will haunt it")
            .placeNearTarget()
            .pointAt(util.vector().topOf(HAUNT_DEPOT))
            .attachKeyFrame();
        scene.idle(15);

        puff(scene, util, HAUNT_DEPOT, ParticleTypes.SOUL_FIRE_FLAME, 1, 60);
        puff(scene, util, HAUNT_DEPOT, ParticleTypes.SMOKE, 1, 60);
        scene.effects().indicateSuccess(HAUNT_DEPOT);
        hold(scene, util, HAUNT_DEPOT, new ItemStack(Items.SOUL_SAND));
        scene.idle(60);
    }

    // ------------------------------------------------------------------ 公共套路

    /** 每段加工场景的共同开场: 标题 → 底板 → 置物台。 */
    private static CreateSceneBuilder start(SceneBuilder builder, SceneBuildingUtil util,
                                            String titleId, String title, BlockPos depot) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(titleId, title);
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);
        scene.world().showSection(util.select().position(depot), Direction.DOWN);
        scene.idle(5);
        return scene;
    }

    /**
     * 在置物台上方摆**一个长图标**: 「鼠标右键」+ {@link #MECHANISM_MATERIALS} 依次排在里面。
     *
     * <p>⚠️ Ponder 的输入元素只给自定义 {@code ScreenElement} 留 24px(它自己按"图标/文字/物品"三选几算宽度),
     * 想塞 4 个只能自己加宽。做法(全部按 `javap` 实测的几何来):
     * 元素的渲染是在 `pose.scale(1.5)` 之后调 {@code icon.render(graphics, 0, 0)} 的, 所以先 `scale(1/1.5)`
     * **把单位换回屏幕像素**, 然后用与 Ponder **完全相同**的锚点/高度再调一次公开的
     * {@code PonderUI.renderSpeechBox}(只是宽度变成 4 格) —— 这样气泡除了更宽以外与元素自带那个**逐像素重合**
     * (它自带的 24px 小气泡被完全盖住, 尾巴也重合), 最后在气泡内部按格画右击鼠标图标
     * ({@code PonderGuiTextures.ICON_RMB}, 与 {@code .rightClick()} 同一张贴图)与三件材料图标。</p>
     */
    private static ScreenElement materialStrip(ItemStack... materials) {
        return (graphics, x, y) -> {
            int count = materials.length + 1;
            int width = SLOT * count;
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.scale(1f / INPUT_SCALE, 1f / INPUT_SCALE, 1f);      // 单位 = 屏幕像素
            PonderUI.renderSpeechBox(graphics, x, y, width, SLOT, false, Pointing.DOWN, true);
            int top = y - SLOT - TAIL_GAP;                           // renderSpeechBox 的 DOWN 分支: 盒子在锚点上方
            int left = x - width / 2;
            for (int i = 0; i < count; i++) {
                pose.pushPose();
                pose.translate(left + i * SLOT, top, 0);
                pose.scale(INPUT_SCALE, INPUT_SCALE, 1f);
                if (i == 0)
                    PonderGuiTextures.ICON_RMB.render(graphics, 0, 0);
                else
                    graphics.renderItem(materials[i - 1], 0, 0);
                pose.popPose();
            }
            pose.popPose();
        };
    }

    /**
     * 把某件物品摆到置物台上(直接改方块实体 NBT —— Create 的 {@code FanScenes} 用的就是这个键 {@code HeldItem})。
     * 再调一次就是"台面上的物品换了"(整摞也照写, 例如 `asStack(3)`)。
     */
    private static void hold(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos depot, ItemStack stack) {
        scene.world().modifyBlockEntityNBT(util.select().position(depot), DepotBlockEntity.class,
            nbt -> nbt.put("HeldItem",
                new TransportedItemStack(stack.copy()).serializeNBT(scene.world().getHolderLookupProvider())));
    }

    /** 台面上方喷一小撮粒子(与游戏内加工反馈同款)。 */
    private static void puff(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos depot,
                             ParticleOptions particle, float amount, int ticks) {
        Vec3 at = util.vector().topOf(depot).add(0, 0.25, 0);
        scene.effects().emitParticles(at, scene.effects().simpleParticleEmitter(particle, new Vec3(0, 0.05, 0)),
            amount, ticks);
    }

    /** 洗涤专用的蓝色尘 + SPIT(与 {@code AssembleLogic#playFanFeedback} 的洗涤分支一致)。 */
    private static void wash(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos depot) {
        puff(scene, util, depot, new DustParticleOptions(new Vector3f(0f, 0x55 / 255f, 1f), 1f), 8, 25);
        puff(scene, util, depot, ParticleTypes.SPIT, 1, 60);
    }

    /**
     * 缠魂场景里"从置物台下方慢慢冒出来"的灵魂火焰(现实游戏里由 {@code DepotSoulFlames} 负责)。
     *
     * <p>⚠️ 不能只在置物台**中心**喷: 置物台的模型是整格底座(0~11/16 高、横向铺满),
     * 中心处的粒子会被模型挡住 ⇒ 与游戏内一样, 沿**四条竖边外侧一丝**各喷一处。</p>
     */
    private static void soulFlames(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos depot, int ticks) {
        Vec3 center = util.vector().centerOf(depot);
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
