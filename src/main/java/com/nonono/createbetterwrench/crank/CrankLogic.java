package com.nonono.createbetterwrench.crank;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.mode.WrenchMode;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 「曲柄」模式的服务端核心: 把玩家准星所指的**机械动力传动件**临时变成网络的"动力源",
 * 从而做到"用手摇动任意机器", 而不需要放置任何方块。
 *
 * <h2>原理</h2>
 * Create 里提供动力的东西都是 {@code GeneratingKineticBlockEntity}(原版曲柄、马达、水车…)。
 * 但 {@link KineticBlockEntity} 的关键方法/字段**全是 public**({@code setSpeed}/{@code setNetwork}/
 * {@code attachKinetics}/{@code detachKinetics}/{@code getOrCreateNetwork}, 以及 {@code source}、{@code network} 字段),
 * 所以我们可以照抄 {@code GeneratingKineticBlockEntity.applyNewSpeed} 里"变成新网络源"的那几步:
 * <pre>
 *   detachKinetics(); setSpeed(±rpm); source = null; setNetwork(pos.asLong()); attachKinetics();
 * </pre>
 * 再补一句 {@code getOrCreateNetwork().updateCapacityFor(be, 512f)} 把**应力容量**注入网络
 * (普通动能方块的 {@code calculateAddedStressCapacity()} 取的是它注册的容量, 传动件一般是 0)。
 *
 * <h2>安全边界</h2>
 * <ul>
 *   <li>目标**已经接通动力**({@code hasSource()})时拒绝 —— 否则会和既有网络打架, 且松手后难以完好还原。</li>
 *   <li>目标方块有**多个轴接口**(如传动轴两端、齿轮箱)时, 必须对准可接轴的那个面;只有单个接口(如鼓风机)则不强制。</li>
 *   <li>停止时按进入前记录的 {speed, source, network} 还原。</li>
 * </ul>
 *
 * <h2>饥饿</h2>
 * 原版曲柄每次 {@code causeFoodExhaustion(32 × crankHungerMultiplier)}(默认 0.32);本模式取其 **1/2**。
 */
public final class CrankLogic {

    /** 每次"摇一下"之间的最长间隔(刻);超过就自动停止并还原。 */
    private static final int TIMEOUT_TICKS = 8;

    /** 原版曲柄的转速, 用于按同口径折算饥饿。 */
    private static final float VANILLA_CRANK_RPM = 32f;

    /** 饥饿只有原版曲柄的一半。 */
    private static final float HUNGER_FACTOR = 0.5f;

    /** 每个玩家当前摇着的目标及还原信息。 */
    private record Active(ServerLevel level, BlockPos target, Direction face, int rpm, boolean backwards,
                          float savedSpeed, BlockPos savedSource, Long savedNetwork, int ticks) {
        Active refresh(int newRpm, boolean newBackwards) {
            return new Active(level, target, face, newRpm, newBackwards, savedSpeed, savedSource, savedNetwork, TIMEOUT_TICKS);
        }

        Active ticked() {
            return new Active(level, target, face, rpm, backwards, savedSpeed, savedSource, savedNetwork, ticks - 1);
        }
    }

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    private CrankLogic() {
    }

    // ---------------------------------------------------------------- 对外

