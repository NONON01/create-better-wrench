package com.nonono.createbetterwrench.client.ponder.scenes;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;

/**
 * 万能扳手的思索场景(第一段: 总览)。
 *
 * <p><b>⚠️ 两个 id 别混</b>(docs/13 §2.3 / §5):</p>
 * <ul>
 *   <li>{@code addStoryBoard("wrench/overview", ...)} 的字符串 = **结构文件路径**
 *       ⇒ {@code assets/create_better_wrench/ponder/wrench/overview.nbt};</li>
 *   <li>{@code title("wrench_overview", ...)} 的第一个参数 = **语言键前缀**
 *       ⇒ 文本键 {@code create_better_wrench.ponder.wrench_overview.header} / {@code .text_1...}。</li>
 * </ul>
 *
 * <p>结构文件由 {@code tools/nbt/PonderSchemGen.java} 程序化生成(5×3×5: 25 块白混凝土底板 +
 * 一小段传动作为装饰), 格式 = **原版结构 NBT**(Ponder 内部就是 {@code StructureTemplate})。</p>
 */
public final class WrenchScenes {

    private WrenchScenes() {
    }

    /** 总览: 五种模式 + ALT 工具栏 + 各模式的 Ctrl+滚轮选项。 */
    public static void overview(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("wrench_overview", "Universal Wrench");
        scene.configureBasePlate(0, 0, 5);

        // 底板(y=0)先浮起来, 再逐段展示装饰用的传动
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 2, 1, 2), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(3, 1, 2), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(3, 2, 2), Direction.DOWN);
        scene.idle(20);

        // ① 这是什么
        scene.overlay().showText(80)
            .text("The Universal Wrench works on Create blocks while adding five modes of its own")
            .attachKeyFrame()
            .colored(PonderPalette.BLUE)
            .placeNearTarget()
            .pointAt(util.vector().topOf(2, 1, 2));
        scene.overlay().showControls(util.vector().topOf(2, 1, 2), Pointing.DOWN, 40)
            .withItem(BetterWrenchMod.BETTER_WRENCH.get().getDefaultInstance());
        scene.idle(90);

        // ② 怎么切模式
        scene.overlay().showText(80)
            .text("Hold ALT to open the mode bar, then scroll to switch between Wrench, Connect, Deconstruct, Process and Mod Info")
            .independent()
            .colored(PonderPalette.GREEN);
        scene.idle(90);

        // ③ 每个模式的 Ctrl+滚轮选项
        scene.overlay().showText(80)
            .text("Each mode has a Ctrl + Scroll option: corner type, deconstruct filter, or the product stay time")
            .independent()
            .colored(PonderPalette.WHITE);
        scene.idle(90);

        scene.markAsFinished();
    }
}
