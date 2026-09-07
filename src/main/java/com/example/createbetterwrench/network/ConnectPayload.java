package com.example.createbetterwrench.network;

import javax.annotation.Nullable;

import com.example.createbetterwrench.BetterWrenchMod;
import com.example.createbetterwrench.connect.ConnectLogic;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端: 请求在 A→(拐点)→B 之间铺设传动结构。
 *
 * <p>载荷: start(起点, 机械动力方块) + 是否带拐点 + corner(普通方块位置, 可无) + end(终点, 机械动力方块)。
 * 服务端用 {@link ConnectLogic#connect} 校验轴线/占位/材料后落块扣料。</p>
 */
public record ConnectPayload(BlockPos start, boolean hasCorner, BlockPos corner, BlockPos end)
    implements CustomPacketPayload {

    public static final Type<ConnectPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "connect"));

    public static final StreamCodec<ByteBuf, ConnectPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, ConnectPayload::start,
        ByteBufCodecs.BOOL, ConnectPayload::hasCorner,
        BlockPos.STREAM_CODEC, ConnectPayload::corner,
        BlockPos.STREAM_CODEC, ConnectPayload::end,
        ConnectPayload::new
    );

    public static ConnectPayload create(BlockPos start, @Nullable BlockPos corner, BlockPos end) {
        return new ConnectPayload(start, corner != null, corner == null ? BlockPos.ZERO : corner, end);
    }

    @Nullable
    public BlockPos cornerOrNull() {
        return hasCorner ? corner : null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp))
                return;
            if (!sp.level().hasChunkAt(start) || !sp.level().hasChunkAt(end))
                return;

            ConnectLogic.Result result =
                ConnectLogic.connect((net.minecraft.server.level.ServerLevel) sp.level(), sp, start, cornerOrNull(), end);

            String msg = switch (result) {
                case SUCCESS -> "连接成功:已铺设传动结构";
                case UNLOADED -> "连接失败:区块未加载";
                case NOT_KINETIC -> "连接失败:两端方块无法接入本方向的传动轴";
                case AXIS_MISMATCH -> "连接失败:起点/拐点/终点未严格轴线对齐";
                case BAD_TURN -> "连接失败:拐弯必须是 90° 直角";
                case PATH_BLOCKED -> "连接失败:路径被方块阻挡";
                case CONFLICTING_SOURCE -> "连接失败:两端动力方向冲突,不能连入";
                case MATERIALS -> "连接失败:背包材料不足";
                case TOO_LONG -> "连接失败:路径过长";
            };
            sp.displayClientMessage(net.minecraft.network.chat.Component.literal(msg), true);
        });
    }
}
