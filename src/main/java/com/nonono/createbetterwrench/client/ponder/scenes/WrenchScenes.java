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
 *   使用万能扳手连接应力 → {@link #connect}
 *   使用万能扳手批量拆除 → {@link #deconstruct}
 * </pre>
 *
 * <p><b>写法(2026-09-23 按 Create 自家场景重写)</b>: 之前那版"太怪", 与 Create 168 段场景的统计对不上。
 * 重写时照抄 Create 的这几条:</p>
 * <ul>
 *   <li><b>结构逐块长出来</b>: {@code showSection(position(...))} + {@code idle(5)}, 而不是整块 {@code fromTo} 一次出现
 *       (Create 全库 {@code showSection} 868 次 / 168 段);</li>
 *   <li><b>文字短、陈述句</b>: 中文照 "右击可以手动放置或取下物品" / "齿轮会将动力传递至临近的齿轮" 的口气,
 *       不出现"你 / 你的背包 / 交给…"这类第二人称;</li>
 *   <li><b>几乎每条文字都 {@code .pointAt(...).placeNearTarget()}</b>(Create 82% 的 showText 都带它),
 *       只有关键拍才 {@code .attachKeyFrame()};</li>
 *   <li><b>不乱上色、不用 {@code independent()}</b>(Create 平均每段 1.75 次 colored、0.09 次 independent);</li>
 *   <li><b>不写 {@code markAsFinished()}</b>(Create 168 段里只有 33 次);</li>
 *   <li>演"手上拿什么"用 {@code showControls(...).withItem(...)} —— Create 的惯例, 不强行演点击动画;</li>
 *   <li>用 {@code setKineticSpeed(selection, 0)} 先让机器静止, 连通后再给速度, 让"接上了"自己表现出来
 *       (照 {@code KineticsScenes.cogAsRelay} 的 0 → 64)。</li>
 * </ul>
 *
 * <p>⚠️ 结构路径的尾段必须等于 {@code assets/create_better_wrench/ponder/wrench/<尾段>.nbt} 的文件名。</p>
 */
public final class WrenchScenes {

    /** connect: 7×7 底板, 传动线沿 z=3 铺开。 */
    private static final BlockPos MOTOR = new BlockPos(1, 1, 3);
    /** connect: 需要由扳手补上的那几段轴。 */
    private static final BlockPos[] CONNECT_SHAFTS = {
        new BlockPos(2, 1, 3), new BlockPos(3, 1, 3), new BlockPos(4, 1, 3)
    };
    /** connect: 线的末端(齿轮箱 + 小齿轮, 转起来最直观)。 */
    private static final BlockPos GEARBOX = new BlockPos(5, 1, 3);
    private static final BlockPos TOP_COG = new BlockPos(5, 2, 3);
    /** connect: 中间那段的取样点(用于指路文字)。 */
    private static final BlockPos MID_SHAFT = new BlockPos(3, 1, 3);

    /** deconstruct: 要拆掉的那一坨(3×3, 见结构文件)。 */
    private static final int DECON_MIN = 1;
    private static final int DECON_MAX = 3;

    private WrenchScenes() {
    }

    // ------------------------------------------------------------------ 使用万能扳手连接应力

    /** 连接: 起点/拐点/终点各右击一次, 缺的传动杆自动补齐; 连完整条线开始转动。 */
    public static void connect(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("wrench_connect", "Linking Stress using the Universal Wrench");
        scene.configureBasePlate(0, 0, 7);

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        // 动力端与负载端先立起来, 但**先静止** —— 等连上再转
        scene.world().showSection(util.select().position(MOTOR), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().position(MOTOR), 0);
        scene.idle(5);
        scene.world().showSection(util.select().position(GEARBOX), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(TOP_COG), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(70)
            .text("The Universal Wrench will link two kinetic blocks together")
            .pointAt(util.vector().topOf(MOTOR))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(60);

        // 手上拿着扳手(Create 惯例: 只演"手上有什么", 不强行演点击动画)
        scene.overlay().showControls(util.vector().blockSurface(MOTOR, Direction.UP), Pointing.DOWN, 20)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance());
        scene.idle(20);
        scene.overlay().showText(70)
            .text("Right-clicking a block will set the route's start")
            .pointAt(util.vector().blockSurface(MOTOR, Direction.UP))
            .placeNearTarget();
        scene.effects().indicateSuccess(MOTOR);
        scene.idle(70);

        // 缺的传动杆一段段补上
        scene.overlay().showText(70)
            .text("Missing shafts will be placed along the route")
            .pointAt(util.vector().topOf(MID_SHAFT))
            .placeNearTarget()
            .attachKeyFrame();
        for (BlockPos shaft : CONNECT_SHAFTS) {
            scene.idle(5);
            scene.world().showSection(util.select().position(shaft), Direction.DOWN);
        }
        scene.idle(30);

        // 连通 → 整条线转起来
        scene.world().setKineticSpeed(util.select().fromTo(1, 1, 3, 5, 2, 3), 64);
        scene.effects().indicateSuccess(GEARBOX);
        scene.overlay().showText(80)
            .colored(PonderPalette.GREEN)
            .text("Right-clicking the other end will link it up and set it running")
            .pointAt(util.vector().topOf(TOP_COG))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showText(70)
            .text("Ctrl and Scroll will switch the corner type")
            .pointAt(util.vector().topOf(GEARBOX))
            .placeNearTarget();
        scene.idle(70);
    }

    // ------------------------------------------------------------------ 使用万能扳手批量拆除

    /** 批量拆除: 两次右击框选, 范围内的可拆方块分批消失。 */
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

        scene.overlay().showControls(util.vector().blockSurface(new BlockPos(1, 1, 1), Direction.UP), Pointing.DOWN, 20)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance());
        scene.idle(20);
        scene.overlay().showText(70)
            .text("Right-clicking two opposite corners will select the area")
            .pointAt(util.vector().topOf(1, 1, 1))
            .placeNearTarget();
        scene.overlay().showOutline(PonderPalette.BLUE, "cbw_sel",
            util.select().fromTo(DECON_MIN, 1, DECON_MIN, DECON_MAX, 1, DECON_MAX), 70);
        scene.idle(80);

        scene.overlay().showText(70)
            .text("Every wrenchable block inside will be removed in batches")
            .pointAt(util.vector().topOf(2, 1, 2))
            .placeNearTarget()
            .attachKeyFrame();
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
