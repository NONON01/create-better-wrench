package com.nonono.createbetterwrench.network;

import java.util.function.Supplier;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.assemble.DepotStayState;
import com.nonono.createbetterwrench.mode.AssembleStay;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端到服务端: 同步「加工」模式的<b>成品停留时间</b>(Ctrl+滚轮选的档位)。
 *
 * <p>载荷: {@code ticks} = 客户端选中的停留服务端 tick 数。服务端<b>夹住范围</b>后存进
 * {@link DepotStayState}; 客户端传来的值一律不信任, 越界值由 {@link AssembleStay#clampTicks}
 * 夹回合法区间, 不报错也不断开连接。</p>
 */
public record AssembleStayPayload(int ticks) {

    public static void encode(AssembleStayPayload p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.ticks());
    }

    public static AssembleStayPayload decode(FriendlyByteBuf buf) {
        return new AssembleStayPayload(buf.readVarInt());
    }

    public static void handle(AssembleStayPayload p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null)
                return;
            // 服务端校验: 不信任客户端传来的值, 只接受 0..MAX_TICKS 的整数
            DepotStayState.set(sp.getUUID(), AssembleStay.clampTicks(p.ticks()));
        });
        ctx.get().setPacketHandled(true);
    }
}
