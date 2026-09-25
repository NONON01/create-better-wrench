package com.nonono.createbetterwrench.client.ponder.scenes;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 直接附属于**万能扳手**的两段场景。
 *
 * <pre>
 *   使用万能扳手连接应力 → {@link #connect}      (connect.nbt: 一条带**两个拐点**的路线)
 *   使用万能扳手批量拆除 → {@link #deconstruct}  (deconstruct.nbt: 3×3 机械动力机器 + 两个红石零件)
 * </pre>
 *
 * <h2>这个版本按用户 2026-09-23 的四条反馈改过</h2>
 * <ol>
 *   <li><b>不把"举例"写进文字</b>: 文字只讲机制(怎么触发 / 会发生什么), 具体是哪些物品交给画面;</li>
 *   <li><b>Ctrl 的各个版本要在场景里演出来</b>: 连接演"齿轮箱拐角 → 大齿轮拐角"的**实际替换**;
 *       拆除演"只拆红石 → 换回全部再拆剩下的"(成品停留档位用户明确说**不必演示**, 只在加工总述里提一句);</li>
 *   <li><b>连接 = 先选框、选定目标之后才生成结构</b>: 前三下右击只画**节点框 + 路线连线**, 一块方块都不出;
 *       右击目标之后结构才逐块铺好 —— 与游戏里真实发包时机一致;</li>
 *   <li><b>文字不重复、时长要对</b>: 每条 {@code showText(n)} 之后的 idle 之和必须 ≥ n 才会出现下一条,
 *       所以下面每条文字后面都留足了它自己的 n(见行尾注释)。</li>
 * </ol>
 *
 * <p>其余写法规则(逐块生长 / 短陈述句 / placeNearTarget / attachKeyFrame / 不乱上色 / 不用 independent /
 * 不写 markAsFinished / showControls 演手持 / setKineticSpeed 0→64)见 docs/dev/06-ponder.md §15。</p>
 *
 * <p>⚠️ 结构路径的尾段必须等于 {@code assets/create_better_wrench/ponder/wrench/<尾段>.nbt} 的文件名。</p>
 */
public final class WrenchScenes {

    // ---- connect: 7×7 底板, 路线 = 电机 --x--> 拐点1 --z--> 拐点2 --x--> 大齿轮(两次拐点) ----
    private static final BlockPos START = new BlockPos(1, 1, 2);
    private static final BlockPos END = new BlockPos(6, 1, 5);
    private static final BlockPos CORNER1 = new BlockPos(4, 1, 2);
    private static final BlockPos CORNER2 = new BlockPos(4, 1, 5);
    /** 第一段(沿 x)的两根轴; 末格在大齿轮拐角方案里会被换成大齿轮。 */
    private static final BlockPos[] LEG1 = {new BlockPos(2, 1, 2), new BlockPos(3, 1, 2)};
    /** 第二段(沿 z)的两根轴; 首格在大齿轮拐角方案里会被换成大齿轮。 */
    private static final BlockPos[] LEG2 = {new BlockPos(4, 1, 3), new BlockPos(4, 1, 4)};
    private static final BlockPos LEG3 = new BlockPos(5, 1, 5);

    // ---- deconstruct: 5×5 底板, 选区 = (0,1,1) ~ (3,1,3) ----
    private static final int BOX_X0 = 0;
    private static final int BOX_X1 = 3;
    private static final int BOX_Z0 = 1;
    private static final int BOX_Z1 = 3;
    /** 选区里的两个红石零件(用来演示"仅红石"筛选)。 */
    private static final BlockPos[] REDSTONE_CELLS = {new BlockPos(0, 1, 2), new BlockPos(0, 1, 3)};

    private WrenchScenes() {
    }

    // ------------------------------------------------------------------ 使用万能扳手连接应力

    /**
     * 连接: **先选框(起点 → 拐点 → 拐点), 选定目标后才生成结构**, 连完整条线开始转动;
     * 最后把"Ctrl 加滚轮切换拐角做法"的两套做法**都演一遍**。
     */
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

        scene.overlay().showText(70)                     // ① 需 ≥70
            .text("The Universal Wrench will link two kinetic blocks together")
            .pointAt(util.vector().topOf(START))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(70);

        scene.overlay().showControls(util.vector().blockSurface(START, Direction.UP), Pointing.DOWN, 20)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance());
        scene.idle(20);
        scene.overlay().showText(70)                     // ② 需 ≥70
            .text("Right-clicking a block will set the route start")
            .pointAt(util.vector().blockSurface(START, Direction.UP))
            .placeNearTarget();
        scene.overlay().showOutline(PonderPalette.BLUE, "cbw_node_start", util.select().position(START), 80);
        scene.effects().indicateSuccess(START);
        scene.idle(70);

        // ---- 选框: 只画节点框与路线, **一块方块都不出** ----
        scene.overlay().showOutline(PonderPalette.BLUE, "cbw_node_c1", util.select().position(CORNER1), 160);
        scene.overlay().showLine(PonderPalette.WHITE, util.vector().topOf(START), util.vector().topOf(CORNER1), 160);
        scene.overlay().showText(70)                     // ③ 需 ≥70
            .text("Right-clicking a corner block will change the route direction")
            .pointAt(util.vector().topOf(CORNER1))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(70);

        scene.overlay().showOutline(PonderPalette.BLUE, "cbw_node_c2", util.select().position(CORNER2), 90);
        scene.overlay().showLine(PonderPalette.WHITE, util.vector().topOf(CORNER1), util.vector().topOf(CORNER2), 90);
        scene.idle(50);
        scene.overlay().showLine(PonderPalette.WHITE, util.vector().topOf(CORNER2), util.vector().topOf(END), 60);
        scene.idle(30);

        // ---- 选定目标 → 这时才真正铺结构 ----
        scene.overlay().showText(80)                     // ④ 需 ≥80
            .text("The missing shafts will only be placed once the target is picked")
            .pointAt(util.vector().topOf(END))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(20);
        for (BlockPos shaft : LEG1) {
            scene.idle(3);
            scene.world().showSection(util.select().position(shaft), Direction.DOWN);
        }
        scene.idle(3);
        scene.world().showSection(util.select().position(CORNER1), Direction.DOWN);
        for (BlockPos shaft : LEG2) {
            scene.idle(3);
            scene.world().showSection(util.select().position(shaft), Direction.DOWN);
        }
        scene.idle(3);
        scene.world().showSection(util.select().position(CORNER2), Direction.DOWN);
        scene.idle(3);
        scene.world().showSection(util.select().position(LEG3), Direction.DOWN);
        scene.idle(30);                                  // ④ 累计 20+27+30 = 77…再补一拍
        scene.idle(10);                                  //    ⇒ 共 87 ≥ 80 ✓

        // 连通 → 整条线转起来
        scene.world().setKineticSpeed(util.select().fromTo(1, 1, 2, 6, 1, 5), 64);
        scene.effects().indicateSuccess(END);
        scene.overlay().showText(70)                     // ⑤ 需 ≥70
            .colored(PonderPalette.GREEN)
            .text("Linking the end will set the whole line running")
            .pointAt(util.vector().topOf(END))
            .placeNearTarget();
        scene.idle(70);

        // ---- Ctrl 演示: 把"齿轮箱拐角"换成"两个大齿轮拐角", 两套做法都看得见 ----
        scene.overlay().showText(90)                     // ⑥ 需 ≥90
            .text("Ctrl and Scroll will switch how a corner is built")
            .pointAt(util.vector().topOf(CORNER1))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(30);
        BlockPos cogA = LEG1[LEG1.length - 1];           // 入段末格
        BlockPos cogB = LEG2[0];                         // 出段首格(与 cogA 斜对角)
        scene.world().setBlock(CORNER1, Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(cogA, largeCog(Direction.Axis.X), false);
        scene.world().setBlock(cogB, largeCog(Direction.Axis.Z), false);
        scene.world().setKineticSpeed(util.select().fromTo(1, 1, 2, 6, 1, 5), 64);
        scene.effects().indicateSuccess(CORNER1);
        scene.idle(60);                                  // ⑥ 累计 30+60 = 90 ✓
    }

    /** 大齿轮拐角用的方块状态(与连接功能落块时同源)。 */
    private static BlockState largeCog(Direction.Axis axis) {
        return AllBlocks.LARGE_COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, axis);
    }

    // ------------------------------------------------------------------ 使用万能扳手批量拆除

    /**
     * 批量拆除: **先选第一个角, 再选对角**框出选区; 然后演示 Ctrl 的筛选 ——
     * 先"仅红石"拆掉红石零件, 再换回"全部"把剩下的机械动力方块也拆掉。
     */
    public static void deconstruct(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("wrench_deconstruct", "Removing Blocks using the Universal Wrench");
        scene.configureBasePlate(0, 0, 5);

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        // 3×3 的机械动力机器: 一块块长出来
        for (int z = 1; z <= 3; z++) {
            for (int x = 1; x <= 3; x++) {
                scene.idle(3);
                scene.world().showSection(util.select().position(x, 1, z), Direction.DOWN);
            }
        }
        // 旁边的两个红石零件
        for (BlockPos cell : REDSTONE_CELLS) {
            scene.idle(3);
            scene.world().showSection(util.select().position(cell), Direction.DOWN);
        }
        scene.idle(20);

        scene.overlay().showText(70)                     // ① 需 ≥70
            .text("The Universal Wrench can remove a whole area at once")
            .pointAt(util.vector().topOf(2, 1, 2))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(70);

        // 第一次右击: 只选中**一个角**
        BlockPos cornerA = new BlockPos(BOX_X0, 1, BOX_Z0);
        BlockPos cornerB = new BlockPos(BOX_X1, 1, BOX_Z1);
        scene.overlay().showControls(util.vector().blockSurface(cornerA, Direction.UP), Pointing.DOWN, 20)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance());
        scene.idle(20);
        scene.overlay().showText(70)                     // ② 需 ≥70
            .text("Right-clicking a block will set the first corner")
            .pointAt(util.vector().blockSurface(cornerA, Direction.UP))
            .placeNearTarget();
        scene.overlay().showOutline(PonderPalette.BLUE, "cbw_sel_a", util.select().position(cornerA), 80);
        scene.effects().indicateSuccess(cornerA);
        scene.idle(70);

        // 第二次右击: 对角 → 选区成形
        scene.overlay().showControls(util.vector().blockSurface(cornerB, Direction.UP), Pointing.DOWN, 20)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance());
        scene.idle(20);
        scene.overlay().showText(70)                     // ③ 需 ≥70
            .text("Right-clicking the opposite corner will finish the selection")
            .pointAt(util.vector().blockSurface(cornerB, Direction.UP))
            .placeNearTarget()
            .attachKeyFrame();
        scene.overlay().showOutline(PonderPalette.BLUE, "cbw_sel",
            util.select().fromTo(BOX_X0, 1, BOX_Z0, BOX_X1, 1, BOX_Z1), 70);
        scene.effects().indicateSuccess(cornerB);
        scene.idle(70);

        // ---- Ctrl 演示(一): 只拆红石 ----
        scene.overlay().showText(70)                     // ④ 需 ≥70
            .text("Ctrl and Scroll will narrow the selection down to one kind of block")
            .pointAt(util.vector().topOf(2, 1, 2))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(30);
        for (BlockPos cell : REDSTONE_CELLS) {
            scene.idle(10);
            scene.world().destroyBlock(cell);
        }
        scene.idle(30);                                  // ④ 累计 30+20+30 = 80 ≥ 70 ✓

        // ---- Ctrl 演示(二): 换回全部 → 剩下的也拆掉 ----
        scene.overlay().showText(70)                     // ⑤ 需 ≥70
            .text("Switching back will also remove what is left inside")
            .pointAt(util.vector().topOf(2, 1, 2))
            .placeNearTarget();
        scene.idle(10);
        for (int z = 1; z <= 3; z++) {
            for (int x = 1; x <= 3; x++) {
                scene.idle(3);
                scene.world().destroyBlock(new BlockPos(x, 1, z));
            }
        }
        scene.idle(40);                                  // ⑤ 累计 10+27+40 = 77 ≥ 70 ✓
    }
}
