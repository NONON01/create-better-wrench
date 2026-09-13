package com.nonono.createbetterwrench.network;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.deconstruct.DeconstructLogic;
import com.nonono.createbetterwrench.mode.DeconstructScope;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端: 请求对某区域执行一次「拆除」。
 *
 * <p>载荷: 两角 BlockPos + 拆除范围档名(String, 解码后转 DeconstructScope)。
 * 服务端收到后遍历区域拆除并入背包。</p>
 */
public record DeconstructPayload(BlockPos cornerA, BlockPos cornerB, String scopeName)
    implements CustomPacketPayload {

    public static final Type<DeconstructPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "deconstruct"));

    public static final StreamCodec<ByteBuf, DeconstructPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, DeconstructPayload::cornerA,
        BlockPos.STREAM_CODEC, DeconstructPayload::cornerB,
        ByteBufCodecs.STRING_UTF8, DeconstructPayload::scopeName,
        DeconstructPayload::new
    );

    public static DeconstructPayload create(BlockPos a, BlockPos b, DeconstructScope scope) {
        return new DeconstructPayload(a, b, scope.name());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp))
                return;
            if (!sp.level().hasChunkAt(cornerA) || !sp.level().hasChunkAt(cornerB))
                return;
            DeconstructScope scope;
            try {
                scope = DeconstructScope.valueOf(scopeName);
            } catch (Exception e) {
                scope = DeconstructScope.ALL;
            }
            int removed = DeconstructLogic.deconstructRegion(
                (net.minecraft.server.level.ServerLevel) sp.level(), cornerA, cornerB, scope, sp);
            // 始终提示(含 0), 显示在 actionbar; 文案在语言文件 msg.<modid>.deconstruct.count
            sp.displayClientMessage(net.minecraft.network.chat.Component
                .translatable("msg." + BetterWrenchMod.MODID + ".deconstruct.count", removed), true);
        });
    }
}