    /** 客户端每次"摇"都会调用这里(按住右键时约每 4 刻一次)。 */
    public static void crank(ServerPlayer player, BlockPos targetPos, Direction face, int rpm, boolean backwards) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(targetPos))
            return;

        int clamped = clampRpm(rpm);
        UUID id = player.getUUID();

        Active current = ACTIVE.get(id);
        if (current != null && current.target().equals(targetPos) && current.level() == level) {
            // 同一个目标: 只刷新计时与参数(方向/转速变了就即时生效)
            if (current.rpm() != clamped || current.backwards() != backwards) {
                BlockEntity be = level.getBlockEntity(targetPos);
                if (be instanceof KineticBlockEntity kbe)
                    applySpeed(kbe, clamped, backwards);
            }
            ACTIVE.put(id, current.refresh(clamped, backwards));
            consumeHunger(player);
            return;
        }

        if (current != null)
            stop(player);

        BlockEntity be = level.getBlockEntity(targetPos);
        if (!(be instanceof KineticBlockEntity kbe)) {
            message(player, "msg." + BetterWrenchMod.MODID + ".crank.not_kinetic");
            return;
        }
        BlockState state = level.getBlockState(targetPos);
        if (!faceAllowed(level, targetPos, state, face)) {
            message(player, "msg." + BetterWrenchMod.MODID + ".crank.bad_face");
            return;
        }
        if (kbe.hasSource()) {
            message(player, "msg." + BetterWrenchMod.MODID + ".crank.powered");
            return;
        }

        // 记录原状以便还原
        Active active = new Active(level, targetPos, face, clamped, backwards,
            kbe.getSpeed(), kbe.source, kbe.network, TIMEOUT_TICKS);

        attach(kbe, clamped, backwards);
        ACTIVE.put(id, active);
        consumeHunger(player);
    }

    /** 主动停止(松手后由计时器触发;玩家登出时也会调用)。 */
    public static void stop(ServerPlayer player) {
        Active active = ACTIVE.remove(player.getUUID());
        if (active != null)
            restore(active);
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty())
            return;
        Iterator<Map.Entry<UUID, Active>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Active> entry = it.next();
            Active active = entry.getValue();
            if (active.ticks() > 1) {
                entry.setValue(active.ticked());
                continue;
            }
            it.remove();
            restore(active);
        }
    }

    // ---------------------------------------------------------------- 内部

    /**
     * 单接口的方块(如鼓风机)不强制对准哪个面;有多个轴接口时必须对准**能接轴**的那个面。
     */
    public static boolean faceAllowed(Level level, BlockPos pos, BlockState state, Direction clickedFace) {
        Block block = state.getBlock();
        if (!(block instanceof IRotate rotate))
            return true;

        int interfaces = 0;
        for (Direction d : Direction.values())
            if (rotate.hasShaftTowards(level, pos, state, d))
                interfaces++;

        if (interfaces <= 1)
            return true;
        return rotate.hasShaftTowards(level, pos, state, clickedFace);
    }

    /** 把自己变成网络源(等价于 GeneratingKineticBlockEntity.applyNewSpeed 的"新建网络"分支)。 */
    private static void attach(KineticBlockEntity kbe, int rpm, boolean backwards) {
        kbe.detachKinetics();
        kbe.setSpeed(signed(rpm, backwards));
        kbe.source = null; // 自己当源
        kbe.setNetwork(kbe.getBlockPos().asLong());
        kbe.attachKinetics();
        kbe.getOrCreateNetwork().updateCapacityFor(kbe, WrenchMode.CRANK_CAPACITY);
    }

    /** 转速/方向变化时只改速度, 不重建网络。 */
    private static void applySpeed(KineticBlockEntity kbe, int rpm, boolean backwards) {
        kbe.setSpeed(signed(rpm, backwards));
        kbe.getOrCreateNetwork().updateCapacityFor(kbe, WrenchMode.CRANK_CAPACITY);
    }

    private static float signed(int rpm, boolean backwards) {
        // 与 Create 曲柄一致: 不按 Shift 为正(顺时针), 按下 Shift 取负
        return backwards ? -rpm : rpm;
    }

    private static void restore(Active active) {
        BlockEntity be = active.level().getBlockEntity(active.target());
        if (!(be instanceof KineticBlockEntity kbe))
            return;

        kbe.detachKinetics();
        if (active.savedNetwork() == null) {
            kbe.setSpeed(0);
            kbe.source = null;
            kbe.setNetwork(null);
            return;
        }
        kbe.setSpeed(active.savedSpeed());
        kbe.source = active.savedSource();
        kbe.setNetwork(active.savedNetwork());
        kbe.attachKinetics();
    }

    private static void consumeHunger(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator())
            return;
        if (player.getMainHandItem().is(AllItems.EXTENDO_GRIP.get())
            || player.getOffhandItem().is(AllItems.EXTENDO_GRIP.get()))
            return; // 与 Create 曲柄一致: 伸缩握把不消耗饥饿
        float exhaustion = VANILLA_CRANK_RPM
            * AllConfigs.server().kinetics.crankHungerMultiplier.getF()
            * HUNGER_FACTOR;
        player.causeFoodExhaustion(exhaustion);
    }

    private static int clampRpm(int rpm) {
        return Math.max(WrenchMode.CRANK_RPM_MIN, Math.min(WrenchMode.CRANK_RPM_MAX, rpm));
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    // ---------------------------------------------------------------- 事件

    @EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            tick(event.getServer());
        }

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer sp)
                stop(sp);
        }
    }
}
