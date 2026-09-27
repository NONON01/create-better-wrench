package com.nonono.createbetterwrench.assemble;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 「<b>成品先在台面上停留若干 tick, 然后弹出</b>」的延时弹出器。
 *
 * <p>加工产出的成品不立刻消失, 先在置物台台面上停一下, 再作为<b>普通掉落物</b>弹出去
 * (不再有成品堆或带标记的掉落物, 见 {@link DepotPiles})。
 * 停留时长由玩家在「加工」模式里用 <b>Ctrl+滚轮</b>选择(不停留/短/中/长 = 0/2/4/8 tick),
 * 档位定义见 {@link com.nonono.createbetterwrench.mode.AssembleStay};
 * 服务端侧的值来自 {@link DepotStayState}(客户端发包同步), 由 {@link AssembleLogic} 取用后传进来。</p>
 *
 * <p>产品一律弹出(设计约定): 所有成品都被弹出的效果需要保留,
 * 因此 {@link AssembleLogic} 里已经<b>刻意不再区分</b>成品与废料, 一律走这条弹出路径
 * (旧代码拿 {@code SequencedAssemblyRecipe.resultPool.getFirst()} 当作唯一的目标成品去比对,
 * 但结果池是按权重随机抽取的, 该判断本身不成立)。</p>
 *
 * <p>实现方式是服务端刻 + 到期时间, 不依赖每 tick 的方块实体逻辑, 因此<b>不需要 Mixin</b>。</p>
 *
 * <p><b>队列纪律(审计 A-6 / A-11):</b></p>
 * <ul>
 *   <li>每条待弹记录都带<b>它当初摆上台面的那件物品</b>; 到点时如果台面上的东西已经不是它,
 *       就<b>跳过不弹</b>(否则会把玩家后来放上去或自动续料续上去的东西误弹出去)。</li>
 *   <li>置物台被破坏 / 炸毁 / 解锁时用 {@link #cancelAt} 主动作废该坐标的条目。</li>
 *   <li>
 *       世界卸载({@link LevelEvent.Unload})时清掉属于该世界的全部条目, 避免静态队列长期持有
 *       已废弃的 {@code ServerLevel} 并让条目永不淘汰。</li>
 * </ul>
 */
public final class DepotProductEjector {

    /**
     * 一条待弹出的成品: 哪个世界的哪个置物台、当初台上放的是什么、到什么时候弹。
     *
     * <p>{@code expected} 在构造时<b>取副本</b>并且此后只读, 用来在到点时确认台面上还是同一件物品
     * (比对物品与组件, 不比数量 —— 台面只应该有 1 个)。</p>
     */
    private record Pending(ServerLevel level, BlockPos pos, ItemStack expected, long dueTick) {
        private Pending {
            // 取副本并此后只读: ItemStack 是可变的, 不能让入队方之后改动影响到队列里的判定
            expected = expected.copy();
        }
    }

    /**
     * 待弹出队列。只在服务端主线程读写(入队来自右键交互, 出队来自 {@code ServerTickEvent.Post},
     * 作废来自破坏/爆炸/解锁事件), 因此不需要并发容器。
     */
    private static final List<Pending> PENDING = new ArrayList<>();

    private DepotProductEjector() {
    }

    /**
     * 把成品摆上台面, 并安排 {@code stayTicks} 个服务端刻之后把它弹出。
     *
     * <p>摆上去时会 {@code notifyUpdate()}, 所以客户端<b>能看见</b>它停在台面上这一下。
     * {@code stayTicks == 0}(档位「不停留」)时, {@code ServerTickEvent.Post} 会在<b>同一个服务端刻</b>
     * 就把它弹出去, 即立刻弹出。</p>
     *
     * @param stayTicks 停留的服务端 tick 数(由玩家 Ctrl+滚轮选的档位决定, 见 {@code mode.AssembleStay})
     */
    public static void holdThenEject(ServerLevel level, BlockPos pos, DepotBlockEntity depot,
                                     ItemStack product, int stayTicks) {
        AssembleLogic.setDepot(depot, product.copy());
        PENDING.add(new Pending(level, pos.immutable(), product, level.getGameTime() + stayTicks));
    }

    /**
     * 作废某个坐标上<b>所有</b>还在排队的「停留后弹出」条目。
     *
     * <p>调用时机: 置物台被破坏 / 被炸毁({@code AssembleInteractionHandler})、以及解锁置物台
     * ({@code AssembleLogic#returnHeldAndPiles})。不这么做的话, 条目到点后会去弹<b>那时</b>台面上的
     * 东西(可能是玩家刚续上去的原料), 把物品弹错位置。</p>
     */
    public static void cancelAt(Level level, BlockPos pos) {
        if (level == null || level.isClientSide || PENDING.isEmpty())
            return;
        PENDING.removeIf(pending -> pending.level() == level && pending.pos().equals(pos));
    }

    /** 每服务端刻检查一次有没有到期的成品要弹出。 */
    @EventBusSubscriber(modid = BetterWrenchMod.MODID)
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
                // 复审 B-14: 先判 isLoaded —— Level#getBlockEntity 走 getChunkAt, 区块没加载时会<b>同步加载</b>它
                if (!pending.level().isLoaded(pending.pos()))
                    continue;
                if (!(pending.level().getBlockEntity(pending.pos()) instanceof DepotBlockEntity depot))
                    continue;
                // 审计 A-6: 台面上的东西已经不是当初摆上去的那件(被玩家/其它路径换过), 放弃这次弹出,
                // 否则会把当下台面上的东西误弹出去。只比物品与组件, 不比数量。
                if (!ItemStack.isSameItemSameComponents(pending.expected(), depot.getHeldItem()))
                    continue;
                AssembleLogic.ejectHeldAndRefill(pending.level(), pending.pos(), depot);
            }
        }

        /**
         * 审计 A-11: 世界卸载(关服 / 退出世界 / 换世界)时清掉属于它的全部条目。
         *
         * <p>否则静态队列会一直持有已废弃的 {@code ServerLevel}(强引用), 而新世界/新存档的
         * {@code gameTime} 重新计较小值, {@code getGameTime() < dueTick} 长期为真,
         * 因此条目既不执行也永不淘汰, 形成无界泄漏。</p>
         */
        @SubscribeEvent
        public static void onLevelUnload(LevelEvent.Unload event) {
            if (PENDING.isEmpty())
                return;
            PENDING.removeIf(pending -> pending.level() == event.getLevel());
        }
    }
}
