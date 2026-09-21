package com.nonono.createbetterwrench.network;

import com.nonono.createbetterwrench.BetterWrenchMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 → 客户端: 把「战斗模式」的**权威状态**回传给客户端。
 *
 * <p>{@code announce} 表示"这次回包是否该给玩家显示提示" —— 只有**玩家自己发起的一次切换**才为真;
 * 进世界时的自动同步为假, 免得一进游戏就冒出一条无意义的 actionbar。
 * (早期版本客户端在按下时就乐观显示, 被拒后那条消息已经挂在屏幕上 ⇒ 用户看到"文本卡死在战斗模式"。)</p>
 */
public record CombatModeSyncPayload(boolean combat, boolean announce) implements CustomPacketPayload {

    public static final Type<CombatModeSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "combat_mode_sync"));

    public static final StreamCodec<ByteBuf, CombatModeSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, CombatModeSyncPayload::combat,
        ByteBufCodecs.BOOL, CombatModeSyncPayload::announce,
        CombatModeSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        // ⚠️ 审计发现 #6: 这里**绝对不能**直接引用 client/ 下的类(它们标了 @OnlyIn(Dist.CLIENT))。
        //    本类在通用包 network/ 里, 专用服务器同样会加载它; 一旦执行到指向客户端类的指令,
        //    就会抛 NoClassDefFoundError(Error 不是 Exception, 无法恢复)。
        //    所以只把状态丢给通用的 CombatModeState, 由客户端自己的 WrenchCombatClient 取用 ——
        //    通用包因此做到"零客户端类引用"。
        ctx.enqueueWork(() -> com.nonono.createbetterwrench.combat.CombatModeState.push(combat, announce));
    }
}
