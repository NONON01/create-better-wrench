package com.nonono.createbetterwrench.config;

/**
 * 「功能开关」的**跨端快照**(服务的 → 客户端的单向同步)。
 *
 * <h2>为什么需要它</h2>
 * <p>本模组的配置是 NeoForge 的 <b>SERVER</b> 类型: 数值由**服务端权威**读取。单人游戏里客户端与内置服务端同进程,
 * 客户端能直接读到同一份配置; 但**专用服务器**上客户端读不到({@code ModConfigSpec#isLoaded()} 为 false),
 * 于是"某个功能被服主关掉了"这件事客户端一无所知 —— 模式还能切、HUD 也不会提示。</p>
 *
 * <p>所以专用服务器在玩家登录时(以及每次配置重载后)把这份快照发过来, 客户端据此:
 * ① 切到被关闭的模式时提示「此功能未启用」; ② 提前拦住不该发的请求(服务端仍会再校验一次)。</p>
 *
 * <h2>⚠️ 只碰 JDK 类型</h2>
 * <p>本类会被**两侧**加载(网络载荷的 handle 也要读它), 所以刻意不引用任何客户端/服务端专属类,
 * 只存 boolean/int —— 与 {@code combat/CombatModeState} 同一套路(见 docs/reference/03-known-issues.md B-1)。</p>
 */
public final class FeatureToggles {

    /**
     * 一份完整的功能开关快照。
     *
     * @param combatGranted 是**本玩家**是否被 {@code /cbw combat} 单独授权(不是全局开关)
     */
    public record Snapshot(
        boolean connectEnabled,
        int connectMaxCorners,
        int connectMaxLegLength,
        int connectMaxTotalBlocks,
        boolean deconstructEnabled,
        boolean deconstructAllowCreate,
        boolean deconstructAllowRedstone,
        int deconstructMaxEdge,
        int deconstructBlocksPerTick,
        boolean processEnabled,
        boolean processAssembly,
        boolean processFilling,
        boolean processSplash,
        boolean processBlasting,
        boolean processSmoking,
        boolean processHaunting,
        boolean combatEnabled,
        int combatPermissionLevel,
        boolean combatGranted
    ) {

        /** 出厂默认(全部启用) —— 没收到快照时用它, 保证"未同步"不会误伤正常玩法。 */
        public static Snapshot defaults() {
            return new Snapshot(
                true,
                WrenchConfig.DEFAULT_CONNECT_MAX_CORNERS,
                WrenchConfig.DEFAULT_CONNECT_MAX_LEG_LENGTH,
                WrenchConfig.DEFAULT_CONNECT_MAX_TOTAL_BLOCKS,
                true,
                true,
                true,
                WrenchConfig.DEFAULT_DECONSTRUCT_MAX_EDGE,
                WrenchConfig.DEFAULT_DECONSTRUCT_BLOCKS_PER_TICK,
                true,
                true, true, true, true, true, true,
                true,
                WrenchConfig.DEFAULT_COMBAT_PERMISSION_LEVEL,
                false);
        }
    }

    /** 服务端发来的快照; {@code null} = 还没收到(用本地配置或默认值)。 */
    private static volatile Snapshot remote;

    private FeatureToggles() {
    }

    /** 记录服务端发来的快照(网络侧调用, 不碰任何客户端类)。 */
    public static void set(Snapshot snapshot) {
        remote = snapshot;
    }

    /** 当前快照; {@code null} 表示没有(调用方应回落到本地配置/默认值)。 */
    public static Snapshot get() {
        return remote;
    }

    /** 断开连接时清空, 避免跨服务器残留上一台的开关。 */
    public static void clear() {
        remote = null;
    }
}
