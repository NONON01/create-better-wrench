package com.nonono.createbetterwrench.network;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.assemble.DepotStayState;
import com.nonono.createbetterwrench.mode.AssembleStay;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端到服务端: 同步「加工」模式的<b>成品停留时间</b>(Ctrl+滚轮选的档位)。
 *
 * <p>载荷: {@code ticks} = 客户端选中的停留服务端 tick 数。服务端<b>夹住范围</b>后存进
 * {@link DepotStayState}, 供 {@code DepotProductEjector} 排弹出队列时取用;
 * 客户端传来的值一律不信任, 越界值由 {@link AssembleStay#clampTicks} 夹回合法区间, 不报错也不断开。</p>
 *
 * <p>与其他几个包一样, 本类位于通用包 {@code network/} —— <b>绝不能引用任何 {@code client/} 下的类</b>
 * (专用服务器会 {@code NoClassDefFoundError});这里只碰 {@code int}, 天然满足。</p>
 */
public record AssembleStayPayload(int ticks) implements CustomPacketPayload {

    public static final Type<AssembleStayPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "assemble_stay"));

    public static final StreamCodec<ByteBuf, AssembleStayPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, AssembleStayPayload::ticks,
        AssembleStayPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp))
                return;
            // 服务端校验: 不信任客户端传来的值, 只接受 0..MAX_TICKS 的整数
            DepotStayState.set(sp.getUUID(), AssembleStay.clampTicks(ticks));
        });
    }
}
