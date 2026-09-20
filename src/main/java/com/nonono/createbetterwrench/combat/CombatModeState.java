package com.nonono.createbetterwrench.combat;

/**
 * 「战斗模式」权威状态的**跨端中转站**。
 *
 * <p>服务端回传的权威开关(见 {@code network/CombatModeSyncPayload})先写在这里,
 * 再由**仅客户端**的 {@code client/WrenchCombatClient} 在每个客户端 tick 取走、落到本地开关上,
 * 并在 {@code announce=true} 时**此时才**显示提示(actionbar)。</p>
 *
 * <h2>为什么绕这一道(审计发现 #6)</h2>
 * <p>通用包 {@code network/} 位于**两侧都会加载**的公共代码里。如果载荷的 {@code handle} 里直接写
 * {@code WrenchCombatClient.onCombatModeSync(...)}, 那么专用服务器上只要执行到那条字节码指令,
 * 就会因为去加载 {@code @OnlyIn(Dist.CLIENT)} 的类而抛
 * {@code NoClassDefFoundError: net/minecraft/client/Minecraft} ——
 * 它是 {@link Error} 而不是 {@link Exception}, catch 不住, 轻则踢人重则崩服。</p>
 *
 * <p>本类刻意**只碰 boolean 这种 JDK 类型**, 没有一行 import 涉及客户端/服务端专属类,
 * 因此无论哪一侧加载或执行它都不会碰到客户端类。</p>
 *
 * <h2>为什么要带 {@code announce}(2026-09-20 修 bug)</h2>
 * <p>战斗模式是**需要服务端授权**的开关。早期实现在客户端按下时就**乐观地**翻转本地开关并立刻显示
 * actionbar「战斗模式」;无权限时服务端随后把本地开关改回 false, 但**那条 actionbar 消息已经发出**,
 * 会在屏幕上停留 2~3 秒 ⇒ 用户看到"文本卡死在战斗模式"。</p>
 * <p>现在:客户端只把"想要的值"发给服务端(**不改本地、不显示**), 待权威回包到达后才改本地状态,
 * 并在 {@code announce} 为真(**确实是一次用户操作**)时才显示提示。 登录时的同步带 {@code announce=false},
 * 因此不会在进世界时冒出一条无意义的提示。</p>
 */
public final class CombatModeState {

    /** 一次权威回包:{@code combat} = 权威开关, {@code announce} = 是否该给玩家显示提示。 */
    public record Sync(boolean combat, boolean announce) {
    }

    /**
     * 待客户端取走的权威回包;{@code null} = 没有待处理值。
     *
     * <p>volatile:写入发生在网络线程的 {@code enqueueWork} 之后(主线程), 读取在客户端主线程,
     * 用一次引用写入表达"有没有值", 天然原子, 不需要额外锁。</p>
     */
    private static volatile Sync pending = null;

    private CombatModeState() {
    }

    /** 记录服务端回传的权威状态(通用侧调用, 不碰任何客户端类)。 */
    public static void push(boolean combat, boolean announce) {
        pending = new Sync(combat, announce);
    }

    /** 客户端取走待处理回包;没有则返回 {@code null}。 */
    public static Sync poll() {
        Sync value = pending;
        pending = null;
        return value;
    }

    /** 断开连接时清空, 避免跨世界残留。 */
    public static void clear() {
        pending = null;
    }
}
