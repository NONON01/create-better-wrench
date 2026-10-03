package com.nonono.createbetterwrench.network;

import java.util.function.Supplier;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.util.ChatFeedback;
import com.nonono.createbetterwrench.combat.WrenchCombat;
import com.nonono.createbetterwrench.permission.WrenchPermissions;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端到服务端: 请求切换<b>战斗(彩蛋)模式</b>, 并附带是否显示提示。
 *
 * <p>服务端不信任请求: 权限判定由 {@link WrenchPermissions#canUseCombatMode} 给出
 * (含配置总开关 + 单独授权 + 权限等级三重), 结果通过 {@link CombatModeSyncPayload} 回传权威状态。</p>
 */
public record CombatModePayload(boolean combat, boolean announce) {

    public static void encode(CombatModePayload p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.combat());
        buf.writeBoolean(p.announce());
    }

    public static CombatModePayload decode(FriendlyByteBuf buf) {
        return new CombatModePayload(buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(CombatModePayload p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null)
                return;
            boolean requested = p.combat();
            // canUseCombatMode 里已经含"配置里的战斗模式总开关 + 单独授权 + 权限等级"三重判定
            boolean allowed = requested && WrenchPermissions.canUseCombatMode(sp);
            WrenchCombat.setServer(sp.getUUID(), allowed);
            WrenchCombat.apply(sp, WrenchCombat.holdsWrench(sp) && allowed);
            // 回传权威状态 + 是否提示(客户端据此改本地开关并显示提示)
            WrenchNetwork.sendToPlayer(sp, new CombatModeSyncPayload(allowed, p.announce()));
            if (requested && !allowed)
                // 设计约定: 走聊天栏(不是 actionbar), 前缀 [CBW]: 黄色加粗、正文白色, 且仅该玩家可见
                ChatFeedback.warn(sp, Component.translatable(
                    "msg." + BetterWrenchMod.MODID
                        + (com.nonono.createbetterwrench.config.WrenchConfig.combatEnabled()
                            ? ".combat_no_permission"
                            : ".feature_disabled")));
        });
        ctx.get().setPacketHandled(true);
    }
}
