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
 * 「**成品先在台面上停留若干 tick, 然后弹出**」的延时弹出器。
 *
 * <p>加工产出的成品不要立刻消失, 先在置物台台面上停一下, 再作为掉落物弹出去。
 * 停留时长由玩家在「加工」模式里 **Ctrl+滚轮**选择(不停留/短/中/长 = 0/2/4/8 tick),
 * 档位定义见 {@link com.nonono.createbetterwrench.mode.AssembleStay};
 * 服务端侧的值来自 {@link DepotStayState}(客户端发包同步), 由 {@link AssembleLogic} 取用后传进来。</p>
 *
 * <p>用户同时明确说"所有成品都被弹出"这个效果**非常好, 要保留** ——
 * 所以 {@link AssembleLogic} 里已经**刻意不再区分**成品与废料, 一律走这条弹出路径
 * (旧代码拿 {@code SequencedAssemblyRecipe.resultPool.getFirst()} 当"唯一的目标成品"去比对,
 * 但结果池其实是**按权重随机抽取**的, 那个判断本身就不成立, 详见 docs/03-operations.md)。</p>
 *
 * <p>实现方式是「服务端刻 + 到期时间」, 不依赖每 tick 的方块实体逻辑, 因此**不需要 Mixin**。</p>
 */
public final class DepotProductEjector {

    /** 一条待弹出的成品: 哪个世界的哪个置物台、到什么时候弹。 */
    private record Pending(ServerLevel level, BlockPos pos, long dueTick) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private DepotProductEjector() {
    }

    /**
     * 把成品摆上台面, 并安排 {@code stayTicks} 个服务端刻之后把它弹出。
     *
     * <p>摆上去时会 {@code notifyUpdate()}, 所以客户端**真的看得见**它停在台面上这一下。
     * {@code stayTicks == 0}(用户档位「不停留」)时, {@code ServerTickEvent.Post} 会在**同一个服务端刻**
     * 就把它弹出去, 即"立刻弹出"。</p>
     *
     * @param stayTicks 停留的服务端 tick 数(由玩家 Ctrl+滚轮选的档位决定, 见 {@code mode.AssembleStay})
     */
    public static void holdThenEject(ServerLevel level, BlockPos pos, DepotBlockEntity depot,
                                     ItemStack product, int stayTicks) {
        AssembleLogic.setDepot(depot, product.copy());
        PENDING.add(new Pending(level, pos.immutable(), level.getGameTime() + stayTicks));
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
