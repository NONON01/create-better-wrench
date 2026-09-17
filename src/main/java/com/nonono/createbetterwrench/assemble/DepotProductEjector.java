package com.nonono.createbetterwrench.assemble;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 「**成品先在台面上停留 2 tick, 然后弹出**」的延时弹出器。
 *
 * <p>用户要求(2026-09-17): 加工产出的成品不要立刻消失, 先在置物台台面上停一下
 * ({@link #STAY_TICKS} = 2 tick), 再作为掉落物弹出去。</p>
 *
 * <p>用户同时明确说"所有成品都被弹出"这个效果**非常好, 要保留** ——
 * 所以 {@link AssembleLogic} 里已经**刻意不再区分**成品与废料, 一律走这条弹出路径
 * (旧代码拿 {@code SequencedAssemblyRecipe.resultPool.getFirst()} 当"唯一的目标成品"去比对,
 * 但结果池其实是**按权重随机抽取**的, 那个判断本身就不成立, 详见 docs/03-operations.md)。</p>
 *
 * <p>实现方式是「服务端刻 + 到期时间」, 不依赖每 tick 的方块实体逻辑, 因此**不需要 Mixin**。</p>
 */
public final class DepotProductEjector {

    /** 成品在台面上的停留时长(用户指定: 2 tick)。 */
    public static final int STAY_TICKS = 2;

    /** 一条待弹出的成品: 哪个世界的哪个置物台、到什么时候弹。 */
    private record Pending(ServerLevel level, BlockPos pos, long dueTick) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private DepotProductEjector() {
    }

    /**
     * 把成品摆上台面, 并安排 {@link #STAY_TICKS} tick 之后把它弹出。
     *
     * <p>摆上去时会 {@code notifyUpdate()}, 所以客户端**真的看得见**它停在台面上这一下。</p>
     */
    public static void holdThenEject(ServerLevel level, BlockPos pos, DepotBlockEntity depot, ItemStack product) {
        AssembleLogic.setDepot(depot, product.copy());
        PENDING.add(new Pending(level, pos.immutable(), level.getGameTime() + STAY_TICKS));
    }

    /** 每服务端刻检查一次有没有到期的成品要弹出。 */
    @EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME)
    public static final class Tick {
        private Tick() {
        }

        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            if (PENDING.isEmpty())
                return;
            Iterator<Pending> it = PENDING.iterator();
            while (it.hasNext()) {
                Pending pending = it.next();
                if (pending.level().getGameTime() < pending.dueTick())
                    continue;
                it.remove();
                if (!(pending.level().getBlockEntity(pending.pos()) instanceof DepotBlockEntity depot))
                    continue;
                AssembleLogic.ejectHeldAndRefill(pending.level(), pending.pos(), depot);
            }
        }
    }
}
