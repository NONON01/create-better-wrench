package com.nonono.createbetterwrench.client.ponder.scenes;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 直接附属于**万能扳手**的两段场景。
 *
 * <pre>
 *   使用万能扳手连接应力 → {@link #connect}      (connect.nbt: 创造模式引擎 --两次拐点--&gt; 鼓风机)
 *   使用万能扳手批量拆除 → {@link #deconstruct}  (deconstruct.nbt: 3×3 区域内机械动力与红石方块夹杂)
 * </pre>
 *
 * <h2>本版按用户 2026-09-23 第二轮反馈重写</h2>
 * <ul>
 *   <li><b>右击要有反馈</b>: 右击本身没有特效的方块(起点 / 拐点 / 终点 / 置物台)一律给
 *       {@link #SELECT 黄色选框} + “拿着扳手右击”的图标({@code showControls(...).withItem(...).rightClick()});</li>
 *   <li><b>连接</b>: 起点/拐点/终点都只是**选框 + 连线**, 到终点时先画**一整条连续的绿色框**,
 *       绿框消失后中间的结构才长出来(先选框 → 选完才建);</li>
 *   <li><b>拆除</b>: 3×3 里机械动力方块与红石方块**夹杂**; 先演一遍正常流程(全拆) → **复原** →
 *       演示 Ctrl 切换时**先显示当前范围**(黄色高亮即将被拆的那些) → 然后**立刻**(同一拍)把它们拆掉;</li>
 *   <li><b>文字</b>: 全部换成用户给定的句子。</li>
 * </ul>
 *
 * <p>⚠️ 结构路径的尾段必须等于 {@code assets/create_better_wrench/ponder/wrench/<尾段>.nbt} 的文件名。</p>
 */
public final class WrenchScenes {

    /**
     * “黄色选框”。⚠️ Ponder 的调色板里**没有纯黄**(WHITE/BLACK/RED/GREEN/BLUE/SLOW/MEDIUM/FAST/INPUT/OUTPUT),
     * 其中 {@code OUTPUT = 0xDDC166} 是唯一的金黄, 所以“右击黄色选框”用它。
     */
    private static final PonderPalette SELECT = PonderPalette.OUTPUT;

    // ---- connect: 7×7 底板, 路线 = 创造模式引擎 --x--> 拐点1 --z--> 拐点2 --x--> 鼓风机 ----
    private static final BlockPos START = new BlockPos(1, 1, 2);
    private static final BlockPos END = new BlockPos(6, 1, 5);
    private static final BlockPos CORNER1 = new BlockPos(4, 1, 2);
    private static final BlockPos CORNER2 = new BlockPos(4, 1, 5);
    /** 第一段(沿 x)的两根轴; 末格在"大齿轮拐角"方案里会变成大齿轮。 */
    private static final BlockPos[] LEG1 = {new BlockPos(2, 1, 2), new BlockPos(3, 1, 2)};
    /** 第二段(沿 z)的两根轴; 首格在"大齿轮拐角"方案里会变成大齿轮。 */
    private static final BlockPos[] LEG2 = {new BlockPos(4, 1, 3), new BlockPos(4, 1, 4)};
    /** 第三段(沿 x)的一根轴; 在"大齿轮拐角"方案里会变成大齿轮。 */
    private static final BlockPos LEG3 = new BlockPos(5, 1, 5);

    // ---- deconstruct: 5×5 底板, 选区 = (1,1,1) ~ (3,1,3), 机械动力与红石夹杂 ----
    private static final BlockPos BOX_A = new BlockPos(1, 1, 1);
    private static final BlockPos BOX_B = new BlockPos(3, 1, 3);
    /** “仅红石”档会拆掉的 4 格(方块 id 都含红石关键字, 且都在 create:wrench_pickup 里)。 */
    private static final BlockPos[] REDSTONE_CELLS = {
        new BlockPos(2, 1, 1), new BlockPos(1, 1, 2), new BlockPos(3, 1, 2), new BlockPos(2, 1, 3)
    };
    /** “仅机械动力”档会拆掉的 5 格。 */
    private static final BlockPos[] CREATE_CELLS = {
        new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(2, 1, 2), new BlockPos(1, 1, 3), new BlockPos(3, 1, 3)
    };

    private WrenchScenes() {
    }

    // ------------------------------------------------------------------ 使用万能扳手连接应力

    /**
     * 连接: 右击起点 → 右击两个拐点 → 右击终点, 全程只画**选框 + 路线**;
     * 到终点时先亮一整条**绿色连续框**, 绿框消失后结构才逐块长出来, 然后整条线转起来;
     * 收尾把 Ctrl 的两种拐点实现方式(**齿轮箱** / **大齿轮**)都演一遍。
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

        // ---- ① 右击起点: 扳手右击图标 + 黄色选框 ----
        scene.overlay().showControls(util.vector().topOf(START), Pointing.DOWN, 20)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance())
            .rightClick();
        scene.idle(20);
        scene.overlay().showOutline(SELECT, "cbw_node_start", util.select().position(START), 80);
        scene.effects().indicateSuccess(START);
        scene.overlay().showText(70)
            .text("Right-clicking a block that can join a stress network will set the start")
            .pointAt(util.vector().topOf(START))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(70);

        // ---- ② 右击途中 = 加拐点 ----
        scene.overlay().showControls(util.vector().topOf(CORNER1), Pointing.DOWN, 20)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance())
            .rightClick();
        scene.idle(20);
        scene.overlay().showOutline(SELECT, "cbw_node_c1", util.select().position(CORNER1), 80);
        scene.overlay().showLine(PonderPalette.WHITE, util.vector().topOf(START), util.vector().topOf(CORNER1), 80);
        scene.overlay().showText(70)
            .text("Right-clicking along the way will add a corner")
            .pointAt(util.vector().topOf(CORNER1))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(70);

        // ---- 第二个拐点(同上, 不加文字) ----
        scene.overlay().showControls(util.vector().topOf(CORNER2), Pointing.DOWN, 20)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance())
            .rightClick();
        scene.idle(20);
        scene.overlay().showOutline(SELECT, "cbw_node_c2", util.select().position(CORNER2), 90);
        scene.overlay().showLine(PonderPalette.WHITE, util.vector().topOf(CORNER1), util.vector().topOf(CORNER2), 90);
        scene.idle(50);

        // ---- ③ 右击终点 ----
        scene.overlay().showControls(util.vector().topOf(END), Pointing.DOWN, 20)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance())
            .rightClick();
        scene.idle(20);
        scene.overlay().showOutline(SELECT, "cbw_node_end", util.select().position(END), 80);
        scene.overlay().showLine(PonderPalette.WHITE, util.vector().topOf(CORNER2), util.vector().topOf(END), 80);
        scene.overlay().showText(70)
            .text("Right-clicking another block that can join the network will set the end")
            .pointAt(util.vector().topOf(END))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(70);

        // ---- ④ 一整条连续的绿色框 → 框消失 → 结构逐块长出来 ----
        Selection route = route(util);
        scene.overlay().showOutline(PonderPalette.GREEN, "cbw_route", route, 50);
        scene.idle(50);

        scene.overlay().showText(80)
            .text("The transmission structure will be built")
            .pointAt(util.vector().topOf(CORNER1))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(20);
        for (BlockPos shaft : LEG1) {
            scene.idle(4);
            scene.world().showSection(util.select().position(shaft), Direction.DOWN);
        }
        scene.idle(4);
        scene.world().showSection(util.select().position(CORNER1), Direction.DOWN);
        for (BlockPos shaft : LEG2) {
            scene.idle(4);
            scene.world().showSection(util.select().position(shaft), Direction.DOWN);
        }
        scene.idle(4);
        scene.world().showSection(util.select().position(CORNER2), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(LEG3), Direction.DOWN);
        scene.idle(30);                                  // ④ 合计 20+20+30 = 70…再补一拍
        scene.idle(20);                                  //    ⇒ 共 90 ≥ 80 ✓

        // 连通 → 整条线转起来
        scene.world().setKineticSpeed(route, 64);
        scene.effects().indicateSuccess(END);
        scene.idle(20);

        // ---- ⑤ Ctrl + 滚轮: 切换拐点的实现方式 ----
        scene.overlay().showControls(util.vector().topOf(CORNER1), Pointing.DOWN, 30)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance())
            .whileCTRL()
            .scroll();
        scene.overlay().showText(90)
            .text("Ctrl and Scroll will switch how a corner is built")
            .pointAt(util.vector().topOf(CORNER1))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        // ---- ⑥ 方式一: 齿轮箱 ----
        scene.overlay().showOutline(SELECT, "cbw_corner_gearbox",
            util.select().position(CORNER1).add(util.select().position(CORNER2)), 70);
        scene.overlay().showText(70)
            .text("Corner type: Gearbox")
            .pointAt(util.vector().topOf(CORNER1))
            .placeNearTarget();
        scene.idle(70);

        // ---- ⑦ 方式二: 大齿轮(两个拐点都换成大齿轮拼角) ----
        scene.overlay().showText(70)
            .text("Corner type: Large Cogs")
            .pointAt(util.vector().topOf(CORNER1))
            .placeNearTarget();
        scene.world().setBlock(CORNER1, Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(LEG1[LEG1.length - 1], largeCog(Direction.Axis.X), false);
        scene.world().setBlock(LEG2[0], largeCog(Direction.Axis.Z), false);
        scene.world().setBlock(CORNER2, Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(LEG2[LEG2.length - 1], largeCog(Direction.Axis.Z), false);
        scene.world().setBlock(LEG3, largeCog(Direction.Axis.X), false);
        scene.world().setKineticSpeed(route, 64);
        scene.effects().indicateSuccess(CORNER1);
        scene.effects().indicateSuccess(CORNER2);
        scene.idle(20);
        scene.overlay().showOutline(SELECT, "cbw_corner_cogs",
            util.select().position(LEG1[LEG1.length - 1]).add(util.select().position(LEG2[0])), 60);
        scene.idle(50);
    }

    /** 整条路线的单元格(起点 → 拐点1 → 拐点2 → 终点), 用来画"完整连续的绿色框"和给整线加速。 */
    private static Selection route(SceneBuildingUtil util) {
        return util.select().fromTo(START, CORNER1)
            .add(util.select().fromTo(CORNER1, CORNER2))
            .add(util.select().fromTo(CORNER2, END));
    }

    /** 大齿轮拐角用的方块状态(与连接功能落块时同源)。 */
    private static BlockState largeCog(Direction.Axis axis) {
        return AllBlocks.LARGE_COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, axis);
    }

    // ------------------------------------------------------------------ 使用万能扳手批量拆除

    /**
     * 批量拆除: 右击第一个角 → 右击对角框出选区 → **正常流程**(一块块拆完) →
     * **复原** → 演示 Ctrl: 先显示当前范围(黄色高亮即将被拆的方块), 再**立刻**把它们拆掉
     * (先"仅红石" → 复原 → 再"仅机械动力")。
     */
    public static void deconstruct(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("wrench_deconstruct", "Removing Blocks using the Universal Wrench");
        scene.configureBasePlate(0, 0, 5);

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        // 3×3 区域: 机械动力方块与红石方块**夹杂**, 一块块长出来
        for (int z = 1; z <= 3; z++) {
            for (int x = 1; x <= 3; x++) {
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
        scene.idle(70);

        // ---- 正常流程: 两次右击定角 ----
        scene.overlay().showControls(util.vector().topOf(BOX_A), Pointing.DOWN, 20)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance())
            .rightClick();
        scene.idle(20);
        scene.overlay().showOutline(SELECT, "cbw_sel_a", util.select().position(BOX_A), 80);
        scene.effects().indicateSuccess(BOX_A);
        scene.overlay().showText(70)
            .text("Right-clicking a block will set the first corner")
            .pointAt(util.vector().topOf(BOX_A))
            .placeNearTarget();
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(BOX_B), Pointing.DOWN, 20)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance())
            .rightClick();
        scene.idle(20);
        scene.overlay().showOutline(SELECT, "cbw_sel", util.select().fromTo(BOX_A, BOX_B), 70);
        scene.effects().indicateSuccess(BOX_B);
        scene.overlay().showText(70)
            .text("Right-clicking the opposite corner will finish the selection")
            .pointAt(util.vector().topOf(BOX_B))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(70);

        // 正常流程: 一块块拆完(9 块)
        for (int z = 1; z <= 3; z++) {
            for (int x = 1; x <= 3; x++) {
                scene.idle(4);
                scene.world().destroyBlock(new BlockPos(x, 1, z));
            }
        }
        scene.idle(20);

        // ---- 复原, 准备演示 Ctrl ----
        scene.world().restoreBlocks(util.select().fromTo(BOX_A, BOX_B));
        scene.idle(20);

        scene.overlay().showControls(util.vector().topOf(BOX_A), Pointing.DOWN, 30)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance())
            .whileCTRL()
            .scroll();
        scene.overlay().showText(70)
            .text("Ctrl and Scroll will switch which blocks are in scope")
            .pointAt(util.vector().topOf(2, 1, 2))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(20);

        // 范围一: 仅红石 —— 先"显示当前范围", 再**立刻**拆掉
        scene.overlay().showOutline(SELECT, "cbw_scope_redstone", cells(util, REDSTONE_CELLS), 40);
        scene.idle(40);
        for (BlockPos cell : REDSTONE_CELLS) {
            scene.world().destroyBlock(cell);
        }
        scene.idle(30);                                  // 合计 20+40+30 = 90 ≥ 70 ✓

        // 复原红石, 范围二: 仅机械动力 —— 同样先显示范围, 再**立刻**拆掉
        scene.world().restoreBlocks(cells(util, REDSTONE_CELLS));
        scene.overlay().showOutline(SELECT, "cbw_scope_create", cells(util, CREATE_CELLS), 40);
        scene.overlay().showText(70)
            .text("Only blocks that match the current scope will be removed")
            .pointAt(util.vector().topOf(2, 1, 2))
            .placeNearTarget();
        scene.idle(20);
        for (BlockPos cell : CREATE_CELLS) {
            scene.world().destroyBlock(cell);
        }
        scene.idle(50);                                  // 合计 20+50 = 70 ≥ 70 ✓
    }

    /** 把若干单元格合成一个选区(用于黄色高亮 / 复原)。 */
    private static Selection cells(SceneBuildingUtil util, BlockPos[] positions) {
        Selection selection = util.select().position(positions[0]);
        for (int i = 1; i < positions.length; i++) {
            selection = selection.add(util.select().position(positions[i]));
        }
        return selection;
    }
}
