package com.nonono.createbetterwrench.client.ponder.scenes;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 「锁链传动」模式的思索场景(单情景), 按分镜依次演示 标准 / 直线 / 半自动。
 *
 * <p>结构 chain.nbt 为 9x5x9 底板: 起点 (2,1,6) 与终点 (9,1,2) 位于场景对角; 标准段的中间轮 (5,1,4)、
 * 直线段的拐点 (9,1,6)、半自动段的两个中继位与高出三格的终点 (9,4,2) 都藏在结构里, 由本场景按分镜
 * 逐步显现与移除。演示节奏与实机一致: 连接只发生在两次右击之后, 半自动的每个节点先出现选择框,
 * 之后才放置传动轮并建立连接。</p>
 */
public final class ChainScenes {

    private static final PonderPalette SELECT = PonderPalette.OUTPUT;
    private static final int SPEED = 32;

    private ChainScenes() {
    }

    /** 标准 / 直线 / 半自动依次演示, 每段结束移除该段产生的传动轮与连接。 */
    public static void chain(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("wrench_chain", "Laying Chain Conveyors");
        scene.configureBasePlate(0, 0, 9);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos start = new BlockPos(1, 1, 7);
        BlockPos end = new BlockPos(7, 1, 1);

        // ================= 标准 =================
        BlockPos mid = new BlockPos(4, 1, 4);
        scene.world().showSection(util.select().position(start), Direction.DOWN);
        scene.world().showSection(util.select().position(end), Direction.DOWN);
        scene.idle(15);

        click(scene, util, start);
        scene.overlay().showOutline(SELECT, "cbw_chain_std_a", util.select().position(start), 50);
        scene.overlay().showText(50)
            .text("Right-click the first wheel to set the start")
            .pointAt(util.vector().topOf(start))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(60);

        click(scene, util, end);
        scene.overlay().showOutline(SELECT, "cbw_chain_std_b", util.select().position(end), 50);
        scene.overlay().showText(50)
            .text("Right-click the second wheel to set the end")
            .pointAt(util.vector().topOf(end))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(60);

        scene.world().showSection(util.select().position(mid), Direction.DOWN);
        scene.idle(12);
        connect(scene, start, mid);
        scene.idle(8);
        connect(scene, mid, end);
        scene.world().setKineticSpeed(util.select().fromTo(start, end), SPEED);
        scene.overlay().showText(60)
            .text("The missing wheels and links are filled in automatically")
            .pointAt(util.vector().topOf(mid))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(70);

        disconnect(scene, start, mid);
        disconnect(scene, mid, end);
        scene.world().hideSection(util.select().position(mid), Direction.DOWN);
        scene.idle(15);

        // ================= 切换提示 =================
        scene.overlay().showControls(util.vector().topOf(start), Pointing.DOWN, 30)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance())
            .whileCTRL()
            .scroll();
        scene.overlay().showText(60)
            .text("Ctrl and Scroll switches between Standard, Straight and Semi-Auto")
            .pointAt(util.vector().topOf(start))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(70);

        // ================= 直线 =================
        BlockPos corner = new BlockPos(7, 1, 7);
        click(scene, util, start);
        scene.overlay().showOutline(SELECT, "cbw_chain_str_a", util.select().position(start), 45);
        scene.overlay().showText(45)
            .text("Pick the start again")
            .pointAt(util.vector().topOf(start))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(55);

        click(scene, util, end);
        scene.overlay().showOutline(SELECT, "cbw_chain_str_b", util.select().position(end), 45);
        scene.overlay().showText(45)
            .text("Now pick the end")
            .pointAt(util.vector().topOf(end))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(55);

        scene.world().showSection(util.select().position(corner), Direction.DOWN);
        scene.idle(12);
        connect(scene, start, corner);
        scene.overlay().showLine(PonderPalette.GREEN, util.vector().topOf(start), util.vector().topOf(corner), 65);
        scene.idle(8);
        connect(scene, corner, end);
        scene.overlay().showLine(PonderPalette.GREEN, util.vector().topOf(corner), util.vector().topOf(end), 65);
        scene.world().setKineticSpeed(util.select().fromTo(start, end), SPEED);
        scene.overlay().showText(60)
            .text("Links are completed along the X or Z axis")
            .pointAt(util.vector().topOf(corner))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(70);

