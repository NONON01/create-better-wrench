package com.nonono.createbetterwrench.client.ponder.scenes;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
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
 * <b>归在置物台、同时关联到万能扳手</b>的 7 段场景(顺序与标题为设计约定, 2026-09-23)。
 *
 * <pre>
 *   使用万能扳手进行加工: {@link #process}     (总述: 锁定、加工、六种都支持)
 *   进行装配:             {@link #assembly}    (图标行: 右击 / 小齿轮 / 大齿轮 / 铁粒, 一次性出精密构件)
 *   进行注液:             {@link #filling}     (烈焰蛋糕胚 + 岩浆桶, 产出烈焰蛋糕, 多个原料按配方算)
 *   进行洗涤:             {@link #splash}      (只演沙砾, 产出燧石)
 *   进行冶炼:             {@link #blasting}    (只演粗铁, 产出铁锭; 官方 Ponder 文案也作「冶炼」)
 *   进行烤制:             {@link #smoking}     (生牛肉, 产出熟牛肉, 文案中提及消耗耐久)
 *   进行缠魂:             {@link #haunting}    (灵魂沙<b>单独一层</b>(2,1,2) + 置物台(2,2,2), 沙子产出灵魂沙)
 * </pre>
 *
 * <p><b>结构文件</b>都是 5×5 底板 + 一个 {@code create:depot}(坐标见 {@link #DEPOT} / {@link #HAUNT_DEPOT});
 * 台面上的物品不是写在结构里, 而是用 Create 自己的办法<b>直接改方块实体 NBT</b>
 * ({@code modifyBlockEntityNBT(..., nbt -> nbt.put("HeldItem", new TransportedItemStack(stack).serializeNBT(provider)))}),
 * 所以物品变化就等于再调一次 {@link #hold} 换掉它。<b>成品都留在台面上, 不弹出去</b>。</p>
 *
 * <p><b>文案</b>取自设计约定给定的句子(2026-09-23): 先讲台面上有什么样的原料, 再讲用什么右击会发生什么;
 * 加括号的举例(如金板)按约定保留。</p>
 *
 * <p><b>节奏</b>: 每个动作之间留足间隔; 成对出现的右击图标<b>各自一个位置</b>(不重叠),
 * 且<b>一起出现、一起结束</b>(设计约定, 2026-09-23: 不重叠、不淡入淡出叠在一起)。</p>
 *
 * <p><b>右击反馈</b>: 右击本身没有特效的地方(锁定置物台 / 缠魂的灵魂沙)给黄色选框({@link #SELECT}) +
 * {@code showControls(...).withItem(...).rightClick()} 的右击图标。</p>
 */
public final class DepotScenes {

    /** 一般加工场景的置物台位置(5×5 底板 + 台座)。 */
    private static final BlockPos DEPOT = new BlockPos(2, 1, 2);
    /** 缠魂场景: <b>整体加高一格</b>, 灵魂沙单独一层(2,1,2)、置物台坐在它上面(2,2,2), 这样灵魂沙可见。 */
    private static final BlockPos HAUNT_DEPOT = new BlockPos(2, 2, 2);
    /** 缠魂场景里那块灵魂沙(就摆在置物台正下方)。 */
    private static final BlockPos HAUNT_SOUL = new BlockPos(2, 1, 2);

    /**
     * 黄色选框。注意: Ponder 调色板没有纯黄, {@code OUTPUT = 0xDDC166} 是唯一的金黄;
     * 与游戏内连接模式的选区金色({@code ConnectSelectionHandler.GOLD = 0xE8B54C})基本一致, 右击反馈用它。
     */
    private static final PonderPalette SELECT = PonderPalette.OUTPUT;

    /** 精密构件的序列装配材料 = 小齿轮、大齿轮、铁粒(配方的 {@code loops=5}, 这里只把材料图标列出来)。 */
    private static final ItemStack[] MECHANISM_MATERIALS = {
        new ItemStack(AllBlocks.COGWHEEL.get()),
        new ItemStack(AllBlocks.LARGE_COGWHEEL.get()),
        new ItemStack(Items.IRON_NUGGET)
    };

    /** 图标气泡的全亮时长(设计约定: 停留时间取较长值)。 */
    private static final int ICON_TICKS = 32;
    /**
     * Ponder 的淡入 / 淡出各 5 tick。
     *
     * <p>实测上游源码 {@code FadeInOutInstruction}:{@code fadeTime = 5}, 构造器是
     * {@code super(false, duration + 2 * fadeTime)}, 因此一个 {@code showControls(pos, dir, D)} 的气泡
     * <b>实际存活 D + 10 tick</b>(前 5 tick 淡入、后 5 tick 淡出)。所以每拍只 {@code idle(D)} 必然重叠。</p>
     */
    private static final int ICON_FADE = 5;
    /** 上一拍<b>彻底消失</b>之后再空这么久才出下一拍(不重叠, 也不紧贴)。 */
    private static final int ICON_GAP = 8;

    private DepotScenes() {
    }

    // ------------------------------------------------------------------ 使用万能扳手进行加工(总述)

    /** 加工总述: 右击锁定, 锁定的台面可以加工, 支持六种加工。 */
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
     * 装配: 台面上放原料(金板), 依次演示要投的三件材料, 最后<b>一次性</b>显示成品。
     *
     * <p><b>中间态</b>(设计约定, 2026-09-25): 投下第一件材料后台面上的金板就变成 Create 的
     * {@code create:incomplete_precision_mechanism}(未完成精密构件), 之后两件材料都是往这半成品上继续投,
     * 最后才变成精密构件, 因此台面上依次是<b>金板、未完成精密构件、精密构件</b>。</p>
     *
     * <p>不使用单个长图标的理由: Ponder 的输入气泡宽度由它自己按内容算(每个物品槽 24px),
     * 想在同一个气泡里放 4 个图标只能自行加宽气泡, 而元素回调拿到的局部坐标系与
     * {@code renderSpeechBox} 摆放气泡用的坐标系<b>不是同一个</b>(2026-09-25 实机截图证实: 自行画的气泡与图标
     * 各在一处, 还会多出一个没被盖住的原生气泡), 因此不走该方案。
     * 现在改成<b>原生气泡依次演示</b>: 每一拍都是 {@code [右击鼠标][材料]}, 由 {@link #showIconTip} 等它
     * <b>彻底淡出后再空 {@link #ICON_GAP} tick</b> 才出下一拍(不重叠、不叠影), 三件材料演完再一次性出成品。</p>
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

        // [右击鼠标][小齿轮] 使台面变为未完成精密构件(中间态), 随后是 [右击鼠标][大齿轮] 与 [右击鼠标][铁粒], 最后变为精密构件
        // 每一拍都由 showIconTip 负责等它彻底消失(全亮 32 + 淡出 10 + 空档 8), 因此不会重叠
        Vec3 anchor = util.vector().topOf(DEPOT).add(0, 0.55, 0);
        showIconTip(scene, anchor, MECHANISM_MATERIALS[0]);
        hold(scene, util, DEPOT, AllItems.INCOMPLETE_PRECISION_MECHANISM.asStack());
        scene.effects().indicateSuccess(DEPOT);
        scene.idle(20);

        showIconTip(scene, anchor, MECHANISM_MATERIALS[1]);
        showIconTip(scene, anchor, MECHANISM_MATERIALS[2]);

        hold(scene, util, DEPOT, AllItems.PRECISION_MECHANISM.asStack());
        scene.effects().indicateSuccess(DEPOT);
        scene.idle(60);
    }

    /**
     * 演示一拍右击加材料的图标气泡, 并<b>等到它彻底消失</b>之后才把时间交还给场景。
     *
     * <p>时长 = {@link #ICON_TICKS}(全亮) + 2 × {@link #ICON_FADE}(淡入淡出, Ponder 自己会占用) +
     * {@link #ICON_GAP}(全空的间隔)。注意: 只写 {@code idle(ICON_TICKS)} 会让下一拍压在本拍的淡出上
     * (2026-09-25 实机观察到的上一拍淡出与下一拍淡入同时出现即由此而来), 所以这里把 10 tick 的淡入淡出也算进去。
     * 全项目所有 {@code showControls} 都受 {@code tools/audit_icons.ps1} 检查。</p>
     */
    private static void showIconTip(CreateSceneBuilder scene, Vec3 anchor, ItemStack material) {
        scene.overlay().showControls(anchor, Pointing.DOWN, ICON_TICKS)
            .withItem(material)
            .rightClick();
        scene.idle(ICON_TICKS + 2 * ICON_FADE + ICON_GAP);
    }

    // ------------------------------------------------------------------ 进行注液

    /** 注液: 台面上的原料 + 相应的流体桶; 台面上有几个原料就按配方注几个, 产物<b>留在台面上</b>。 */
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

    /** 洗涤: 台面上的物品 + 水桶; 只演示沙砾产出燧石(设计约定: 不再演示第二个例子)。 */
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

    /** 冶炼: 台面上的物品 + 岩浆桶; 只演示粗铁产出铁锭(设计约定: 不再演示圆石那条链)。 */
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

    // ------------------------------------------------------------------ 进行锻造

    /** 锻造: 台面上的铁锭 + 重锤右击 -> 铁板(与置物台冲压路径一致)。 */
    public static void forging(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, util, "wrench_process_forging", "Forging", DEPOT);

        hold(scene, util, DEPOT, new ItemStack(Items.IRON_INGOT));
        scene.idle(10);
        scene.overlay().showText(70)
            .text("When a pressable item is on the Depot (an Iron Ingot, for example)")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 20)
            .withItem(new ItemStack(Items.MACE))
            .rightClick();
        // 图标与转换同刻发生(2026-10-03 维护者要求): 重锤右击的图标出现的同时, 台面的铁锭就变成铁板,
        //   之后才给出文字说明 —— 不再先等 25 tick 再变。
        puff(scene, util, DEPOT, ParticleTypes.CRIT, 1, 60);
        scene.effects().indicateSuccess(DEPOT);
        hold(scene, util, DEPOT, AllItems.IRON_SHEET.asStack());
        scene.idle(25);
        scene.overlay().showText(70)
            .text("The Iron Ingot is pressed into an Iron Sheet")
            .placeNearTarget()
            .pointAt(util.vector().topOf(DEPOT));
        scene.idle(70);
    }

    // ------------------------------------------------------------------ 进行烤制

    /** 烤制: 台面上的物品 + 打火石(文案中提及消耗耐久); 生牛肉产出熟牛肉。 */
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
     * 缠魂: <b>灵魂沙单独一层</b>(结构整体加了一格, 灵魂沙一眼可见) + 置物台坐在它上面 + 打火石完成缠魂。
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

    /** 每段加工场景的共同开场: 标题、底板、置物台。 */
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
     * 把某件物品摆到置物台上(直接改方块实体 NBT —— Create 的 {@code FanScenes} 用的就是这个键 {@code HeldItem})。
     * 再调一次即表示台面上的物品被替换(整摞也照写, 例如 {@code asStack(3)})。
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
     * 缠魂场景里从置物台下方冒出来的灵魂火焰(游戏内由 {@code DepotSoulFlames} 负责)。
     *
     * <p>注意: 不能只在置物台<b>中心</b>喷: 置物台的模型是整格底座(0~11/16 高、横向铺满),
     * 中心处的粒子会被模型挡住, 因此与游戏内一样, 沿<b>四条竖边外侧一丝</b>各喷一处。</p>
     *
     * <p>2026-10-02 与游戏内同步调整: 密度提高, 速度由 0.01 提到 0.05, 并沿各自边的<b>朝外方向</b>
     * 给一个小的水平初速(游戏内的水平初速是随机方向, 这里为了回放稳定改为固定朝外)。</p>
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
        Vec3[] outward = {
            new Vec3(0, 0, -1),
            new Vec3(1, 0, 0),
            new Vec3(0, 0, 1),
            new Vec3(-1, 0, 0)
        };
        for (int i = 0; i < rims.length; i++) {
            Vec3 motion = outward[i].scale(0.02).add(0, 0.05, 0);
            scene.effects().emitParticles(rims[i],
                scene.effects().simpleParticleEmitter(ParticleTypes.SOUL_FIRE_FLAME, motion),
                0.5f, ticks);
        }
    }
}
