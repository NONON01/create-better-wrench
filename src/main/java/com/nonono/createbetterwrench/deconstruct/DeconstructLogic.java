package com.nonono.createbetterwrench.deconstruct;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.nonono.createbetterwrench.mode.DeconstructScope;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 「拆除」的服务端核心: 判定一个方块是否"可用扳手拆下"、是否落在某 Ctrl 档过滤内,
 * 以及遍历一个区域批量拆除并入背包/掉落。
 *
 * <p>判据(Create 自身规则, 见 docs/07 模式二已确认规格):
 * <ul>
 *   <li>可拆 = 方块 {@code instanceof IWrenchable} 或 位于 {@code create:wrench_pickup} tag;</li>
 *   <li>仅机械动力 = 上面集合 且 命名空间 == create;</li>
 *   <li>仅红石 = 上面集合 且 属红石类(枚举 MC 红石件 + forge 红石 tag)。</li>
 * </ul></p>
 */
public final class DeconstructLogic {

    /** Create 的 wrench_pickup block tag(数据包, create 命名空间)。 */
    public static final TagKey<Block> CREATE_WRENCH_PICKUP =
        TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("create", "wrench_pickup"));

    private DeconstructLogic() {
    }

    /** 该方块是否"可被扳手拆下"(Create 定义的可拆物)。 */
    public static boolean isWrenchRemovable(BlockState state) {
        Block block = state.getBlock();
        return block instanceof IWrenchable || state.is(CREATE_WRENCH_PICKUP);
    }

    /**
     * 在"可拆"基础上, 再按拆除范围档过滤。
     * 全部→直接可拆即算; 仅机械动力→命名空间 create; 仅红石→红石类。
     */
    public static boolean matchesScope(BlockState state, DeconstructScope scope) {
        if (!isWrenchRemovable(state))
            return false;
        return switch (scope) {
            case ALL -> true;
            case CREATE_ONLY -> isNamespace(state, "create");
            case REDSTONE_ONLY -> isRedstone(state);
        };
    }

    private static boolean isNamespace(BlockState state, String ns) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key != null && ns.equals(key.getNamespace());
    }

    /**
     * 粗略红石类判定: 方块 id 含若干红石关键字。
     *
     * <p>⚠️ 2026-09 核查补了 4 个关键字(见 docs/07 §6 A-8): 实测 Create 6.0.10 的
     * {@code create:wrench_pickup} 标签里, {@code tripwire} / {@code tripwire_hook} /
     * {@code daylight_detector} / {@code hopper}, 以及 {@code #minecraft:rails} 展开出的
     * {@code rail} / {@code powered_rail} / {@code detector_rail} / {@code activator_rail}(都含 "rail")
     * **原本一个都命中不了** ⇒ 在「仅红石」档下全都拆不到(静默漏掉)。</p>
     */
    private static boolean isRedstone(BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = key == null ? "" : key.getPath();
        // 常见 MC 红石组件关键字(按钮/拉杆/门/压力板/红石线/比较器/中继器/目标方块/红石块)
        if (path.contains("button") || path.contains("lever") || path.contains("pressure_plate")
            || path.contains("redstone") || path.contains("comparator") || path.contains("repeater")
            || path.contains("target") || path.contains("observer") || path.contains("piston")
            // 2026-09 补: 绊线(钩)/阳光探测器/漏斗/各类铁轨
            || path.contains("tripwire") || path.contains("daylight_detector")
            || path.contains("hopper") || path.contains("rail"))
            return true;
        return false;
    }

    /**
     * 拆除指定的一个方块, 产物进背包(满则掉落脚下)。
     *
     * <h2>为什么不再无条件走 Create 的 {@code onSneakWrenched}</h2>
     * <p>早期实现是"只要是 {@link IWrenchable} 就交给 Create", 好处是自动获得多方块连带拆除。
     * 但 Create 的默认实现({@code IWrenchable#onSneakWrenched})**每格**都会做三件很贵的事:</p>
     * <pre>
     *   state.spawnAfterBreak(serverLevel, pos, ItemStack.EMPTY, true);  // 破坏粒子 + 经验
     *   world.destroyBlock(pos, false);                                  // levelEvent(2001) => 原版破坏粒子爆发 + 音效
     *   playRemoveSound(world, pos);                                     // Create 扳手音效
     * </pre>
     * <p>单格使用完全没问题;但**批量拆除**(实测 16240 格 / 8 批, 约 2000 格每刻)会产生
     * **约 2000 个音效事件 + 十万级粒子 + 经验球**, 把客户端直接卡住 —— 这正是用户反馈的
     * "极其短暂的卡顿"的来源(与拆除本身的计算量无关)。</p>
     *
     * <h2>现在的做法</h2>
     * <ul>
     *   <li><b>绝大多数方块</b>:自己做"静默拆除" —— 发 {@code BlockEvent.BreakEvent}(领地保护照常生效)、
     *       产物进背包, 然后 {@code level.removeBlock()}(<b>不触发</b> {@code levelEvent(2001)}),
     *       因此**没有破坏粒子、没有破坏音效、没有经验球**;</li>
     *   <li><b>少数重写了 {@code onSneakWrenched} 的方块</b>(Create 6.0.10 里共 **9 个类**:
     *       {@code CartAssemblerBlock} / {@code CopycatBlock} / {@code WhistleExtenderBlock} /
     *       {@code ChainConveyorBlock} / {@code EncasedCogwheelBlock} / {@code EncasedShaftBlock} /
     *       {@code WaterWheelStructuralBlock} / {@code FactoryPanelBlock} / {@code TrackBlock}。
     *       例如前者会把拆除重定向到主方块)仍然**交还给 Create**,
     *       否则会留下孤儿方块。它们数量少, 那点粒子开销无所谓。</li>
     * </ul>
     *
     * <p>⚠️ 刻意的一个取舍: 静默路径**不再产生经验球**({@code spawnAfterBreak} 的 {@code dropExperience=true}
     * 是 XP 的主要来源)。批量拆除是便利操作, 少掉这点经验可以接受;若将来要保留,
     * 应改为"整批结束后统一结算经验", 而不是每格刷一轮。</p>
     *
     * @return 是否真的把这个位置的方块拆掉了(用于计数)
     */
    public static boolean deconstructBlock(ServerLevel level, BlockPos pos, ServerPlayer player) {
        BlockState state = level.getBlockState(pos);
        if (!isWrenchRemovable(state))
            return false;

        if (state.getBlock() instanceof IWrenchable wrenchable && overridesSneakWrench(state.getBlock())) {
            // 有多方块连带拆除逻辑的少数方块: 只能交给 Create 自己处理
            UseOnContext context = new UseOnContext(level, player, InteractionHand.MAIN_HAND,
                player.getMainHandItem(),
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            wrenchable.onSneakWrenched(state, context);
            // 被 BreakEvent 取消时方块还在 -> 计为未拆
            return level.getBlockState(pos).isAir();
        }

        // 静默拆除(绝大多数方块走这里)
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled())
            return false;

        if (!player.isCreative())
            Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, player.getMainHandItem())
                .forEach(stack -> player.getInventory().placeItemBackInInventory(stack));

        // ⚠️ 用 removeBlock 而不是 destroyBlock: 后者会发 levelEvent(2001) => 客户端爆发破坏粒子 + 音效。
        //    批量拆除时那正是卡顿来源。removeBlock 照样会正确移除方块实体、发邻居更新。
        level.removeBlock(pos, false);
        return true;
    }

    /** 缓存"该方块类是否重写了 onSneakWrenched", 避免每格反射一次。 */
    private static final Map<Class<?>, Boolean> SNEAK_OVERRIDE = new ConcurrentHashMap<>();

    /**
     * 该方块类是否**重写**了 {@link IWrenchable#onSneakWrenched}。
     *
     * <p>用反射判断而不是硬编码类型清单: 这样不依赖具体 Create 版本有哪些多方块,
     * 将来 Create 新增/改动结构方块也能自动跟上(重写者一律走 Create 路径)。</p>
     */
    private static boolean overridesSneakWrench(Block block) {
        return SNEAK_OVERRIDE.computeIfAbsent(block.getClass(), c -> {
            try {
                Method m = c.getMethod("onSneakWrenched", BlockState.class, UseOnContext.class);
                return m.getDeclaringClass() != IWrenchable.class;
            } catch (NoSuchMethodException e) {
                return false;
            }
        });
    }

}
