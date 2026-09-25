package com.nonono.createbetterwrench.client.ponder.scenes;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * 直接附属于**万能扳手**的两段场景(用户 2026-09-23 定的顺序与标题)。
 *
 * <pre>
 *   使用万能扳手连接应力   → {@link #connect}      (结构 wrench/connect)
 *   使用万能扳手批量拆除   → {@link #deconstruct}  (结构 wrench/deconstruct)
 * </pre>
 *
 * <p>加工相关的 7 段在 {@link DepotScenes}(它们归属**置物台**, 同时也关联到扳手)。</p>
 *
 * <p><b>⚠️ 两个 id 别混</b>(docs/dev/06-ponder.md §2.3 / §5): {@code addStoryBoard("wrench/connect", ...)} 的字符串是
 * **结构文件路径**; {@code title("wrench_connect", ...)} 的第一个参数才是**语言键前缀**。</p>
 *
 * <p>场景里用 Create 的 {@link CreateSceneBuilder}(它是 {@code SceneBuilder} 的包装), 因为需要
 * {@code world().setKineticSpeed(...)} 这类 Create 扩展指令; 本包只会在**客户端**加载(由
 * {@code BetterWrenchClient} 注册插件时引用), 因此引用 Create 客户端类是安全的。</p>
 */
public final class WrenchScenes {

    /** connect 场景里那台电机的位置(7×7 底板, 传动线沿 z=3 铺开)。 */
    private static final BlockPos MOTOR = new BlockPos(1, 1, 3);
    /** connect: 需要"由扳手补上"的那几段轴。 */
    private static final BlockPos[] CONNECT_SHAFTS = {
        new BlockPos(2, 1, 3), new BlockPos(3, 1, 3), new BlockPos(4, 1, 3)
    };
    /** connect: 线的末端(齿轮箱 + 小齿轮, 转起来最直观)。 */
    private static final BlockPos GEARBOX = new BlockPos(5, 1, 3);
    private static final BlockPos TOP_COG = new BlockPos(5, 2, 3);

    /** deconstruct: 要被拆掉的那一坨(3×3, 见结构文件)。 */
    private static final int DECON_MIN = 1;
    private static final int DECON_MAX = 3;

    private WrenchScenes() {
    }

    // ------------------------------------------------------------------ 使用万能扳手连接应力

    /**
     * 连接: 起点/拐点/终点各右击一次, 缺的轴**从背包扣材料**并自动铺好, 连完整条线就开始转。
     *
     * @param builder Ponder 给的场景 builder(由 {@code addStoryBoard} 传入)
     * @param util    选择/坐标工具
     */
    public static void connect(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("wrench_connect", "Linking Stress with the Wrench");
        scene.configureBasePlate(0, 0, 7);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        // 先亮出"电源"(电机)与"末端"(齿轮箱 + 小齿轮), 中间故意空着
        scene.world().showSection(util.select().position(MOTOR), Direction.DOWN);
        scene.world().showSection(util.select().position(GEARBOX), Direction.DOWN);
        scene.world().showSection(util.select().position(TOP_COG), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
            .text("A Powered Motor on one side, a Gearbox on the other, nothing in between")
            .colored(PonderPalette.BLUE)
            .placeNearTarget()
            .pointAt(util.vector().topOf(MOTOR));
        scene.idle(80);

        // ① 右击起点
        scene.overlay().showControls(util.vector().topOf(MOTOR), Pointing.DOWN, 30)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance())
            .rightClick();
        scene.overlay().showText(80)
            .text("Right-click the start block to remember its direction")
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .placeNearTarget()
            .pointAt(util.vector().topOf(MOTOR));
        scene.effects().indicateSuccess(MOTOR);
        scene.idle(90);

        // ② 中间缺的轴由扳手一段段补上(材料从背包扣)
        scene.overlay().showText(80)
            .text("Missing shafts are placed for you, paid from your inventory")
            .attachKeyFrame()
            .colored(PonderPalette.WHITE)
            .independent();
        for (BlockPos shaft : CONNECT_SHAFTS) {
            scene.world().showSection(util.select().position(shaft), Direction.DOWN);
            scene.idle(12);
        }
        scene.idle(20);

        // ③ 连完整条线开始转
        scene.overlay().showText(80)
            .text("Right-click the end to link it up - the whole line starts turning")
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .placeNearTarget()
            .pointAt(util.vector().topOf(GEARBOX));
        scene.effects().indicateSuccess(GEARBOX);
        scene.world().setKineticSpeed(util.select().fromTo(1, 1, 3, 5, 2, 3), 32);
        scene.idle(60);

        scene.overlay().showText(70)
            .sharedText("ctrl_scroll")
            .colored(PonderPalette.WHITE)
            .independent();
        scene.idle(80);

        scene.markAsFinished();
    }

    // ------------------------------------------------------------------ 使用万能扳手批量拆除

    /**
     * 批量拆除: 两次右击框选 ⇒ 范围内的可拆方块**分批**消失; 允许 Ctrl+滚轮 换过滤器。
     *
     * @param builder Ponder 给的场景 builder
     * @param util    选择/坐标工具
     */
    public static void deconstruct(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("wrench_deconstruct", "Bulk Deconstructing");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        scene.world().showSection(util.select().fromTo(DECON_MIN, 1, DECON_MIN, DECON_MAX, 1, DECON_MAX), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(80)
            .text("Right-click two opposite corners to box in what you want gone")
            .attachKeyFrame()
            .colored(PonderPalette.BLUE)
            .placeNearTarget()
            .pointAt(util.vector().topOf(2, 1, 2));
        scene.idle(90);

        // 选区高亮(蓝框)
        scene.overlay().showOutline(PonderPalette.BLUE, "cbw_sel",
            util.select().fromTo(DECON_MIN, 1, DECON_MIN, DECON_MAX, 1, DECON_MAX), 70);
        scene.overlay().showControls(util.vector().topOf(1, 1, 1), Pointing.DOWN, 30)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance())
            .rightClick();
        scene.idle(50);

        scene.overlay().showText(80)
            .text("Every wrenchable block inside is removed in batches - no lag spike")
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .independent();
        scene.idle(30);

        // 一批批拆: 先两条边, 再中间
        scene.world().replaceBlocks(util.select().fromTo(DECON_MIN, 1, DECON_MIN, DECON_MAX, 1, DECON_MIN),
            Blocks.AIR.defaultBlockState(), true);
        scene.idle(15);
        scene.world().replaceBlocks(util.select().fromTo(DECON_MIN, 1, DECON_MAX, DECON_MAX, 1, DECON_MAX),
            Blocks.AIR.defaultBlockState(), true);
        scene.idle(15);
        scene.world().replaceBlocks(util.select().fromTo(1, 1, 2, 3, 1, 2),
            Blocks.AIR.defaultBlockState(), true);
        scene.idle(25);

        scene.overlay().showText(70)
            .sharedText("ctrl_scroll")
            .colored(PonderPalette.WHITE)
            .independent();
        scene.idle(80);

        scene.markAsFinished();
    }
}
