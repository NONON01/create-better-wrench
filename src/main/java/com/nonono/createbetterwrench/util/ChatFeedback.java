package com.nonono.createbetterwrench.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

/**
 * 面向玩家的**聊天栏**反馈(不是 actionbar)。
 *
 * <p>统一格式:</p>
 * <pre>[CBW]:    你没有权限打开战斗模式(需要OP权限)</pre>
 * <ul>
 *   <li>前缀 {@code [CBW]:} —— <b>黄色 + 加粗</b>;</li>
 *   <li>前缀与正文之间 —— **4 个空格**;</li>
 *   <li>正文 —— <b>标准白色、不加粗</b>。</li>
 * </ul>
 *
 * <p>⚠️ 实现要点:<b>子组件的样式会与父组件合并</b>, 未显式设置的属性会**继承**父级。
 * 所以正文不能只写 {@code withStyle(WHITE)} —— 那样颜色对了, 但 {@code bold} 仍会继承前缀的
 * {@code true}, 整句都变粗(用户实测反馈过这一点)。必须**显式** {@code withBold(false)}。</p>
 *
 * <p>用 {@link ServerPlayer#displayClientMessage(Component, boolean)} 的 {@code false} 分支走**聊天栏**,
 * 且它是**只发给该玩家**的 —— 同一服务器上的其它玩家看不到这条提示。</p>
 */
public final class ChatFeedback {

    /** 模组前缀(黄色加粗)。 */
    private static final String PREFIX = "[CBW]:";

    /** 前缀与正文之间的间隔:4 个空格。 */
    private static final String GAP = "    ";

    private ChatFeedback() {
    }

    /** 给该玩家发一条聊天栏提示(仅他可见)。 */
    public static void warn(ServerPlayer player, Component message) {
        player.displayClientMessage(decorate(message), false);
    }

    /**
     * 组装成 {@code [CBW]:    <正文>}:
     * 前缀黄色加粗;间隔与正文为白色且**显式不加粗**。
     */
    public static Component decorate(Component message) {
        MutableComponent prefix = Component.literal(PREFIX)
            .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withBold(true));
        MutableComponent gap = Component.literal(GAP)
            .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE).withBold(false));
        MutableComponent body = message.copy()
            .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE).withBold(false));
        return prefix.append(gap).append(body);
    }
}
