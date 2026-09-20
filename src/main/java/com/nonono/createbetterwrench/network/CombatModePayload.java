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
 * 客户端 → 服务端: 同步「战斗模式(彩蛋)」开关; 服务端做**权限检查**(cbw.combatmode, 默认拥有)后,
 * 记录并在持扳手时据此应用攻击加成; 无论允许与否都回传权威状态给客户端。
 *
 * <p>{@code announce} = 这次是不是**玩家主动的一次切换**(而不是进世界时的自动同步);
 * 服务端把它原样带回客户端, 客户端据此决定要不要显示 actionbar 提示 ——
 * 这样"提示"只出现在权威结果确定之后, 不会先乐观显示再被推翻。</p>
 */
public record CombatModePayload(boolean combat, boolean announce) implements CustomPacketPayload {

    public static final Type<CombatModePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "combat_mode"));

    public static final StreamCodec<ByteBuf, CombatModePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, CombatModePayload::combat,
        ByteBufCodecs.BOOL, CombatModePayload::announce,
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
            boolean allowed = requested && WrenchPermissions.canUseCombatMode(sp);
            WrenchCombat.setServer(sp.getUUID(), allowed);
            WrenchCombat.apply(sp, WrenchCombat.holdsWrench(sp) && allowed);
            // 回传权威状态 + 是否提示(客户端据此改本地开关并显示提示)
            PacketDistributor.sendToPlayer(sp, new CombatModeSyncPayload(allowed, announce));
            if (requested && !allowed)
                // 用户要求: 走**聊天栏**(不是 actionbar), 前缀 [CBW]: 黄色加粗、正文白色, 且**仅该玩家可见**
                ChatFeedback.warn(sp,
                    Component.translatable("msg." + BetterWrenchMod.MODID + ".combat_no_permission"));
        });
    }
}