        disconnect(scene, start, corner);
        disconnect(scene, corner, end);
        scene.world().hideSection(util.select().position(end), Direction.DOWN);
        scene.world().hideSection(util.select().position(corner), Direction.DOWN);
        scene.idle(15);

        // ================= 半自动 =================
        BlockPos relay = new BlockPos(7, 3, 7);
        BlockPos highEnd = new BlockPos(7, 4, 1);
        BlockPos relayGround = new BlockPos(1, 1, 1);
        BlockPos relayLow = new BlockPos(1, 2, 1);

        scene.world().showSection(util.select().position(highEnd), Direction.DOWN);
        scene.idle(12);
        scene.overlay().showText(60)
            .text("The waypoints are chosen by hand")
            .pointAt(util.vector().topOf(highEnd))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(70);

        click(scene, util, start);
        scene.overlay().showOutline(SELECT, "cbw_chain_semi_a", util.select().position(start), 45);
        scene.idle(55);

        click(scene, util, relayGround);
        scene.overlay().showOutline(SELECT, "cbw_chain_semi_col1", util.select().position(relayGround), 60);
        scene.overlay().showText(55)
            .text("Pick the first waypoint: only its X and Z are used")
            .pointAt(util.vector().centerOf(relayGround))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(65);

        click(scene, util, corner);
        scene.overlay().showOutline(SELECT, "cbw_chain_semi_col2", util.select().position(corner), 60);
        scene.overlay().showText(50)
            .text("Pick the second waypoint")
            .pointAt(util.vector().centerOf(corner))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(60);

        click(scene, util, highEnd);
        scene.overlay().showText(55)
            .text("Pick the end")
            .pointAt(util.vector().topOf(highEnd))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(65);

        scene.world().showSection(util.select().position(relayLow), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(relay), Direction.DOWN);
        scene.idle(10);
        connect(scene, start, relayLow);
        scene.idle(8);
        connect(scene, relayLow, relay);
        scene.idle(8);
        connect(scene, relay, highEnd);
        scene.world().setKineticSpeed(util.select().fromTo(start, highEnd), SPEED);
        scene.effects().indicateSuccess(highEnd);
        scene.overlay().showText(60)
            .text("Heights at the chosen spots are solved automatically")
            .pointAt(util.vector().topOf(relay))
            .placeNearTarget()
            .attachKeyFrame();
        // 最后一条文本播放时, 依次把三个节点离地的高度用黄框逐格标出(不带文字)
        highlightHeight(scene, util, relayLow, 1);
        highlightHeight(scene, util, relay, 2);
        highlightHeight(scene, util, highEnd, 3);
        scene.idle(70);
    }

    /** 一次右击: 扳手气泡先出现, 随后由调用方展示选择结果。 */
    private static void click(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos) {
        scene.overlay().showControls(util.vector().topOf(pos), Pointing.DOWN, 25)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance())
            .rightClick();
        scene.idle(25);
    }

    /**
     * 由下往上逐格用黄色选择框标出该传动轮离地的高度(不配文字)。
     *
     * <p>用于半自动段: 传动轮在指定处生成后, 其下方的每一格同时亮起黄框, 直观表示该节点被抬高了
     * 多少格; 框的存活时间较长, 因此会逐层保留到本段结束。</p>
     */
    private static void highlightHeight(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos, int levels) {
        for (int i = 1; i <= levels; i++) {
            scene.overlay().showOutline(SELECT, "cbw_chain_h_" + pos.toShortString() + "_" + i,
                util.select().position(pos.below(i)), 300);
        }
    }

    /** 建立两台传动轮之间的连接(相对坐标, 链子由引擎渲染)。 */
    private static void connect(CreateSceneBuilder scene, BlockPos a, BlockPos b) {
        scene.world().modifyBlockEntity(a, ChainConveyorBlockEntity.class,
            be -> be.connections.add(b.subtract(a)));
        scene.world().modifyBlockEntity(b, ChainConveyorBlockEntity.class,
            be -> be.connections.add(a.subtract(b)));
    }

    /** 断开两台传动轮之间的连接(用于段落之间的清理)。 */
    private static void disconnect(CreateSceneBuilder scene, BlockPos a, BlockPos b) {
        scene.world().modifyBlockEntity(a, ChainConveyorBlockEntity.class,
            be -> be.connections.remove(b.subtract(a)));
        scene.world().modifyBlockEntity(b, ChainConveyorBlockEntity.class,
            be -> be.connections.remove(a.subtract(b)));
    }
}
