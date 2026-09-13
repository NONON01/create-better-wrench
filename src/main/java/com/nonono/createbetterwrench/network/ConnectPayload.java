package com.nonono.createbetterwrench.network;

import java.util.ArrayList;
import java.util.List;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.connect.ConnectLogic;
import com.nonono.createbetterwrench.mode.ConnectCorner;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端: 请求在 S→(多个拐点)→E 之间铺设传动结构。
 *
 * <p>载荷: start(起点) + corners(按序的拐点列表, 可为空=直线直达) + end(终点) + cornerType(拐角=齿轮箱/大齿轮)。
 * 服务端用 {@link ConnectLogic#connect} 按"每段边同轴=直线 / 同平面=自动一次拐弯 / 非平面=拒连"
 * 路由, 校验轴线/占位/材料后落块扣料。</p>
 */
public record ConnectPayload(BlockPos start, List<BlockPos> corners, BlockPos end, String cornerTypeName)
    implements CustomPacketPayload {

    public static final Type<ConnectPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "connect"));

    private static final StreamCodec<ByteBuf, List<BlockPos>> CORNERS_CODEC = new StreamCodec<>() {
        @Override
        public List<BlockPos> decode(ByteBuf buffer) {
            int n = buffer.readInt();
            List<BlockPos> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++)
                list.add(BlockPos.STREAM_CODEC.decode(buffer));
            return list;
        }

        @Override
        public void encode(ByteBuf buffer, List<BlockPos> value) {
            buffer.writeInt(value.size());
            for (BlockPos p : value)
                BlockPos.STREAM_CODEC.encode(buffer, p);
        }
    };

    public static final StreamCodec<ByteBuf, ConnectPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, ConnectPayload::start,
        CORNERS_CODEC, ConnectPayload::corners,
        BlockPos.STREAM_CODEC, ConnectPayload::end,
        ByteBufCodecs.STRING_UTF8, ConnectPayload::cornerTypeName,
        ConnectPayload::new
    );

    public static ConnectPayload create(BlockPos start, List<BlockPos> corners, BlockPos end,
                                        ConnectCorner cornerType) {
        return new ConnectPayload(start, new ArrayList<>(corners), end, cornerType.name());
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

            ConnectCorner cornerType = ConnectCorner.byName(cornerTypeName);

            ConnectLogic.Result result = ConnectLogic.connect(
                (net.minecraft.server.level.ServerLevel) sp.level(), sp, start, corners, end, cornerType);

            // 结果提示文案在语言文件: msg.<modid>.connect.<result 小写>。见 lang/*.json。
            String key = "msg." + BetterWrenchMod.MODID + ".connect."
                + result.name().toLowerCase(java.util.Locale.ROOT);
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(key), true);
        });
    }
}
