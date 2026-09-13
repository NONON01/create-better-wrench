package com.nonono.createbetterwrench.network;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.assemble.AssembleLock;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端: 在「装配」模式下右击置物台, 请求**切换锁定状态**(锁定/解锁)。
 */
public record AssemblePayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<AssemblePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "assemble_toggle"));

    public static final StreamCodec<ByteBuf, AssemblePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, AssemblePayload::pos,
        AssemblePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp))
                return;
            if (!sp.level().hasChunkAt(pos))
                return;
            if (!(sp.level().getBlockEntity(pos) instanceof DepotBlockEntity depot))
                return;
            boolean now = !AssembleLock.isLocked(depot);
            AssembleLock.setLocked(depot, now);
            sp.displayClientMessage(Component.translatable("msg." + BetterWrenchMod.MODID
                + (now ? ".assemble.locked" : ".assemble.unlocked")), true);
        });
    }
}
