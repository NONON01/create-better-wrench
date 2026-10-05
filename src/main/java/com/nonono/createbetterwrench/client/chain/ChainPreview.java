package com.nonono.createbetterwrench.client.chain;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.AllSpecialTextures;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 「锁链传动」的预览绘制, 只由选择状态机在需要时调用, 本类不订阅任何事件, 也不保存规划结果。
 *
 * <ul>
 *   <li>合法路径: 相邻两轮之间绘制绿色连线, 其中需要新建的传动轮额外绘制绿色方框;</li>
 *   <li>非法路径: 沿选中折线(起点到当前候选点或终点)绘制整条红色连线, 并在悬停方块处绘制红框
 *       (规格第 3.2 节的「红框或红色路径」两处都有); 拿不到选中折线时只画红框;</li>
 *   <li>尚未产生计划: 在悬停方块处绘制金色候选框, 与「连接」模式的起点候选一致;</li>
 *   <li>半自动模式的选择预览: 底面 1x1、覆盖世界建筑高度的竖直柱体; 该点试算合法时用「连接」与「拆除」
 *       两个模式一致的金色选择框, 试算非法时改为与非法路径一致的红色。</li>
 * </ul>
 *
 * <p>本类只记录自身写入 Outliner 的键, 因此 {@link #clear()} 可以精确移除本类绘制的内容,
 * 不影响其它模式的选择框。{@link Outliner#showLine} 写入的线条只存活一个游戏刻, 需要每刻重复调用
 * 才能持续可见; 方框使用 {@code chaseAABB}, 调用一次即持续显示, 由 {@link #clear()} 移除。
 * 调用方因此按「每客户端刻刷新一次预览」的既有习惯(与连接、拆除两个选择状态机一致)调用本类即可。</p>
 */
public final class ChainPreview {

    private static final String PATH_KEY_PREFIX = "create_better_wrench:chain_path_";
    private static final String PLACED_KEY_PREFIX = "create_better_wrench:chain_placed_";
    private static final Object HOVER_KEY = "create_better_wrench:chain_hover";
    private static final Object COLUMN_KEY = "create_better_wrench:chain_column";

    /** 合法路径的绿色(与连接模式的合法提示同色)。 */
    private static final int COLOR_VALID = 0x95CD41;
    /** 非法路径的红色(与连接模式的非法提示同色)。 */
    private static final int COLOR_INVALID = 0xEA5C2B;
    /** 选择框的金色(与连接、拆除两个模式的选择框同色)。 */
    private static final int COLOR_SELECTION = 0xE8B54C;
    private static final float LINE_WIDTH = 1 / 16f;

    private static final List<Object> PATH_KEYS = new ArrayList<>();
    private static final List<Object> COLUMN_KEYS = new ArrayList<>();

    private ChainPreview() {
    }

    /** 绘制一次规划结果的预览(非法时不显示红色路径, 仅保留原有的红框提示)。 */
    public static void show(ChainPlanner.Plan plan, BlockPos hovered) {
        show(plan, hovered, null);
    }

    /**
     * 绘制一次规划结果的预览。
     *
     * <p>合法时沿各段连接绘制绿色连线, 并给需要新建的传动轮加绿色方框; 非法时沿 {@code selectedPath} 绘制
     * 整条红色连线(规格第 3.2 节的「红色路径」), 使玩家一眼看出整条候选路线不可用, 而不是只看准星那一格;
     * 拿不到选中折线(例如尚未选起点)时退回只在悬停方块处画红框的既有行为。</p>
     *
     * <p>连线使用 {@link Outliner#showLine}, 只存活一个游戏刻, 由调用方每客户端刻重复调用刷新;
     * 本方法每次先清除自己写入的路径键, 因此红绿两种画法不会互相残留。</p>
     *
     * @param plan         规划结果; 为 {@code null} 时只按悬停位置绘制起点候选框;
     * @param hovered      当前悬停方块; 为 {@code null} 时不绘制悬停框;
     * @param selectedPath 起点到当前候选点或终点的选中折线, 非法时用作红色路径; 可为 {@code null}。
     */
    public static void show(ChainPlanner.Plan plan, BlockPos hovered, List<BlockPos> selectedPath) {
        Outliner outliner = Outliner.getInstance();
        clearPath();

        if (plan != null && plan.valid()) {
            int index = 0;
            for (ChainPlanner.Link link : plan.links()) {
                Object key = PATH_KEY_PREFIX + index++;
                outliner.showLine(key, Vec3.atCenterOf(link.a()), Vec3.atCenterOf(link.b()))
                    .colored(COLOR_VALID)
                    .lineWidth(LINE_WIDTH);
                PATH_KEYS.add(key);
            }
            index = 0;
            for (BlockPos pos : plan.placed()) {
                Object key = PLACED_KEY_PREFIX + index++;
                outliner.chaseAABB(key, new AABB(pos))
                    .colored(COLOR_VALID)
                    .lineWidth(LINE_WIDTH);
                PATH_KEYS.add(key);
            }
        } else if (plan != null && selectedPath != null) {
            int index = 0;
            for (int i = 1; i < selectedPath.size(); i++) {
                BlockPos from = selectedPath.get(i - 1);
                BlockPos to = selectedPath.get(i);
                if (from.equals(to))
                    continue;                   // 同一列重复出现时不画零长线段
                Object key = PATH_KEY_PREFIX + index++;
                outliner.showLine(key, Vec3.atCenterOf(from), Vec3.atCenterOf(to))
                    .colored(COLOR_INVALID)
                    .lineWidth(LINE_WIDTH);
                PATH_KEYS.add(key);
            }
        }

        if (hovered != null) {
            int color;
            if (plan == null)
                color = COLOR_SELECTION;        // 尚未产生计划: 悬停的起点候选
            else
                color = plan.valid() ? COLOR_VALID : COLOR_INVALID;
            outliner.chaseAABB(HOVER_KEY, new AABB(hovered))
                .colored(color)
                .lineWidth(LINE_WIDTH);
            PATH_KEYS.add(HOVER_KEY);
        }
    }

    /** 绘制半自动模式的竖直选择柱体, 默认按合法着色。 */
    public static void showColumn(BlockPos pos) {
        showColumn(pos, true);
    }

    /**
     * 绘制半自动模式的竖直选择柱体, 并按试算结果着色。
     *
     * <p>底面为所选方块的 X 与 Z, 高度自世界最低建筑高度覆盖到最高建筑高度, 用于表达「只选择 X 与 Z」。
     * 柱体所在列即最终放置传动轮的列。合法时使用选择金色, 非法时使用与非法路径一致的红色, 使玩家在按下
     * 右键之前就能从柱体颜色看出该点是否可用。</p>
     *
     * @param pos   准星所指的方块;
     * @param valid 该点作为中间点的试算结果。
     */
    public static void showColumn(BlockPos pos, boolean valid) {
        clearColumn();
        Level level = Minecraft.getInstance().level;
        if (pos == null || level == null)
            return;

        AABB column = new AABB(pos.getX(), level.getMinBuildHeight(), pos.getZ(),
            pos.getX() + 1.0, level.getMaxBuildHeight(), pos.getZ() + 1.0);
        Outliner.getInstance().chaseAABB(COLUMN_KEY, column)
            .colored(valid ? COLOR_SELECTION : COLOR_INVALID)
            .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
            .lineWidth(LINE_WIDTH);
        COLUMN_KEYS.add(COLUMN_KEY);
    }

    /** 移除本类绘制的全部预览(路径连线、新建传动轮方框、悬停框与竖直柱体)。 */
    public static void clear() {
        clearPath();
        clearColumn();
    }

    private static void clearPath() {
        Outliner outliner = Outliner.getInstance();
        for (Object key : PATH_KEYS)
            outliner.remove(key);
        PATH_KEYS.clear();
    }

    private static void clearColumn() {
        Outliner outliner = Outliner.getInstance();
        for (Object key : COLUMN_KEYS)
            outliner.remove(key);
        COLUMN_KEYS.clear();
    }
}
