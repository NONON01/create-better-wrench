package com.nonono.createbetterwrench.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.nonono.createbetterwrench.chain.ChainConnectServer;
import com.nonono.createbetterwrench.mode.ChainSubMode;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端到服务端: 请求按一条已规划好的锁链传动路径执行<b>放置与连接</b>(Forge 1.20.1 版)。
 *
 * <p>字段与 1.21.1 线完全一致: {@code sub}(当前子模式) + {@code path}(按起点到终点排序的
 * 锁链传动轮坐标, 含两端) + {@code forced}(是否为 Shift 强制放置)。平台差异只有传输方式:
 * 1.21.1 用 {@code CustomPacketPayload} / {@code StreamCodec}, 这里用 Forge 的
 * {@code SimpleChannel} / {@code FriendlyByteBuf} —— 载荷仍是 record, 只是编解码改成静态
 * {@code encode/decode}, 处理体签名改成 {@code Supplier<NetworkEvent.Context>}。</p>
 *
 * <p>服务端在 {@link ChainConnectServer#handle} 里<b>完整重做</b>全部校验, 客户端传值不作为依据。</p>
 *
 * <p>解码安全: 路径长度在解码阶段只过一条<b>固定硬闸</b> {@link #PROTOCOL_MAX_PATH},
 * 与 1.21.1 线同值(1024), 也等于配置 {@code chain.search_max_conveyors} 的取值范围上界; 绝不拿线上
 * 读到的 int 直接当 {@code ArrayList} 容量。按服务端配置做的业务上限留到 {@code handle} 里友好拒绝,
 * 避免"配置漂移"把合法玩家踢下线(与连接载荷复审 A-18 同一处理)。</p>
 */
public record ChainConnectPayload(ChainSubMode sub, List<BlockPos> path, boolean forced) {

    /**
     * 协议层的<b>固定</b>路径长度上限 —— 与 1.21.1 线同口径, 与配置
     * {@code chain.search_max_conveyors} 的取值范围上界(1..1024)<b>同一上界</b>。
     *
     * <p>这里<b>不能</b>读 {@code WrenchConfig.chainSearchMaxConveyors()}: 专用服务器上客户端读不到
     * SERVER 配置、会回落到默认 256, 而服务端可能被管理员调到 1024; 若用配置值当硬闸, 合法客户端
     * 送更长的路径时解码层 {@code throw} 会引发 Netty 解码异常, 最终把玩家踢下线。</p>
     */
    public static final int PROTOCOL_MAX_PATH = 1024;

    /** 规范化: 空子模式回退标准, 路径做防御性不可变拷贝。 */
    public ChainConnectPayload {
        sub = sub == null ? ChainSubMode.STANDARD : sub;
        path = path == null ? List.of() : List.copyOf(path);
    }

    /** 客户端构造入口(拷贝一份可变列表, 避免与调用方后续改动共享)。 */
    public static ChainConnectPayload create(ChainSubMode sub, List<BlockPos> path, boolean forced) {
        return new ChainConnectPayload(sub, new ArrayList<>(path), forced);
    }

    public static void encode(ChainConnectPayload p, FriendlyByteBuf buf) {
        buf.writeUtf(p.sub().id());
        buf.writeInt(p.path().size());
        for (BlockPos pos : p.path())
            buf.writeBlockPos(pos);
        buf.writeBoolean(p.forced());
    }

    public static ChainConnectPayload decode(FriendlyByteBuf buf) {
        ChainSubMode sub = ChainSubMode.byId(buf.readUtf());
        int n = buf.readInt();
        // 绝不拿线上读到的 int 直接当 ArrayList 容量: 先做固定硬闸, 再按小容量起步扩容。
        if (n < 0 || n > PROTOCOL_MAX_PATH)
            throw new DecoderException("chain path length out of range: " + n);
        List<BlockPos> path = new ArrayList<>(Math.min(n, 8));
        for (int i = 0; i < n; i++)
            path.add(buf.readBlockPos());
        return new ChainConnectPayload(sub, path, buf.readBoolean());
    }

    public static void handle(ChainConnectPayload p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null)
                return;
            ChainConnectServer.handle(sp, p.sub(), p.path(), p.forced());
        });
        ctx.get().setPacketHandled(true);
    }
}
