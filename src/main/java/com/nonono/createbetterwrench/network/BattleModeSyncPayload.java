package com.nonono.createbetterwrench.network;

import com.nonono.createbetterwrench.BetterWrenchMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 → 客户端: 把「战斗模式」的**权威状态**回传给客户端(用于因权限被拒后把本地开关回正)。
 */
public record BattleModeSyncPayload(boolean combat) implements CustomPacketPayload {

    public static final Type<BattleModeSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "battle_mode_sync"));

    public static final StreamCodec<ByteBuf, BattleModeSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, BattleModeSyncPayload::combat,
        BattleModeSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        // 仅在客户端执行: 用服务端权威状态覆盖本地开关
        ctx.enqueueWork(() -> com.nonono.createbetterwrench.client.WrenchCombatClient.onBattleModeSync(combat));
    }
}
