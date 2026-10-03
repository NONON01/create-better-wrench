package com.nonono.createbetterwrench.network;

import java.util.function.Supplier;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.config.FeatureToggles;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端到客户端: 下发<b>功能开关快照</b>(总开关 / 各类上限 / 七个加工子开关 / 战斗开关与权限等级 / 本玩家的战斗授权)。
 *
 * <p>为什么需要: 配置是 SERVER 类型, 专用服务器上客户端读不到, 因此不知道"某个功能已被服主关掉"。
 * 客户端收到后只做两件事: ① 切到被关闭的模式时提示「此功能未启用」; ② 提前拦住不该发的请求(服务端仍会再校验)。</p>
 *
 * <p>载荷: {@link FeatureToggles.Snapshot} 的 20 个字段, 编解码按同一固定顺序逐字段读写 ——
 * 字段顺序即协议, 两端必须一致。客户端侧不校验内容, 直接整份存下。</p>
 *
 * <p>本类在<b>通用包</b>里, 专用服务器也会加载它 —— 所以 {@link #handle} 只把快照塞进纯 JDK 的
 * {@link FeatureToggles}, <b>不引用任何客户端类</b>(见 docs/design/known-issues.md B-1)。</p>
 */
public record FeatureTogglePayload(FeatureToggles.Snapshot snapshot) {

;

    public static void encode(FeatureTogglePayload p, FriendlyByteBuf buf) {
        FeatureToggles.Snapshot s = p.snapshot();
        buf.writeBoolean(s.connectEnabled());
        buf.writeInt(s.connectMaxCorners());
        buf.writeInt(s.connectMaxLegLength());
        buf.writeInt(s.connectMaxTotalBlocks());
        buf.writeBoolean(s.deconstructEnabled());
        buf.writeBoolean(s.deconstructAllowCreate());
        buf.writeBoolean(s.deconstructAllowRedstone());
        buf.writeInt(s.deconstructMaxEdge());
        buf.writeInt(s.deconstructBlocksPerTick());
        buf.writeBoolean(s.processEnabled());
        buf.writeBoolean(s.processAssembly());
        buf.writeBoolean(s.processFilling());
        buf.writeBoolean(s.processSplash());
        buf.writeBoolean(s.processBlasting());
        buf.writeBoolean(s.processSmoking());
        buf.writeBoolean(s.processHaunting());
        buf.writeBoolean(s.processForging());
        buf.writeBoolean(s.combatEnabled());
        buf.writeInt(s.combatPermissionLevel());
        buf.writeBoolean(s.combatGranted());
    }

    public static FeatureTogglePayload decode(FriendlyByteBuf buf) {
        return new FeatureTogglePayload(new FeatureToggles.Snapshot(
            buf.readBoolean(),
            buf.readInt(), buf.readInt(), buf.readInt(),
            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
            buf.readInt(), buf.readInt(),
            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(), buf.readInt(), buf.readBoolean()));
    }

    public static void handle(FeatureTogglePayload p, Supplier<NetworkEvent.Context> ctx) {

        ctx.get().enqueueWork(() -> FeatureToggles.set(p.snapshot));
        ctx.get().setPacketHandled(true);
    }
}
