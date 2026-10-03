package com.nonono.createbetterwrench.assemble;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 「装配」模式的置物台锁定状态(门面)。
 *
 * <p>状态本身由 {@link AssembleLockData} 持有 —— 1.20.1 没有数据附件系统, 改用按维度持久化的
 * SavedData(调研第 7.3 节方案 C)。本类只提供"以方块实体为参数"的便利方法, 让调用点保持简洁。</p>
 *
 * <p>锁定的置物台: ①右键不会把物品取走; ②可被玩家用对应物品右键, 按 Create 的
 * {@code create:sequenced_assembly}(序列装配)推进。</p>
 */
public final class AssembleLock {

    private AssembleLock() {
    }

    /** 该机器的锁定朝向; 未锁定或客户端不可用时为 NORTH。 */
    public static Direction facing(BlockEntity be) {
        if (be == null)
            return Direction.NORTH;
        AssembleLockData data = AssembleLockData.get(be.getLevel());
        return data == null ? Direction.NORTH : data.facing(be.getBlockPos());
    }

    /** 记录锁定时的玩家朝向(由服务端在锁定请求里写入, 不信任客户端传来的方向)。 */
    public static void setFacing(BlockEntity be, Direction facing) {
        if (be == null || facing == null)
            return;
        AssembleLockData data = AssembleLockData.get(be.getLevel());
        if (data == null)
            return;
        // 朝向只在锁定时写入: 更新条目里的方向
        if (data.isLocked(be.getBlockPos()))
            data.lock(be.getBlockPos(), facing);
    }

    public static boolean isLocked(BlockEntity be) {
        if (be == null)
            return false;
        AssembleLockData data = AssembleLockData.get(be.getLevel());
        return data != null && data.isLocked(be.getBlockPos());
    }

    public static void setLocked(BlockEntity be, boolean locked) {
        if (be == null)
            return;
        AssembleLockData data = AssembleLockData.get(be.getLevel());
        if (data == null)
            return;
        if (locked)
            data.lock(be.getBlockPos(), data.facing(be.getBlockPos()));
        else
            data.unlock(be.getBlockPos());
        be.setChanged();
        // 锁定的置物台在下方是灵魂底座时要冒灵魂火焰粒子, 因此上锁/解锁时同步登记表(见 DepotSoulFlames)。
        if (be instanceof com.simibubi.create.content.logistics.depot.DepotBlockEntity)
            DepotSoulFlames.setTracked(be.getLevel(), be.getBlockPos(), locked);
    }

    /**
     * 方块被破坏/炸毁时清理锁定条目。
     *
     * <p>附件时代不需要这一步(方块实体消失, 附件随之消失); 换成 SavedData 后必须显式删除,
     * 否则会在存档里留下无效坐标。</p>
     */
    public static void clear(Level level, BlockPos pos) {
        AssembleLockData data = AssembleLockData.get(level);
        if (data != null)
            data.unlock(pos);
    }
}
