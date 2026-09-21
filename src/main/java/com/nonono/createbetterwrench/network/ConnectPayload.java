package com.nonono.createbetterwrench.network;

import java.util.ArrayList;
import java.util.List;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.connect.ConnectLogic;
import com.nonono.createbetterwrench.mode.ConnectCorner;
import com.nonono.createbetterwrench.permission.WrenchPermissions;

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

    /** 拐点数量硬上限(服务端安全: 挡住"发超大 n ⇒ ArrayList 预分配 OOM")。 */
    public static final int MAX_CORNERS = 32;

    private static final StreamCodec<ByteBuf, List<BlockPos>> CORNERS_CODEC = new StreamCodec<>() {
        @Override
        public List<BlockPos> decode(ByteBuf buffer) {
            int n = buffer.readInt();
            // ⚠️ 审计发现 #2: 绝不拿线上读到的 int 直接当 ArrayList 容量。
            //    n = Integer.MAX_VALUE 会让 new ArrayList<>(n) 直接 OOM, 而 OOM 属于 Error, 服务端无法恢复。
            if (n < 0 || n > MAX_CORNERS)
                throw new io.netty.handler.codec.DecoderException("corner count out of range: " + n);
            List<BlockPos> list = new ArrayList<>(Math.min(n, 8));
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

            // ===== 服务端校验(绝不信任客户端)=====
            // ① 资格: 旁观者/无建造权限者一律拒绝;冒险模式下额外提示「当前是冒险模式」
            if (WrenchPermissions.rejectIfCannotBuild(sp))
                return;
            // ② 必须**主手**手持本模组的扳手
            // ⚠️ 2026-09-20(用户约定): 扳手在副手时"只作普通扳手", 不参与本模组的模式功能
            if (!sp.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH))
                return;
            // ③ 拐点数量上限(解码层已有硬上限, 这里再兜一次)
            if (corners.size() > MAX_CORNERS)
                return;
            // ④ 审计 A-3: 终点必须在玩家 8 格内(平方 64) —— 挡住改包客户端远程施工。
            //    刻意**不校验** start/拐点: 玩家是一路走过去逐个点拐点的, 起点很可能已在很远处。
            if (sp.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(end)) > 64.0) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "msg." + BetterWrenchMod.MODID + ".connect.too_far"), true);
                return;
            }
            // ⑤ 所有节点所在区块必须已加载(避免被用来强制生成/加载区块)
            if (!sp.level().hasChunkAt(start) || !sp.level().hasChunkAt(end))
                return;
            for (BlockPos c : corners)
                if (!sp.level().hasChunkAt(c))
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
