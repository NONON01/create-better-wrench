package com.nonono.createbetterwrench.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * 面向玩家的**聊天栏**反馈(不是 actionbar)。
 *
 * <p>统一格式:前缀 {@code [CBW]:} 用 <b>黄色加粗</b>, 正文用 <b>普通白色</b> —— 例如</p>
 * <pre>[CBW]:你没有权限打开战斗模式(需要OP权限)</pre>
 *
 * <p>用 {@link ServerPlayer#displayClientMessage(Component, boolean)} 的 {@code false} 分支走**聊天栏**,
 * 且它是**只发给该玩家**的 —— 同一服务器上的其它玩家看不到这条提示。</p>
 */
public final class ChatFeedback {

    /** 模组前缀(黄色加粗)。 */
    private static final String PREFIX = "[CBW]:";

    private ChatFeedback() {
    }

    /** 给该玩家发一条聊天栏提示(仅他可见)。 */
    public static void warn(ServerPlayer player, Component message) {
        player.displayClientMessage(decorate(message), false);
    }

    /**
     * 把正文包装成 {@code [CBW]:<正文>}:
     * 前缀黄色加粗, 正文强制为普通白色(避免继承了前缀的加粗/黄色)。
     */
    public static Component decorate(Component message) {
        MutableComponent prefix = Component.literal(PREFIX)
            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
        return prefix.append(message.copy().withStyle(ChatFormatting.WHITE));
    }
}
