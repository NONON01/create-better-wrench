package com.nonono.createbetterwrench.network;

import java.util.ArrayList;
import java.util.List;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.chain.ChainConnectServer;
import com.nonono.createbetterwrench.mode.ChainSubMode;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端到服务端: 请求按一条已规划好的锁链传动路径执行<b>放置与连接</b>。
 *
 * <p>载荷: {@code sub}(当前子模式) + {@code path}(按起点到终点排序的锁链传动轮坐标, 含两端) +
 * {@code forced}(是否为 Shift+右键的强制放置)。服务端在 {@link ChainConnectServer#handle} 里
 * <b>完整重做</b>全部校验(几何约束、连接数上限、材料), 客户端传值一律不作为依据。</p>
 *
 * <p>解码安全: 路径长度在解码阶段只过一条<b>固定硬闸</b> {@link #PROTOCOL_MAX_PATH}
 * (与配置 {@code chain.search_max_conveyors} 的取值范围上界 1024 相同), 绝不拿线上读到的 int
 * 直接当 {@code ArrayList} 容量; 按服务端配置做的业务上限留到 {@code handle} 里友好拒绝,
 * 避免"配置漂移"把合法玩家踢下线(与连接载荷复审 A-18 同一处理)。</p>
 */
public record ChainConnectPayload(ChainSubMode sub, List<BlockPos> path, boolean forced)
    implements CustomPacketPayload {

    public static final Type<ChainConnectPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "chain_connect"));

    /**
     * 协议层的<b>固定</b>路径长度上限 —— 与配置 {@code chain.search_max_conveyors} 的取值范围
     * 上界(1..1024)<b>同一上界</b>。
     *
     * <p>这里<b>不能</b>读 {@code WrenchConfig.chainSearchMaxConveyors()}: 专用服务器上客户端读不到
     * SERVER 配置、会回落到默认 256, 而服务端可能被管理员调到 1024; 若用配置值当硬闸, 合法客户端
     * 送更长的路径时解码层 {@code throw} 会引发 Netty 解码异常, 最终把玩家踢下线。因此解码阶段
     * 只做防 OOM 的固定硬闸; 真正的配置上限在 {@link ChainConnectServer#handle} 里友好拒绝。</p>
     */
    public static final int PROTOCOL_MAX_PATH = 1024;

    /** 子模式按稳定 id 编解码; 未知 id 由 {@link ChainSubMode#byId} 回退到标准模式。 */
    private static final StreamCodec<ByteBuf, ChainSubMode> SUB_CODEC =
        ByteBufCodecs.STRING_UTF8.map(ChainSubMode::byId, ChainSubMode::id);

    private static final StreamCodec<ByteBuf, List<BlockPos>> PATH_CODEC = new StreamCodec<>() {
        @Override
        public List<BlockPos> decode(ByteBuf buffer) {
            int n = buffer.readInt();
            // 绝不拿线上读到的 int 直接当 ArrayList 容量: 先做固定硬闸, 再按小容量起步扩容。
            if (n < 0 || n > PROTOCOL_MAX_PATH)
                throw new io.netty.handler.codec.DecoderException("chain path length out of range: " + n);
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

    public static final StreamCodec<ByteBuf, ChainConnectPayload> STREAM_CODEC = StreamCodec.composite(
        SUB_CODEC, ChainConnectPayload::sub,
        PATH_CODEC, ChainConnectPayload::path,
        ByteBufCodecs.BOOL, ChainConnectPayload::forced,
        ChainConnectPayload::new
    );

    /** 规范化: 空子模式回退标准, 路径做防御性不可变拷贝。 */
    public ChainConnectPayload {
        sub = sub == null ? ChainSubMode.STANDARD : sub;
        path = path == null ? List.of() : List.copyOf(path);
    }

    /** 客户端构造入口(拷贝一份可变列表, 避免与调用方后续改动共享)。 */
    public static ChainConnectPayload create(ChainSubMode sub, List<BlockPos> path, boolean forced) {
        return new ChainConnectPayload(sub, new ArrayList<>(path), forced);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp))
                return;
            ChainConnectServer.handle(sp, sub, path, forced);
        });
    }
}
