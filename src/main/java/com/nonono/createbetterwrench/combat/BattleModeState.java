package com.nonono.createbetterwrench.combat;

/**
 * 「战斗模式」权威状态的**跨端中转站**。
 *
 * <p>服务端回传的权威开关(见 {@code network/BattleModeSyncPayload})先写在这里,
 * 再由**仅客户端**的 {@code client/WrenchCombatClient} 在每个客户端 tick 取走、落到本地开关上。</p>
 *
 * <h2>为什么绕这一道(审计发现 #6)</h2>
 * <p>通用包 {@code network/} 位于**两侧都会加载**的公共代码里。如果载荷的 {@code handle} 里直接写
 * {@code WrenchCombatClient.onBattleModeSync(...)}, 那么专用服务器上只要执行到那条字节码指令,
 * 就会因为去加载 {@code @OnlyIn(Dist.CLIENT)} 的类而抛
 * {@code NoClassDefFoundError: net/minecraft/client/Minecraft} ——
 * 它是 {@link Error} 而不是 {@link Exception}, catch 不住, 轻则踢人重则崩服。</p>
 *
 * <p>本类刻意**只碰 {@code Boolean} 这种 JDK 类型**, 没有一行 import 涉及客户端/服务端专属类,
 * 因此无论哪一侧加载或执行它都不会碰到客户端类。这也是审计给出的两种修法之一
 * (「让客户端提供一个不碰任何 client 类、只写 boolean 的静态方法给 payload 调」)。</p>
 */
public final class BattleModeState {

    /**
     * 待客户端取走的权威开关; {@code null} = 没有待处理值。
     *
     * <p>volatile: 写入发生在网络线程的 {@code enqueueWork} 之后(主线程),
     * 读取在客户端主线程, 用一次引用写入表达"有没有值", 天然原子, 不需要额外锁。</p>
     */
    private static volatile Boolean pending = null;

    private BattleModeState() {
    }

    /** 记录服务端回传的权威状态(通用侧调用, 不碰任何客户端类)。 */
    public static void push(boolean combat) {
        pending = combat;
    }

    /** 客户端取走待处理值; 没有待处理值则返回 {@code null}。 */
    public static Boolean poll() {
        Boolean value = pending;
        pending = null;
        return value;
    }

    /** 断开连接时清空, 避免跨世界残留。 */
    public static void clear() {
        pending = null;
    }
}
