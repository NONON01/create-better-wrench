package com.nonono.createbetterwrench.network;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.config.FeatureToggles;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 → 客户端: 下发**功能开关快照**(总开关 / 各类上限 / 六个加工子开关 / 战斗开关与权限等级 / 本玩家的战斗授权)。
 *
 * <p>为什么需要: 配置是 SERVER 类型, 专用服务器上客户端读不到 ⇒ 不知道"某个功能已被服主关掉"。
 * 客户端收到后只做两件事: ① 切到被关闭的模式时提示「此功能未启用」; ② 提前拦住不该发的请求(服务端仍会再校验)。</p>
 *
 * <p>⚠️ 本类在**通用包**里, 专用服务器也会加载它 —— 所以 {@link #handle} 只把快照塞进纯 JDK 的
 * {@link FeatureToggles}, **不引用任何客户端类**(见 docs/reference/03-known-issues.md B-1)。</p>
 */
public record FeatureTogglePayload(FeatureToggles.Snapshot snapshot) implements CustomPacketPayload {

    public static final Type<FeatureTogglePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "feature_toggles"));

    public static final StreamCodec<ByteBuf, FeatureTogglePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FeatureTogglePayload decode(ByteBuf buffer) {
            return new FeatureTogglePayload(new FeatureToggles.Snapshot(
                buffer.readBoolean(),
                buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readInt(), buffer.readInt(),
                buffer.readBoolean(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readInt(), buffer.readBoolean()));
        }

        @Override
        public void encode(ByteBuf buffer, FeatureTogglePayload payload) {
            FeatureToggles.Snapshot s = payload.snapshot();
            buffer.writeBoolean(s.connectEnabled());
            buffer.writeInt(s.connectMaxCorners());
            buffer.writeInt(s.connectMaxLegLength());
            buffer.writeInt(s.connectMaxTotalBlocks());
            buffer.writeBoolean(s.deconstructEnabled());
            buffer.writeBoolean(s.deconstructAllowCreate());
            buffer.writeBoolean(s.deconstructAllowRedstone());
            buffer.writeInt(s.deconstructMaxEdge());
            buffer.writeInt(s.deconstructBlocksPerTick());
            buffer.writeBoolean(s.processEnabled());
            buffer.writeBoolean(s.processAssembly());
            buffer.writeBoolean(s.processFilling());
            buffer.writeBoolean(s.processSplash());
            buffer.writeBoolean(s.processBlasting());
            buffer.writeBoolean(s.processSmoking());
            buffer.writeBoolean(s.processHaunting());
            buffer.writeBoolean(s.combatEnabled());
            buffer.writeInt(s.combatPermissionLevel());
            buffer.writeBoolean(s.combatGranted());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> FeatureToggles.set(snapshot));
    }
}
