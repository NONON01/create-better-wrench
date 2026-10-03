package com.nonono.createbetterwrench.network;

import java.util.function.Supplier;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络层(Forge 1.20.1): 用 {@link SimpleChannel} 承载全部自定义载荷。
 *
 * <p><b>为什么整体重写</b>: 1.21.1 线用的是 NeoForge 的 {@code CustomPacketPayload} 与
 * {@code StreamCodec}, 这两个系统在 1.20.2 才出现, 1.20.1 没有。因此这里换成 Forge 的经典通道,
 * 载荷本身仍是 record, 只是编解码与处理签名改为 {@code FriendlyByteBuf} 与
 * {@code Supplier<NetworkEvent.Context>}。</p>
 *
 * <p><b>必须保留的语义</b>(调研第 7.2 节): 每个包的处理体里服务端校验的顺序与条数,
 * 以及解码阶段的硬上限, 一条都不放宽 —— 重写只换传输方式, 不改校验。</p>
 */
public final class WrenchNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(BetterWrenchMod.MODID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals);

    private static int nextId = 0;

    private WrenchNetwork() {
    }

    /** 在模组构造期调用一次: 逐个登记载荷的编解码与处理体。 */
    public static void init() {
        CHANNEL.registerMessage(nextId++, AssemblePayload.class,
            AssemblePayload::encode, AssemblePayload::decode, AssemblePayload::handle);
        CHANNEL.registerMessage(nextId++, AssembleStayPayload.class,
            AssembleStayPayload::encode, AssembleStayPayload::decode, AssembleStayPayload::handle);
        CHANNEL.registerMessage(nextId++, ConnectPayload.class,
            ConnectPayload::encode, ConnectPayload::decode, ConnectPayload::handle);
        CHANNEL.registerMessage(nextId++, DeconstructPayload.class,
            DeconstructPayload::encode, DeconstructPayload::decode, DeconstructPayload::handle);
        CHANNEL.registerMessage(nextId++, CombatModePayload.class,
            CombatModePayload::encode, CombatModePayload::decode, CombatModePayload::handle);
        CHANNEL.registerMessage(nextId++, CombatModeSyncPayload.class,
            CombatModeSyncPayload::encode, CombatModeSyncPayload::decode, CombatModeSyncPayload::handle);
        CHANNEL.registerMessage(nextId++, FeatureTogglePayload.class,
            FeatureTogglePayload::encode, FeatureTogglePayload::decode, FeatureTogglePayload::handle);
    }

    /** 客户端到服务端。 */
    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    /** 服务端到指定玩家(用于把权威状态回传)。 */
    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** 服务端到所有玩家(配置同步等广播场景)。 */
    public static void sendToAll(Object message) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), message);
    }

    /** 供参考: 该通道使用的网络方向常量(避免未使用导入告警)。 */
    static final NetworkDirection ANY_DIRECTION = null;
}
