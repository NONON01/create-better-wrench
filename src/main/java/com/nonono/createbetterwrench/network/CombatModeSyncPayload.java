package com.nonono.createbetterwrench.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端到客户端: 回传<b>权威的战斗模式状态</b>与是否需要提示。
 *
 * <p>注意: 这里绝对不能直接引用 client/ 下的类(它们标了 {@code @OnlyIn(Dist.CLIENT)});
 * 本类在通用包 network/ 里, 专用服务器同样会加载它, 一旦执行到指向客户端类的指令就会抛
 * {@code NoClassDefFoundError}。因此只把状态丢给通用的 CombatModeState, 由客户端自己取用。</p>
 */
public record CombatModeSyncPayload(boolean combat, boolean announce) {

    public static void encode(CombatModeSyncPayload p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.combat());
        buf.writeBoolean(p.announce());
    }

    public static CombatModeSyncPayload decode(FriendlyByteBuf buf) {
        return new CombatModeSyncPayload(buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(CombatModeSyncPayload p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(
            () -> com.nonono.createbetterwrench.combat.CombatModeState.push(p.combat(), p.announce()));
        ctx.get().setPacketHandled(true);
    }
}
