package com.nonono.createbetterwrench.client.ponder.scenes;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 直接附属于**万能扳手**的两段场景。
 *
 * <pre>
 *   使用万能扳手连接应力 → {@link #connect}      (结构 connect.nbt: 一条**带两个拐点**的路线)
 *   使用万能扳手批量拆除 → {@link #deconstruct}  (结构 deconstruct.nbt: 一台 3×3 的小机器)
 * </pre>
 *
 * <p><b>写法</b>(2026-09-23 按 Create 自家场景重写, 规则与统计见 docs/dev/06-ponder.md §15):
 * 结构逐块长出来、文字短且是陈述句、几乎每条都 {@code .pointAt(...).placeNearTarget()}、只有关键拍 {@code .attachKeyFrame()}、
 * 不乱上色、不用 {@code .independent()}、不写 {@code markAsFinished()}; 用 {@code showControls(...).withItem(...)} 演"手上拿什么";
 * 机器先 {@code setKineticSpeed(selection, 0)} 静止, 连通后再给速度(照 {@code KineticsScenes.cogAsRelay} 的 0 → 64)。</p>
 *
 * <p>⚠️ 结构路径的尾段必须等于 {@code assets/create_better_wrench/ponder/wrench/<尾段>.nbt} 的文件名。</p>
 */
public final class WrenchScenes {

    // ---- connect: 7×7 底板, 路线 = 电机 --x--> 拐点1 --z--> 拐点2 --x--> 大齿轮(两次拐点) ----
    /** 起点: 电机(输出朝 +x)。 */
    private static final BlockPos START = new BlockPos(1, 1, 2);
    /** 终点(负载): 大齿轮(绕 x 轴转)。 */
    private static final BlockPos END = new BlockPos(6, 1, 5);
    /** 第一个拐点: 齿轮箱(x → z)。 */
    private static final BlockPos CORNER1 = new BlockPos(4, 1, 2);
    /** 第二个拐点: 齿轮箱(z → x)。 */
    private static final BlockPos CORNER2 = new BlockPos(4, 1, 5);
    /** 第一段(沿 x)中间那几根轴。 */
    private static final BlockPos[] LEG1 = {new BlockPos(2, 1, 2), new BlockPos(3, 1, 2)};
    /** 第二段(沿 z)中间那几根轴。 */
    private static final BlockPos[] LEG2 = {new BlockPos(4, 1, 3), new BlockPos(4, 1, 4)};
    /** 第三段(沿 x)中间那根轴。 */
    private static final BlockPos LEG3 = new BlockPos(5, 1, 5);

    /** deconstruct: 要拆掉的那一坨(3×3, 见结构文件)。 */
    private static final int DECON_MIN = 1;
    private static final int DECON_MAX = 3;

    private WrenchScenes() {
    }

    // ------------------------------------------------------------------ 使用万能扳手连接应力

    /** 连接: 起点 → 拐点 → 拐点 → 终点; 缺的传动杆自动补齐, 连完整条线开始转动。 */
    public static void connect(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("wrench_connect", "Linking Stress using the Universal Wrench");
        scene.configureBasePlate(0, 0, 7);

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        // 两端先立起来, 但**先静止** —— 等连上再转
        scene.world().showSection(util.select().position(START), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().position(START), 0);
        scene.idle(5);
        scene.world().showSection(util.select().position(END), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
            .text("The Universal Wrench will link two kinetic blocks together")
            .pointAt(util.vector().topOf(START))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(60);

        // 手上拿着扳手(Create 惯例: 只演"手上有什么", 不强行演点击)
        scene.overlay().showControls(util.vector().blockSurface(START, Direction.UP), Pointing.DOWN, 20)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance());
        scene.idle(20);
        scene.overlay().showText(70)
            .text("Right-clicking a block will set the route's start")
            .pointAt(util.vector().blockSurface(START, Direction.UP))
            .placeNearTarget();
        scene.effects().indicateSuccess(START);
        scene.idle(70);

        // 第一段: 缺的轴一段段补上
        scene.overlay().showText(70)
            .text("Missing shafts will be placed along the route")
            .pointAt(util.vector().topOf(LEG1[1]))
            .placeNearTarget()
            .attachKeyFrame();
        for (BlockPos shaft : LEG1) {
            scene.idle(5);
            scene.world().showSection(util.select().position(shaft), Direction.DOWN);
        }
        scene.idle(10);

        // 第一个拐点: 右击拐点让路线转向
        scene.idle(5);
        scene.world().showSection(util.select().position(CORNER1), Direction.DOWN);
        scene.effects().rotationDirectionIndicator(CORNER1);
        scene.overlay().showText(70)
            .text("Right-clicking a corner block will turn the route")
            .pointAt(util.vector().topOf(CORNER1))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(70);

        // 第二段 + 第二个拐点
        for (BlockPos shaft : LEG2) {
            scene.idle(5);
            scene.world().showSection(util.select().position(shaft), Direction.DOWN);
        }
        scene.idle(5);
        scene.world().showSection(util.select().position(CORNER2), Direction.DOWN);
        scene.effects().rotationDirectionIndicator(CORNER2);
        scene.idle(20);

        // 第三段
        scene.idle(5);
        scene.world().showSection(util.select().position(LEG3), Direction.DOWN);
        scene.idle(20);

        // 连通 → 整条线转起来
        scene.world().setKineticSpeed(util.select().fromTo(1, 1, 2, 6, 1, 5), 64);
        scene.effects().indicateSuccess(END);
        scene.overlay().showText(80)
            .colored(PonderPalette.GREEN)
            .text("Right-clicking the other end will link it up and set it running")
            .pointAt(util.vector().topOf(END))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showText(70)
            .text("Ctrl and Scroll will switch the corner type")
            .pointAt(util.vector().topOf(CORNER1))
            .placeNearTarget();
        scene.idle(70);
    }

    // ------------------------------------------------------------------ 使用万能扳手批量拆除

    /** 批量拆除: **先选第一个角, 再选对角**框出选区, 范围内的可拆方块分批消失。 */
    public static void deconstruct(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("wrench_deconstruct", "Removing Blocks using the Universal Wrench");
        scene.configureBasePlate(0, 0, 5);

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        // 要被拆掉的那台小机器: 一块块长出来
        for (int z = DECON_MIN; z <= DECON_MAX; z++) {
            for (int x = DECON_MIN; x <= DECON_MAX; x++) {
                scene.idle(3);
                scene.world().showSection(util.select().position(x, 1, z), Direction.DOWN);
            }
        }
        scene.idle(20);

        scene.overlay().showText(70)
            .text("The Universal Wrench can remove a whole area at once")
            .pointAt(util.vector().topOf(2, 1, 2))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(60);

        // 第一次右击: 只选中**一个角**
        BlockPos cornerA = new BlockPos(DECON_MIN, 1, DECON_MIN);
        BlockPos cornerB = new BlockPos(DECON_MAX, 1, DECON_MAX);
        scene.overlay().showControls(util.vector().blockSurface(cornerA, Direction.UP), Pointing.DOWN, 20)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance());
        scene.idle(20);
        scene.overlay().showText(70)
            .text("Right-clicking a block will set the first corner")
            .pointAt(util.vector().blockSurface(cornerA, Direction.UP))
            .placeNearTarget();
        scene.overlay().showOutline(PonderPalette.BLUE, "cbw_sel_a", util.select().position(cornerA), 70);
        scene.effects().indicateSuccess(cornerA);
        scene.idle(70);

        // 第二次右击: 对角 → 选区成形
        scene.overlay().showControls(util.vector().blockSurface(cornerB, Direction.UP), Pointing.DOWN, 20)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance());
        scene.idle(20);
        scene.overlay().showText(70)
            .text("Right-clicking the opposite corner will finish the selection")
            .pointAt(util.vector().blockSurface(cornerB, Direction.UP))
            .placeNearTarget()
            .attachKeyFrame();
        scene.overlay().showOutline(PonderPalette.BLUE, "cbw_sel",
            util.select().fromTo(DECON_MIN, 1, DECON_MIN, DECON_MAX, 1, DECON_MAX), 70);
        scene.effects().indicateSuccess(cornerB);
        scene.idle(80);

        scene.overlay().showText(70)
            .text("Every wrenchable block inside will be removed in batches")
            .pointAt(util.vector().topOf(2, 1, 2))
            .placeNearTarget();
        scene.idle(30);

        // 分批消失(一块块拆, 与"长出来"对称)
        for (int z = DECON_MIN; z <= DECON_MAX; z++) {
            for (int x = DECON_MIN; x <= DECON_MAX; x++) {
                scene.idle(3);
                scene.world().destroyBlock(new BlockPos(x, 1, z));
            }
        }
        scene.idle(30);

        scene.overlay().showText(70)
            .text("Ctrl and Scroll will switch which blocks are included")
            .pointAt(util.vector().topOf(2, 1, 2))
            .placeNearTarget();
        scene.idle(70);
    }
}
