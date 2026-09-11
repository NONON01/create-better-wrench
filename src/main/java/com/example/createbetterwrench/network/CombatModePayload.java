package com.example.createbetterwrench.network;

import com.example.createbetterwrench.BetterWrenchMod;
import com.example.createbetterwrench.combat.WrenchCombat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端: 同步「战斗模式(彩蛋)」开关; 服务端记录并在持扳手时据此应用攻击加成。
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
            WrenchCombat.setServer(sp.getUUID(), combat);
            // 立即应用一次(不必等下一 tick)
            WrenchCombat.apply(sp, WrenchCombat.holdsWrench(sp) && combat);
        });
    }
}
