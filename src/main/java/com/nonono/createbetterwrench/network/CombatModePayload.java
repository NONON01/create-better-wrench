package com.nonono.createbetterwrench.network;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.combat.WrenchCombat;
import com.nonono.createbetterwrench.permission.WrenchPermissions;
import com.nonono.createbetterwrench.util.ChatFeedback;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端: 同步「战斗模式(彩蛋)」开关; 服务端做**权限检查**(cbw.battlemode, 默认拥有)后,
 * 记录并在持扳手时据此应用攻击加成; 无论允许与否都回传权威状态给客户端。
 */
public record CombatModePayload(boolean combat) implements CustomPacketPayload {

    public static final Type<CombatModePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "combat_mode"));

    public static final StreamCodec<ByteBuf, CombatModePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, CombatModePayload::combat,
        CombatModePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp))
                return;
            boolean requested = combat;
            boolean allowed = requested && WrenchPermissions.canUseBattleMode(sp);
            WrenchCombat.setServer(sp.getUUID(), allowed);
            WrenchCombat.apply(sp, WrenchCombat.holdsWrench(sp) && allowed);
            // 回传权威状态(无权限被拒时, 客户端会把本地开关回正)
            PacketDistributor.sendToPlayer(sp, new BattleModeSyncPayload(allowed));
            if (requested && !allowed)
                // 用户要求: 走**聊天栏**(不是 actionbar), 前缀 [CBW]: 黄色加粗、正文白色, 且**仅该玩家可见**
                ChatFeedback.warn(sp,
                    Component.translatable("msg." + BetterWrenchMod.MODID + ".battle_no_permission"));
        });
    }
}
