package com.nonono.createbetterwrench.config;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 「万能扳手」的**可调参数**(NeoForge 配置)。
 *
 * <p>这些数值原先硬编码在代码里(见 docs/reference/01-hardcoded-data.md), 现在集中到这里, 让整合包作者/服主/玩家可调。</p>
 *
 * <h2>配置文件在哪 / Where is the file</h2>
 * <ul>
 *   <li>单人游戏: <b>存档目录</b>下的 <code>serverconfig/create_better_wrench-server.toml</code>
 *       (即"每个世界一份");</li>
 *   <li>专用服务器: 服务器根目录的 <code>serverconfig/create_better_wrench-server.toml</code>;</li>
 *   <li>游戏内也可以改: 模组列表 → Create Better Wrench → 配置(NeoForge 内置配置界面; 由
 *       {@code client/BetterWrenchClient} 注册)。</li>
 * </ul>
 * <p>类型是 <b>SERVER</b>: 数值由**服务端权威**读取与校验。单人游戏里客户端与内置服务端在同一个进程,
 * 因此客户端预览(拆除红框、连接幽灵预览)会读到同一份值; 在**专用服务器**上客户端读不到服务端配置,
 * 预览会退回下面的默认值(真正的限制仍由服务端执行)。</p>
 *
 * <p>⚠️ 本类**不引用任何客户端类**, 两端都能安全加载。</p>
 */
public final class WrenchConfig {

    // ---------------------------------------------------------------- 默认值
    // 代码里的"兜底默认值": 配置尚未加载(例如专用服务器上的客户端)时使用, 必须与下面 defineInRange 的默认值一致。

    /** 拆除: 选区单轴上限(格)。 */
    public static final int DEFAULT_DECONSTRUCT_MAX_EDGE = 64;
    /** 拆除: 每个服务端刻最多处理多少格。 */
    public static final int DEFAULT_DECONSTRUCT_BLOCKS_PER_TICK = 1024;
    /** 连接: 拐点数量上限。 */
    public static final int DEFAULT_CONNECT_MAX_CORNERS = 32;
    /** 连接: 每段边的直线长度上限(格)。 */
    public static final int DEFAULT_CONNECT_MAX_LEG_LENGTH = 64;
    /** 连接: 单次请求可铺设的方块总数上限。 */
    public static final int DEFAULT_CONNECT_MAX_TOTAL_BLOCKS = 256;
    /** 连接: 终点与玩家的最大距离(格)。 */
    public static final double DEFAULT_CONNECT_MAX_END_DISTANCE = 8.0;

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue DECONSTRUCT_MAX_EDGE;
    private static final ModConfigSpec.IntValue DECONSTRUCT_BLOCKS_PER_TICK;
    private static final ModConfigSpec.IntValue CONNECT_MAX_CORNERS;
    private static final ModConfigSpec.IntValue CONNECT_MAX_LEG_LENGTH;
    private static final ModConfigSpec.IntValue CONNECT_MAX_TOTAL_BLOCKS;
    private static final ModConfigSpec.DoubleValue CONNECT_MAX_END_DISTANCE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment(
            "Create Better Wrench —— 可调参数 / Tunable values",
            "数值由服务端权威读取; 单人游戏里客户端与服务端共用同一份配置。",
            "Values are authoritative on the server; in single-player the client and the integrated server share them.",
            "改完需要重新进入世界/重连(或执行 /reload)才会生效。",
            "Changes take effect after rejoining the world / server (or running /reload).");

        // ------------------------------------------------------------ 拆除 / Deconstruct
        b.comment(
                "[拆除] Deconstruct",
                "★ 这里的两项影响'能不能拆'与'拆起来卡不卡'。",
                "★ These two control whether a selection is allowed and how smoothly it is removed.")
            .push("deconstruct");

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

        // ------------------------------------------------------------ 连接 / Connect
        b.comment(
                "[连接] Connect",
                "★ 这四个值决定'能连多远、多复杂', 同时也是服务端的安全上限(防止改包客户端一次铺满世界)。",
                "★ These four decide how far/complex a connection may be, and double as the server-side safety caps.")
            .push("connect");

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

        CONNECT_MAX_END_DISTANCE = b
            .comment(
                "连接模式: 点终点时, 终点离玩家最多多少格, 默认 8.0。",
                "★ 只校验**终点**这最后一次点击(起点/拐点是一路走过去选的, 不受此限制); 调大等于允许隔空收尾。",
                "Connect: how far (in blocks) the end point may be from the player when clicked (default 8.0).",
                "★ Only the final end-click is checked (start/corners were clicked while walking, so they are not limited); raising it allows finishing from farther away.")
            .defineInRange("max_end_distance", DEFAULT_CONNECT_MAX_END_DISTANCE, 1.0, 128.0);

        b.pop();

        // ℹ️ 2026-09-22: 这里曾有一个 `assemble.fan_batch_limit`(类鼓风一次转换上限)。
        //    用户指出"**置物台本身的理论上限就是 64**(一整摞)", 没必要做成配置 ⇒ 已删除该配置项,
        //    代码改为直接取物品自己的最大堆叠数(见 AssembleLogic#tryFanProcessing)。

