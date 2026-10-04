package com.nonono.createbetterwrench.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 「锁链传动」模式的网络载荷登记入口。
 *
 * <p>本类只负责<b>新增</b>的锁链传动请求载荷 {@link ChainConnectPayload} 的登记;
 * 既有的拆除/连接/战斗/装配等载荷仍在 {@code BetterWrenchMod#registerPayloads} 里登记,
 * 本阶段不搬迁, 避免无关改动。接入方式(主机类里各加一行):</p>
 *
 * <pre>
 *   private static void registerPayloads(RegisterPayloadHandlersEvent event) {
 *       PayloadRegistrar registrar = event.registrar(MODID).versioned("1");
 *       ... 既有 playToServer / playToClient ...
 *       WrenchNetwork.register(registrar);   // 锁链传动
 *   }
 * </pre>
 *
 * <p>幂等: 重复调用只会登记一次(便于主机类与测试代码同时接线而不触发"载荷已注册"异常)。</p>
 */
public final class WrenchNetwork {

    /** 是否已经登记过锁链传动载荷。 */
    private static boolean registered;

    private WrenchNetwork() {
    }

    /** 登记「锁链传动」的客户端到服务端请求。 */
    public static void register(PayloadRegistrar registrar) {
        if (registered)
            return;
        registered = true;
        registrar.playToServer(
            ChainConnectPayload.TYPE,
            ChainConnectPayload.STREAM_CODEC,
            ChainConnectPayload::handle);
    }
}
