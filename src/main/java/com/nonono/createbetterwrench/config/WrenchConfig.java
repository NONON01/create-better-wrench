package com.nonono.createbetterwrench.config;

import java.util.List;

import com.nonono.createbetterwrench.mode.DeconstructScope;
import com.nonono.createbetterwrench.mode.ProcessKind;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 「万能扳手」的**可调参数 + 功能开关**(NeoForge 配置)。
 *
 * <h2>配置文件在哪 / Where is the file</h2>
 * <ul>
 *   <li>单人游戏: <b>存档目录</b>下的 <code>serverconfig/create_better_wrench-server.toml</code>;</li>
 *   <li>专用服务器: 服务器根目录的 <code>serverconfig/create_better_wrench-server.toml</code>;</li>
 *   <li>游戏里改: 指令 {@code /cbw config} 打开本模组的自绘配置页面(或模组列表 → 配置)。</li>
 * </ul>
 * <p>类型是 <b>SERVER</b>: 数值由**服务端权威**读取与校验。单人游戏里客户端与内置服务端在同一进程,
 * 因此客户端预览读到的是同一份值; **专用服务器**上客户端读不到 ⇒ 走服务端登录时下发的
 * {@link FeatureToggles} 快照(没有快照时一律按**默认值 = 全开**处理, 真正的限制仍由服务端执行)。</p>
 *
 * <h2>四个分组</h2>
 * <pre>
 *   [connect]     总开关 + 3 个数值(拐点上限 / 单段长度 / 单次方块数)
 *   [deconstruct] 总开关 + 允许机械动力方块 + 允许红石方块 + 2 个数值
 *   [process]     总开关 + 六个加工子功能开关
 *   [combat]      是否启用战斗模式 + 需要的权限等级(0=普通 / 2=OP)
 * </pre>
 *
 * <h2>联动规则(用户 2026-09-25 定, 见 {@link #normalize()} / {@link #applyToggle})</h2>
 * <ul>
 *   <li>拆除: 两个"允许"都关 ⇒ **总开关跟着关**; 总开关打开 ⇒ 两个"允许"都回到开;</li>
 *   <li>加工: 六个子功能都关 ⇒ **总开关跟着关**; 总开关打开 ⇒ 六个子功能都回到开;</li>
 *   <li>任何一个子功能被**打开**时, 它所属的**总开关也一并打开**(免得"子开着、总关着"这种看不出效果的状态)。</li>
 * </ul>
 *
 * <p>⚠️ 本类**不引用任何客户端类**, 两端都能安全加载。</p>
 */
public final class WrenchConfig {

    // ---------------------------------------------------------------- 默认值
    // 代码里的"兜底默认值": 配置尚未加载(例如专用服务器上的客户端)时使用, 必须与下面 define 的默认值一致。

    /** 连接: 功能总开关。 */
    public static final boolean DEFAULT_CONNECT_ENABLED = true;
    /** 连接: 拐点数量上限。 */
    public static final int DEFAULT_CONNECT_MAX_CORNERS = 32;
    /** 连接: 每段边的直线长度上限(格)。 */
    public static final int DEFAULT_CONNECT_MAX_LEG_LENGTH = 64;
    /** 连接: 单次请求可铺设的方块总数上限。 */
    public static final int DEFAULT_CONNECT_MAX_TOTAL_BLOCKS = 256;

    /** 拆除: 功能总开关。 */
    public static final boolean DEFAULT_DECONSTRUCT_ENABLED = true;
    /** 拆除: 是否允许破坏机械动力方块。 */
    public static final boolean DEFAULT_DECONSTRUCT_ALLOW_CREATE = true;
    /** 拆除: 是否允许破坏红石方块。 */
    public static final boolean DEFAULT_DECONSTRUCT_ALLOW_REDSTONE = true;
    /** 拆除: 选区单轴上限(格)。 */
    public static final int DEFAULT_DECONSTRUCT_MAX_EDGE = 64;
    /** 拆除: 每个服务端刻最多处理多少格。 */
    public static final int DEFAULT_DECONSTRUCT_BLOCKS_PER_TICK = 1024;

    /** 加工: 功能总开关。 */
    public static final boolean DEFAULT_PROCESS_ENABLED = true;
    /** 加工: 六个子功能的默认值(都开)。 */
    public static final boolean DEFAULT_PROCESS_SUB = true;

    /** 战斗: 是否启用战斗模式(整个彩蛋的总开关)。 */
    public static final boolean DEFAULT_COMBAT_ENABLED = true;
    /** 战斗: 需要的权限等级(0 = 普通玩家, 2 = OP)。 */
    public static final int DEFAULT_COMBAT_PERMISSION_LEVEL = 2;

    public static final ModConfigSpec SPEC;

    // ---- 连接
    private static final ModConfigSpec.BooleanValue CONNECT_ENABLED;
    private static final ModConfigSpec.IntValue CONNECT_MAX_CORNERS;
    private static final ModConfigSpec.IntValue CONNECT_MAX_LEG_LENGTH;
    private static final ModConfigSpec.IntValue CONNECT_MAX_TOTAL_BLOCKS;
    // ---- 拆除
    private static final ModConfigSpec.BooleanValue DECONSTRUCT_ENABLED;
    private static final ModConfigSpec.BooleanValue DECONSTRUCT_ALLOW_CREATE;
    private static final ModConfigSpec.BooleanValue DECONSTRUCT_ALLOW_REDSTONE;
    private static final ModConfigSpec.IntValue DECONSTRUCT_MAX_EDGE;
    private static final ModConfigSpec.IntValue DECONSTRUCT_BLOCKS_PER_TICK;
    // ---- 加工
    private static final ModConfigSpec.BooleanValue PROCESS_ENABLED;
    private static final ModConfigSpec.BooleanValue PROCESS_ASSEMBLY;
    private static final ModConfigSpec.BooleanValue PROCESS_FILLING;
    private static final ModConfigSpec.BooleanValue PROCESS_SPLASH;
    private static final ModConfigSpec.BooleanValue PROCESS_BLASTING;
    private static final ModConfigSpec.BooleanValue PROCESS_SMOKING;
    private static final ModConfigSpec.BooleanValue PROCESS_HAUNTING;
    // ---- 战斗
    private static final ModConfigSpec.BooleanValue COMBAT_ENABLED;
    private static final ModConfigSpec.IntValue COMBAT_PERMISSION_LEVEL;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment(
            "Create Better Wrench —— 功能开关与可调参数 / Feature switches and tunable values",
            "数值由服务端权威读取; 单人游戏里客户端与服务端共用同一份配置。",
            "Values are authoritative on the server; in single-player the client and the integrated server share them.",
            "游戏内用 /cbw config 打开配置页面(改完关页面即保存并立即生效)。",
            "Open the config screen in game with /cbw config (changes apply immediately and are saved on close).");

        // ------------------------------------------------------------ 连接 / Connect
        b.comment(
                "[连接] Connect",
                "★ 总开关关掉后, 连接模式仍可切换, 但每次使用都会提示「此功能未启用」。",
                "★ When disabled the mode can still be selected, but every attempt reports it as unavailable.")
            .push("connect");

        CONNECT_ENABLED = b
            .comment(
                "连接功能总开关, 默认开。",
                "★ 关掉 = 整个「连接」模式不可用(切过去/右键都会提示「此功能未启用」, 且不放任何方块)。",
                "Master switch for the Connect feature (default on).",
                "★ Off = the whole Connect mode is unavailable (switching/using it reports it as unavailable, nothing is placed).")
            .define("enabled", DEFAULT_CONNECT_ENABLED);

        CONNECT_MAX_CORNERS = b
            .comment(
                "连接模式: 拐点数量上限, 默认 32。",
                "★ 超过就点不下新拐点了(actionbar 会提示)。数值越大, 一条路线能拐的弯越多。",
                "Connect: maximum number of corner points (default 32).",
                "★ Beyond this you cannot add more corners (an action-bar hint appears). Bigger = more turns per route.")
            .defineInRange("max_corners", DEFAULT_CONNECT_MAX_CORNERS, 1, 256);

        CONNECT_MAX_LEG_LENGTH = b
            .comment(
                "连接模式: **单段**两个节点之间的直线长度上限(格), 默认 64。",
                "★ 超过就会报'某段路径过长'; 想连更远就多加拐点, 或把这个值调大。",
                "Connect: maximum length of a single straight segment between two nodes, in blocks (default 64).",
                "★ Longer segments are reported as 'a segment is too long'; add a corner, or raise this value.")
            .defineInRange("max_leg_length", DEFAULT_CONNECT_MAX_LEG_LENGTH, 1, 512);

        CONNECT_MAX_TOTAL_BLOCKS = b
            .comment(
                "连接模式: 一次连接最多铺设多少方块(轴 + 齿轮箱 + 大齿轮), 默认 256。",
                "★ 这是**服务端安全上限**(挡住超大工程把服务器卡住); 调大会同时放宽客户端的绿色预览。",
                "Connect: maximum total blocks placed by one connection (shafts + gearboxes + large cogwheels), default 256.",
                "★ This is a server-side safety cap against huge builds; raising it also relaxes the client's green preview.")
            .defineInRange("max_total_blocks", DEFAULT_CONNECT_MAX_TOTAL_BLOCKS, 1, 4096);

        b.pop();

        // ------------------------------------------------------------ 拆除 / Deconstruct
        b.comment(
                "[拆除] Deconstruct",
                "★ 这里的三项影响'能不能拆'、'能拆什么'与'拆起来卡不卡'。",
                "★ These control whether a selection is allowed, which blocks may be removed, and how smoothly.")
            .push("deconstruct");

        DECONSTRUCT_ENABLED = b
            .comment(
                "拆除功能总开关, 默认开。",
                "★ 关掉 = 整个「拆除」模式不可用(切过去/右键都会提示「此功能未启用」)。",
                "Master switch for the Deconstruct feature (default on).",
                "★ Off = the whole Deconstruct mode is unavailable.")
            .define("enabled", DEFAULT_DECONSTRUCT_ENABLED);

        DECONSTRUCT_ALLOW_CREATE = b
            .comment(
                "是否允许拆除**机械动力方块**(命名空间 create), 默认允许。",
                "★ 关掉后玩家只能拆红石类方块(范围档自动锁定为「仅红石」, Ctrl 切不动); 两个都关则总开关一并关闭。",
                "Whether Create blocks (namespace 'create') may be removed (default yes).",
                "★ When off the scope is forced to 'redstone only' (Ctrl cannot change it); turning both off also disables the master switch.")
            .define("allow_create_blocks", DEFAULT_DECONSTRUCT_ALLOW_CREATE);

        DECONSTRUCT_ALLOW_REDSTONE = b
            .comment(
                "是否允许拆除**红石类方块**(拉杆/中继器/比较器/漏斗/铁轨/红石线等), 默认允许。",
                "★ 关掉后玩家只能拆机械动力方块(范围档自动锁定为「仅机械动力」)。",
                "Whether redstone components may be removed (default yes).",
                "★ When off the scope is forced to 'create only'.")
            .define("allow_redstone_blocks", DEFAULT_DECONSTRUCT_ALLOW_REDSTONE);

        DECONSTRUCT_MAX_EDGE = b
            .comment(
                "拆除选区单轴上限(格), 默认 64。",
                "★ 选区的任意一条边超过这个值就会被**拒绝**(不会拆任何方块); 客户端会顺手把选区框画成红色提示。",
                "Max edge length of a deconstruct selection, in blocks (default 64).",
                "★ If any axis of the selection is longer than this, the request is rejected (nothing is removed); the client draws the selection box red as a hint.")
            .defineInRange("max_edge", DEFAULT_DECONSTRUCT_MAX_EDGE, 1, 512);

        DECONSTRUCT_BLOCKS_PER_TICK = b
            .comment(
                "拆除分帧粒度: 每个服务端刻最多拆除多少格, 默认 1024。",
                "★ 调大 = 拆得更快但单刻更卡(可能导致服务器瞬间卡顿); 调小 = 更平滑但总时间更长。",
                "Deconstruct: how many blocks are removed per server tick (default 1024).",
                "★ Higher = faster but a heavier single tick (can cause a visible hitch); lower = smoother but takes longer overall.")
            .defineInRange("blocks_per_tick", DEFAULT_DECONSTRUCT_BLOCKS_PER_TICK, 16, 16384);

        b.pop();

        // ------------------------------------------------------------ 加工 / Process
        b.comment(
                "[加工] Process",
                "★ 总开关 + 六种加工方式各自的开关(装配/注液/洗涤/冶炼/烤制/缠魂)。",
                "★ Master switch plus one switch per processing kind (assembly/filling/washing/blasting/smoking/haunting).")
            .push("process");

        PROCESS_ENABLED = b
            .comment(
                "加工功能总开关, 默认开。",
                "★ 关掉 = 整个「加工」模式不可用(切过去/右键都会提示「此功能未启用」)。",
                "Master switch for the Process feature (default on).",
                "★ Off = the whole Process mode is unavailable.")
            .define("enabled", DEFAULT_PROCESS_ENABLED);

        PROCESS_ASSEMBLY = subSwitch(b, "assembly", "装配(序列装配 / 机械手式施加)");
        PROCESS_FILLING = subSwitch(b, "filling", "注液(手持流体桶给台面物品注入流体)");
        PROCESS_SPLASH = subSwitch(b, "splash", "洗涤(水桶)");
        PROCESS_BLASTING = subSwitch(b, "blasting", "冶炼(岩浆桶)");
        PROCESS_SMOKING = subSwitch(b, "smoking", "烤制(打火石)");
        PROCESS_HAUNTING = subSwitch(b, "haunting", "缠魂(打火石 + 台下灵魂沙/灵魂土)");

        b.pop();

        // ------------------------------------------------------------ 战斗 / Combat
        b.comment(
                "[战斗] Combat",
                "★ 战斗模式是本模组的彩蛋: 在「模组描述」模式下用 Ctrl 切换, 手持扳手时获得 +5 伤害 / +20 攻速。",
                "★ Combat mode is an easter egg: toggle it with Ctrl in the 'mod description' mode.")
            .push("combat");

        COMBAT_ENABLED = b
            .comment(
                "是否启用战斗模式, 默认启用。",
                "★ 关掉 = 任何人都打不开战斗模式(切换时提示「此功能未启用」)。",
                "Whether combat mode exists at all (default yes).",
                "★ Off = nobody can toggle it on.")
            .define("enabled", DEFAULT_COMBAT_ENABLED);

        COMBAT_PERMISSION_LEVEL = b
            .comment(
                "战斗模式需要的权限等级, 默认 2。",
                "★ 0 = 普通玩家也能开; 2 = 需要 OP(与原版'可执行多数管理指令'的等级一致); 也可填 1/3/4。",
                "★ 另外可用指令单独授权某个玩家: /cbw combat <目标选择器> true —— 被单独授权的玩家不受这个等级限制。",
                "Permission level required for combat mode (default 2).",
                "★ 0 = everyone, 2 = OP only; 1/3/4 also accepted.",
                "★ Individual players can be authorized with /cbw combat <selector> true, bypassing the level.")
            .defineInRange("permission_level", DEFAULT_COMBAT_PERMISSION_LEVEL, 0, 4);

        b.pop();

        SPEC = b.build();
    }

    /** 六个加工子开关的公共定义(注释格式一致)。 */
    private static ModConfigSpec.BooleanValue subSwitch(ModConfigSpec.Builder b, String id, String zhName) {
        return b
            .comment(
                "加工方式「" + zhName + "」的开关, 默认开。",
                "★ 关掉后仍可保持在加工模式, 但用这种方式加工时只会收到「此子功能未启用」, 不消耗任何材料。",
                "Switch for the '" + id + "' processing kind (default on).",
                "★ When off the attempt only reports the sub-feature as unavailable and consumes nothing.")
            .define(id, DEFAULT_PROCESS_SUB);
    }

    private WrenchConfig() {
    }

    // ---------------------------------------------------------------- 读取(带兜底)
    // ⚠️ 专用服务器上的客户端读不到 SERVER 配置 ⇒ SPEC.isLoaded() 为 false,
    //    此时优先用服务端下发的 FeatureToggles 快照, 再退到默认值, 绝不抛异常。

    private static FeatureToggles.Snapshot remote() {
        return FeatureToggles.get();
    }

    /** 连接: 功能是否启用。 */
    public static boolean connectEnabled() {
        if (SPEC.isLoaded())
            return CONNECT_ENABLED.get();
        FeatureToggles.Snapshot s = remote();
        return s == null ? DEFAULT_CONNECT_ENABLED : s.connectEnabled();
    }

    /** 连接拐点数量上限。 */
    public static int connectMaxCorners() {
        if (SPEC.isLoaded())
            return CONNECT_MAX_CORNERS.get();
        FeatureToggles.Snapshot s = remote();
        return s == null ? DEFAULT_CONNECT_MAX_CORNERS : s.connectMaxCorners();
    }

    /** 连接单段直线长度上限(格)。 */
    public static int connectMaxLegLength() {
        if (SPEC.isLoaded())
            return CONNECT_MAX_LEG_LENGTH.get();
        FeatureToggles.Snapshot s = remote();
        return s == null ? DEFAULT_CONNECT_MAX_LEG_LENGTH : s.connectMaxLegLength();
    }

    /** 连接单次方块总数上限。 */
    public static int connectMaxTotalBlocks() {
        if (SPEC.isLoaded())
            return CONNECT_MAX_TOTAL_BLOCKS.get();
        FeatureToggles.Snapshot s = remote();
        return s == null ? DEFAULT_CONNECT_MAX_TOTAL_BLOCKS : s.connectMaxTotalBlocks();
    }

    /**
     * 拆除: 功能是否启用(把"两个子项都关掉 ⇒ 总开关也算关"这条规则也算进来)。
     * 即使有人直接改 TOML 把两个子项都设成 false 而总开关仍是 true, 这里也会正确返回 false。
     */
    public static boolean deconstructEnabled() {
        return deconstructConfigEnabled() && (deconstructAllowCreate() || deconstructAllowRedstone());
    }

    /** 拆除: 配置文件里那个总开关的**原始**值(界面显示用; 功能是否可用请看 {@link #deconstructEnabled()})。 */
    public static boolean deconstructConfigEnabled() {
        if (SPEC.isLoaded())
            return DECONSTRUCT_ENABLED.get();
        FeatureToggles.Snapshot s = remote();
        return s == null ? DEFAULT_DECONSTRUCT_ENABLED : s.deconstructEnabled();
    }

    /** 拆除: 是否允许破坏机械动力方块。 */
    public static boolean deconstructAllowCreate() {
        if (SPEC.isLoaded())
            return DECONSTRUCT_ALLOW_CREATE.get();
        FeatureToggles.Snapshot s = remote();
        return s == null ? DEFAULT_DECONSTRUCT_ALLOW_CREATE : s.deconstructAllowCreate();
    }

    /** 拆除: 是否允许破坏红石方块。 */
    public static boolean deconstructAllowRedstone() {
        if (SPEC.isLoaded())
            return DECONSTRUCT_ALLOW_REDSTONE.get();
        FeatureToggles.Snapshot s = remote();
        return s == null ? DEFAULT_DECONSTRUCT_ALLOW_REDSTONE : s.deconstructAllowRedstone();
    }

    /**
     * 拆除: 被**强制锁定**的范围档; {@code null} = 两个子项都允许 ⇒ 玩家可以自由 Ctrl 切换。
     *
     * <p>用户规则: 不允许机械动力 ⇒ 只能"仅红石"; 不允许红石 ⇒ 只能"仅机械动力"。</p>
     */
    public static DeconstructScope deconstructForcedScope() {
        boolean create = deconstructAllowCreate();
        boolean redstone = deconstructAllowRedstone();
        if (create && redstone)
            return null;
        if (redstone)
            return DeconstructScope.REDSTONE_ONLY;
        if (create)
            return DeconstructScope.CREATE_ONLY;
        return DeconstructScope.ALL;   // 两个都关(总开关也会关) ⇒ 不会真正生效
    }

    /** 拆除: 客户端/服务端共用的"这一档到底能拆什么" —— 强制档会**覆盖**玩家选的那档。 */
    public static DeconstructScope effectiveScope(DeconstructScope requested) {
        DeconstructScope forced = deconstructForcedScope();
        return forced == null ? requested : forced;
    }

    /** 拆除选区单轴上限(格)。 */
    public static int deconstructMaxEdge() {
        if (SPEC.isLoaded())
            return DECONSTRUCT_MAX_EDGE.get();
        FeatureToggles.Snapshot s = remote();
        return s == null ? DEFAULT_DECONSTRUCT_MAX_EDGE : s.deconstructMaxEdge();
    }

    /** 拆除每服务端刻处理的格数。 */
    public static int deconstructBlocksPerTick() {
        if (SPEC.isLoaded())
            return DECONSTRUCT_BLOCKS_PER_TICK.get();
        FeatureToggles.Snapshot s = remote();
        return s == null ? DEFAULT_DECONSTRUCT_BLOCKS_PER_TICK : s.deconstructBlocksPerTick();
    }

    /** 加工: 功能是否启用(六个子功能都关掉时也返回 false, 与联动规则一致)。 */
    public static boolean processEnabled() {
        if (!processConfigEnabled())
            return false;
        for (ProcessKind kind : ProcessKind.values())
            if (processKindEnabled(kind))
                return true;
        return false;
    }

    /** 加工: 配置文件里那个总开关的**原始**值。 */
    public static boolean processConfigEnabled() {
        if (SPEC.isLoaded())
            return PROCESS_ENABLED.get();
        FeatureToggles.Snapshot s = remote();
        return s == null ? DEFAULT_PROCESS_ENABLED : s.processEnabled();
    }

    /** 加工: 某一种加工方式是否启用(**不含**总开关, 调用方需要自行叠加 {@link #processConfigEnabled()})。 */
    public static boolean processKindEnabled(ProcessKind kind) {
        if (SPEC.isLoaded()) {
            return switch (kind) {
                case ASSEMBLY -> PROCESS_ASSEMBLY.get();
                case FILLING -> PROCESS_FILLING.get();
                case SPLASH -> PROCESS_SPLASH.get();
                case BLASTING -> PROCESS_BLASTING.get();
                case SMOKING -> PROCESS_SMOKING.get();
                case HAUNTING -> PROCESS_HAUNTING.get();
            };
        }
        FeatureToggles.Snapshot s = remote();
        if (s == null)
            return DEFAULT_PROCESS_SUB;
        return switch (kind) {
            case ASSEMBLY -> s.processAssembly();
            case FILLING -> s.processFilling();
            case SPLASH -> s.processSplash();
            case BLASTING -> s.processBlasting();
            case SMOKING -> s.processSmoking();
            case HAUNTING -> s.processHaunting();
        };
    }

    /** 加工: 这一种方式**整体**能不能用(总开关 + 子开关)。 */
    public static boolean processKindUsable(ProcessKind kind) {
        return processConfigEnabled() && processKindEnabled(kind);
    }

    /** 战斗: 是否启用战斗模式。 */
    public static boolean combatEnabled() {
        if (SPEC.isLoaded())
            return COMBAT_ENABLED.get();
        FeatureToggles.Snapshot s = remote();
        return s == null ? DEFAULT_COMBAT_ENABLED : s.combatEnabled();
    }

    /** 战斗: 需要的权限等级(0 = 普通, 2 = OP)。 */
    public static int combatPermissionLevel() {
        if (SPEC.isLoaded())
            return COMBAT_PERMISSION_LEVEL.get();
        FeatureToggles.Snapshot s = remote();
        return s == null ? DEFAULT_COMBAT_PERMISSION_LEVEL : s.combatPermissionLevel();
    }

    /** 服务端: 把当前配置 + 该玩家的单独授权打包成一份快照(登录/配置重载/授权变更时下发)。 */
    public static FeatureToggles.Snapshot snapshot(boolean combatGranted) {
        return new FeatureToggles.Snapshot(
            connectEnabled(),
            connectMaxCorners(), connectMaxLegLength(), connectMaxTotalBlocks(),
            deconstructConfigEnabled(), deconstructAllowCreate(), deconstructAllowRedstone(),
            deconstructMaxEdge(), deconstructBlocksPerTick(),
            processConfigEnabled(),
            processKindEnabled(ProcessKind.ASSEMBLY), processKindEnabled(ProcessKind.FILLING),
            processKindEnabled(ProcessKind.SPLASH), processKindEnabled(ProcessKind.BLASTING),
            processKindEnabled(ProcessKind.SMOKING), processKindEnabled(ProcessKind.HAUNTING),
            combatEnabled(), combatPermissionLevel(), combatGranted);
    }

    /** 当前进程能不能改这些值(专用服务器上的客户端拿不到 SERVER 配置 ⇒ false, 界面自动变只读)。 */
    public static boolean isWritable() {
        return SPEC.isLoaded();
    }

    /** 把内存里的值写进配置文件(配置页面关闭时调用一次, 避免拖动过程中反复写盘)。 */
    public static void saveAll() {
        if (SPEC.isLoaded())
            SPEC.save();
    }

    // ---------------------------------------------------------------- 自绘配置界面用的行表

    /** 一行是什么控件。 */
    public enum Kind {
        /** 布尔开关(总开关 / 六个子功能 / 两条"允许")。 */
        TOGGLE,
        /** 数值滑块。 */
        NUMBER,
        /** 两档选择(战斗模式的权限等级: 普通 / OP)。 */
        LEVEL
    }

    /** 配置页面里的一行。{@code group} = 所属分组(页面据此画分组标题)。 */
    public record Row(String group, Kind kind, String path, ModConfigSpec.ConfigValue<?> value) {

        public boolean isToggle() {
            return kind == Kind.TOGGLE;
        }

        /**
         * 布尔行的当前值。
         *
         * <p>⚠️ 专用服务器上的客户端读不到 SERVER 配置({@code SPEC} 未加载)⇒ 优先显示**服务端下发的快照**
         * ({@link FeatureToggles}), 没有快照才回落到默认值 —— 这样 OP 在只读页面上看到的也是"服务器实际开着什么"。</p>
         */
        public boolean asBool() {
            if (SPEC.isLoaded())
                return value.get() instanceof Boolean b && b;
            FeatureToggles.Snapshot s = FeatureToggles.get();
            if (s == null)
                return defaultBool();
            return switch (path) {
                case "connect.enabled" -> s.connectEnabled();
                case "deconstruct.enabled" -> s.deconstructEnabled();
                case "deconstruct.allow_create_blocks" -> s.deconstructAllowCreate();
                case "deconstruct.allow_redstone_blocks" -> s.deconstructAllowRedstone();
                case "process.enabled" -> s.processEnabled();
                case "process.assembly" -> s.processAssembly();
                case "process.filling" -> s.processFilling();
                case "process.splash" -> s.processSplash();
                case "process.blasting" -> s.processBlasting();
                case "process.smoking" -> s.processSmoking();
                case "process.haunting" -> s.processHaunting();
                case "combat.enabled" -> s.combatEnabled();
                default -> defaultBool();
            };
        }

        /** 布尔行的默认值。 */
        public boolean defaultBool() {
            Object v = value.getDefault();
            return v instanceof Boolean b && b;
        }

        /** 数值行的当前值(未加载时优先用服务端快照, 再退回默认值)。 */
        public double asNumber() {
            if (SPEC.isLoaded())
                return ((Number) value.get()).doubleValue();
            FeatureToggles.Snapshot s = FeatureToggles.get();
            if (s == null)
                return defaultNumber();
            return switch (path) {
                case "connect.max_corners" -> s.connectMaxCorners();
                case "connect.max_leg_length" -> s.connectMaxLegLength();
                case "connect.max_total_blocks" -> s.connectMaxTotalBlocks();
                case "deconstruct.max_edge" -> s.deconstructMaxEdge();
                case "deconstruct.blocks_per_tick" -> s.deconstructBlocksPerTick();
                case "combat.permission_level" -> s.combatPermissionLevel();
                default -> defaultNumber();
            };
        }

        /** 数值行的默认值。 */
        public double defaultNumber() {
            return ((Number) value.getDefault()).doubleValue();
        }

        /** 数值行下限。 */
        public double min() {
            return ((Number) value.getSpec().getRange().getMin()).doubleValue();
        }

        /** 数值行上限。 */
        public double max() {
            return ((Number) value.getSpec().getRange().getMax()).doubleValue();
        }

        public String labelKey() {
            return "gui.create_better_wrench.config.opt." + path;
        }

        public String descKey() {
            return "gui.create_better_wrench.config.desc." + path;
        }
    }

    /** 页面里的全部分组(顺序 = 显示顺序)。 */
    public static List<String> groups() {
        return List.of("connect", "deconstruct", "process", "combat");
    }

    /** 页面里的全部行(顺序 = 显示顺序, 分组连续出现)。 */
    public static List<Row> rows() {
        return List.of(
            new Row("connect", Kind.TOGGLE, "connect.enabled", CONNECT_ENABLED),
            new Row("connect", Kind.NUMBER, "connect.max_corners", CONNECT_MAX_CORNERS),
            new Row("connect", Kind.NUMBER, "connect.max_leg_length", CONNECT_MAX_LEG_LENGTH),
            new Row("connect", Kind.NUMBER, "connect.max_total_blocks", CONNECT_MAX_TOTAL_BLOCKS),

            new Row("deconstruct", Kind.TOGGLE, "deconstruct.enabled", DECONSTRUCT_ENABLED),
            new Row("deconstruct", Kind.TOGGLE, "deconstruct.allow_create_blocks", DECONSTRUCT_ALLOW_CREATE),
            new Row("deconstruct", Kind.TOGGLE, "deconstruct.allow_redstone_blocks", DECONSTRUCT_ALLOW_REDSTONE),
            new Row("deconstruct", Kind.NUMBER, "deconstruct.max_edge", DECONSTRUCT_MAX_EDGE),
            new Row("deconstruct", Kind.NUMBER, "deconstruct.blocks_per_tick", DECONSTRUCT_BLOCKS_PER_TICK),

            new Row("process", Kind.TOGGLE, "process.enabled", PROCESS_ENABLED),
            new Row("process", Kind.TOGGLE, "process.assembly", PROCESS_ASSEMBLY),
            new Row("process", Kind.TOGGLE, "process.filling", PROCESS_FILLING),
            new Row("process", Kind.TOGGLE, "process.splash", PROCESS_SPLASH),
            new Row("process", Kind.TOGGLE, "process.blasting", PROCESS_BLASTING),
            new Row("process", Kind.TOGGLE, "process.smoking", PROCESS_SMOKING),
            new Row("process", Kind.TOGGLE, "process.haunting", PROCESS_HAUNTING),

            new Row("combat", Kind.TOGGLE, "combat.enabled", COMBAT_ENABLED),
            new Row("combat", Kind.LEVEL, "combat.permission_level", COMBAT_PERMISSION_LEVEL));
    }

    // ---------------------------------------------------------------- 写入(带联动规则)

    /** 这个开关是不是"总开关"。 */
    private static boolean isMaster(String path) {
        return path.equals("connect.enabled") || path.equals("deconstruct.enabled") || path.equals("process.enabled");
    }

    /** 这个开关是不是"子功能开关"。 */
    private static boolean isSub(String path) {
        if (path.equals("deconstruct.allow_create_blocks") || path.equals("deconstruct.allow_redstone_blocks"))
            return true;
        for (ProcessKind kind : ProcessKind.values())
            if (path.equals(kind.configPath()))
                return true;
        return false;
    }

    /** 写一个布尔开关, 并应用全部联动规则(用户 2026-09-25 定)。 */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void applyToggle(Row row, boolean on) {
        if (!SPEC.isLoaded() || !row.isToggle())
            return;
        ((ModConfigSpec.ConfigValue) row.value()).set(on);
        String path = row.path();

        if (on) {
            // 总开关打开 ⇒ 它下面的子功能一并打开
            if (path.equals("deconstruct.enabled")) {
                DECONSTRUCT_ALLOW_CREATE.set(true);
                DECONSTRUCT_ALLOW_REDSTONE.set(true);
            } else if (path.equals("process.enabled")) {
                setAllProcessKinds(true);
            } else if (isSub(path) || isMaster(path)) {
                // 子功能打开 ⇒ 它所属的总开关也打开(免得"子开着、总关着"看不出效果)
                masterOf(path).set(true);
            }
            // 打开"允许机械动力/红石"时, 总开关也一并打开
            if (path.equals("deconstruct.allow_create_blocks") || path.equals("deconstruct.allow_redstone_blocks"))
                DECONSTRUCT_ENABLED.set(true);
        }
        normalize();
    }

    /** 写一个数值(或 LEVEL 档位)。 */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void applyNumber(Row row, double v) {
        if (!SPEC.isLoaded() || row.isToggle())
            return;
        double clamped = Math.max(row.min(), Math.min(row.max(), v));
        ((ModConfigSpec.ConfigValue) row.value()).set((int) Math.round(clamped));
    }

    /** 把某一行恢复默认值。 */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void resetRow(Row row) {
        if (!SPEC.isLoaded())
            return;
        ModConfigSpec.ConfigValue raw = row.value();
        if (row.isToggle())
            raw.set(row.defaultBool());
        else
            raw.set((int) Math.round(row.defaultNumber()));
        normalize();
    }

    /** 全部恢复默认(再跑一次联动规则)。 */
    public static void resetAll() {
        if (!SPEC.isLoaded())
            return;
        for (Row row : rows())
            resetRow(row);
        normalize();
    }

    /** 子开关所属的总开关。 */
    @SuppressWarnings("rawtypes")
    private static ModConfigSpec.ConfigValue<Boolean> masterOf(String path) {
        if (path.startsWith("process."))
            return PROCESS_ENABLED;
        if (path.startsWith("deconstruct."))
            return DECONSTRUCT_ENABLED;
        return CONNECT_ENABLED;
    }

    private static void setAllProcessKinds(boolean on) {
        PROCESS_ASSEMBLY.set(on);
        PROCESS_FILLING.set(on);
        PROCESS_SPLASH.set(on);
        PROCESS_BLASTING.set(on);
        PROCESS_SMOKING.set(on);
        PROCESS_HAUNTING.set(on);
    }

    /**
     * 规则兜底(用户 2026-09-25): 拆除的两个"允许"都关 ⇒ 总开关关; 加工的六个子功能都关 ⇒ 总开关关。
     * 这样即使有人直接编辑 TOML, 也不会出现"总开关开着但什么都不能做"的自相矛盾状态。
     */
    public static void normalize() {
        if (!SPEC.isLoaded())
            return;
        if (!DECONSTRUCT_ALLOW_CREATE.get() && !DECONSTRUCT_ALLOW_REDSTONE.get())
            DECONSTRUCT_ENABLED.set(false);
        boolean anyKind = PROCESS_ASSEMBLY.get() || PROCESS_FILLING.get() || PROCESS_SPLASH.get()
            || PROCESS_BLASTING.get() || PROCESS_SMOKING.get() || PROCESS_HAUNTING.get();
        if (!anyKind)
            PROCESS_ENABLED.set(false);
    }
}