        SPEC = b.build();
    }

    private WrenchConfig() {
    }

    // ---------------------------------------------------------------- 读取(带兜底)
    // ⚠️ 专用服务器上的客户端读不到 SERVER 配置 ⇒ SPEC.isLoaded() 为 false, 此时一律回退到默认值, 绝不抛异常。

    /** 拆除选区单轴上限(格)。 */
    public static int deconstructMaxEdge() {
        return SPEC.isLoaded() ? DECONSTRUCT_MAX_EDGE.get() : DEFAULT_DECONSTRUCT_MAX_EDGE;
    }

    /** 拆除每服务端刻处理的格数。 */
    public static int deconstructBlocksPerTick() {
        return SPEC.isLoaded() ? DECONSTRUCT_BLOCKS_PER_TICK.get() : DEFAULT_DECONSTRUCT_BLOCKS_PER_TICK;
    }

    /** 连接拐点数量上限。 */
    public static int connectMaxCorners() {
        return SPEC.isLoaded() ? CONNECT_MAX_CORNERS.get() : DEFAULT_CONNECT_MAX_CORNERS;
    }

    /** 连接单段直线长度上限(格)。 */
    public static int connectMaxLegLength() {
        return SPEC.isLoaded() ? CONNECT_MAX_LEG_LENGTH.get() : DEFAULT_CONNECT_MAX_LEG_LENGTH;
    }

    /** 连接单次方块总数上限。 */
    public static int connectMaxTotalBlocks() {
        return SPEC.isLoaded() ? CONNECT_MAX_TOTAL_BLOCKS.get() : DEFAULT_CONNECT_MAX_TOTAL_BLOCKS;
    }

    /** 连接终点与玩家的最大距离(格)。 */
    public static double connectMaxEndDistance() {
        return SPEC.isLoaded() ? CONNECT_MAX_END_DISTANCE.get() : DEFAULT_CONNECT_MAX_END_DISTANCE;
    }

    /** 连接终点距离的**平方**(直接与 {@code distanceToSqr} 比较用)。 */
    public static double connectMaxEndDistanceSqr() {
        double d = connectMaxEndDistance();
        return d * d;
    }

    // ---------------------------------------------------------------- 自绘配置界面用的选项表

    /**
     * 一个**可编辑项**的描述 —— 供 {@code client.gui.WrenchConfigScreen} 使用。
     *
     * <p>范围/默认值全部从 NeoForge 的 spec 里取({@code getSpec().getRange()} / {@code getDefault()}),
     * 所以**不存在"界面里写死的范围与 TOML 不一致"**这种漂移。</p>
     */
    public record Option(String path, ModConfigSpec.ConfigValue<?> value) {

        public boolean isDouble() {
            return value instanceof ModConfigSpec.DoubleValue;
        }

        public String labelKey() {
            return "gui.create_better_wrench.config.opt." + path;
        }

        public String descKey() {
            return "gui.create_better_wrench.config.desc." + path;
        }

        /**
         * 当前值。⚠️ **未加载时返回默认值**: 专用服务器上的客户端拿不到 SERVER 配置,
         * {@code ConfigValue#get()} 会抛异常, 而配置界面照旧要把行画出来(只读)。
         */
        public double get() {
            return SPEC.isLoaded() ? ((Number) value.get()).doubleValue() : getDefault();
        }

        /** 默认值({@code getDefault()} 不需要配置已加载, 任何情况都能读)。 */
        public double getDefault() {
            return ((Number) value.getDefault()).doubleValue();
        }

        /** 下限({@code getSpec().getRange()} 同样不依赖"已加载")。 */
        public double min() {
            return ((Number) value.getSpec().getRange().getMin()).doubleValue();
        }

        /** 上限。 */
        public double max() {
            return ((Number) value.getSpec().getRange().getMax()).doubleValue();
        }

        /**
         * 写**内存**: NeoForge 的 {@code ConfigValue#set} 会同步更新缓存 ⇒ 本 mod 的 getter **立即生效**
         * (不需要重进世界)。落盘见 {@link WrenchConfig#saveAll()}(界面关闭时调用一次)。
         * 未加载(专用服务器客户端)时**什么都不做** —— 那时根本没有可写的服务端配置。
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public void set(double v) {
            if (!SPEC.isLoaded())
                return;
            ModConfigSpec.ConfigValue raw = value;
            if (isDouble())
                raw.set(v);
            else
                raw.set((int) Math.round(v));
        }
    }

    /** 配置界面要展示的全部选项(顺序 = 界面里的顺序)。 */
    public static List<Option> options() {
        return List.of(
            new Option("deconstruct.max_edge", DECONSTRUCT_MAX_EDGE),
            new Option("deconstruct.blocks_per_tick", DECONSTRUCT_BLOCKS_PER_TICK),
            new Option("connect.max_corners", CONNECT_MAX_CORNERS),
            new Option("connect.max_leg_length", CONNECT_MAX_LEG_LENGTH),
            new Option("connect.max_total_blocks", CONNECT_MAX_TOTAL_BLOCKS),
            new Option("connect.max_end_distance", CONNECT_MAX_END_DISTANCE));
    }

    /** 当前进程能不能改这些值(专用服务器上的客户端拿不到 SERVER 配置 ⇒ false, 界面自动变只读)。 */
    public static boolean isWritable() {
        return SPEC.isLoaded();
    }

    /** 把内存里的值写进配置文件(界面关闭时调用一次, 避免拖动过程中反复写盘)。 */
    public static void saveAll() {
        if (SPEC.isLoaded())
            SPEC.save();
    }
}
