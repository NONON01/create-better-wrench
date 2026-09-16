package com.nonono.createbetterwrench.network;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.crank.CrankLogic;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端: 「曲柄」模式下按住右键期间, 周期性地告诉服务端"我正在对着这个方块的这一面摇",
 * 并带上当前转速与方向。服务端据此维持/刷新"临时动力源", 超时(松手)后自动还原。
 *
 * @param pos       准星所指的方块
 * @param face      准星所打到的那个面
 * @param rpm       当前设定转速(1-256)
 * @param backwards 是否反向(按 Shift)
 */
public record CrankPayload(BlockPos pos, Direction face, int rpm, boolean backwards)
    implements CustomPacketPayload {

    public static final Type<CrankPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "crank"));

    public static final StreamCodec<ByteBuf, CrankPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, CrankPayload::pos,
        Direction.STREAM_CODEC, CrankPayload::face,
        net.minecraft.network.codec.ByteBufCodecs.VAR_INT, CrankPayload::rpm,
        net.minecraft.network.codec.ByteBufCodecs.BOOL, CrankPayload::backwards,
        CrankPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp))
                return;
            CrankLogic.crank(sp, pos, face, rpm, backwards);
        });
    }
}
