package com.nonono.createbetterwrench.chain;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 锁链连接的建立与计数, 走 Create 自身的方块实体机制。
 *
 * <p>建立路径与 Create 的连接包
 * {@code com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorConnectionPacket#applySettings}
 * 的 connect 分支完全一致(1.21.1 字节码已核对): 对两端 {@code ChainConveyorBlockEntity}
 * 分别调用 {@code addConnectionTo(对方绝对坐标)}, 先写目标端, 目标端成功后写源端, 源端失败则回滚目标端。
 * 这样可以避免「方块放好了却没有连接」的中间态。</p>
 *
 * <p>本类只负责<b>连接</b>: 不放置方块、不校验几何、不扣材料。几何判定见 {@link ChainRules},
 * 锁链数量扣减由服务端执行层按 {@link #chainCost} 完成。</p>
 */
public final class ChainConnector {

    private ChainConnector() {
    }

    /**
     * 连接两轮所需的锁链数量, 与 Create 的
     * {@code ChainConveyorBlockEntity#getChainCost(BlockPos)} 同源 —— 直接调用该方法,
     * 不做局部重算。规则为: 两端方块角点偏移的欧氏距离 / 2.5 后四舍五入, 至少 1 根。
     */
    public static int chainCost(BlockPos a, BlockPos b) {
        if (a == null || b == null)
            return 0;
        return ChainConveyorBlockEntity.getChainCost(b.subtract(a));
    }

    /**
     * 建立 a 与 b 之间的双向连接, 成功返回 {@code true}。
     *
     * <p>幂等: 任意一端已记录该连接时, 只补齐缺失的一侧, 已存在的一侧保持不变(复用既有链)。
     * 两端都已记录时本方法不写任何数据, 因此对"已双向连接的一对"重复调用既不会新增第二条记录
     * (Create 的 {@code connections} 是 {@code Set<BlockPos>}, 且只按相对坐标 {@code b.subtract(a)}
     * 写入), 也不会改动世界。</p>
     *
     * <p>仅允许在服务端调用(规格 §7 服务端权威): 客户端执行会只改本地方块实体数据、与服务端不一致,
     * 因此客户端调用直接返回 {@code false}。</p>
     */
    public static boolean connect(Level level, BlockPos a, BlockPos b) {
        if (level == null || a == null || b == null)
            return false;
        if (a.equals(b))
            return false;
        if (level.isClientSide)
            return false;
        if (!level.isLoaded(a) || !level.isLoaded(b))
            return false;

        ChainConveyorBlockEntity beA = conveyor(level, a);
        ChainConveyorBlockEntity beB = conveyor(level, b);
        if (beA == null || beB == null)
            return false;

        // 2026-10-04 内测根因: Create 的连接统计(connectionStats)只在 tick/lazyTick 里初始化,
        // 方块刚放下的同一刻调用 addConnectionTo 会在其内部触发空指针。此处显式先准备统计,
        // 使任何调用方都能安全地对"新放置的传动轮"建立连接, 而不必各自规避。
        beA.prepareStats();
        beB.prepareStats();

        BlockPos aToB = b.subtract(a);
        BlockPos bToA = a.subtract(b);

        // 与 Create 一致: 先写目标端, 再写源端; 先出现的 added=false 表示该端已有该连接
        boolean targetHadIt = beB.connections.contains(bToA);
        if (!targetHadIt && !beB.addConnectionTo(a))
            return false;

        boolean sourceHadIt = beA.connections.contains(aToB);
        if (!sourceHadIt && !beA.addConnectionTo(b)) {
            // 源端写失败: 回滚本次新建的目标端连接, 不留下单边连接
            if (!targetHadIt)
                beB.removeConnectionTo(a);
            return false;
        }

        return true;
    }

    /** 该坐标传动轮已建立的连接数; 非传动轮、未加载或无法访问时返回 0。 */
    public static int usedConnections(Level level, BlockPos pos) {
        ChainConveyorBlockEntity be = conveyor(level, pos);
        return be == null ? 0 : be.connections.size();
    }

    /** 取该坐标的传动轮方块实体; 未加载或方块不符时返回 {@code null}(不触发区块加载)。 */
    private static ChainConveyorBlockEntity conveyor(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos))
            return null;
        return level.getBlockEntity(pos) instanceof ChainConveyorBlockEntity be ? be : null;
    }
}
