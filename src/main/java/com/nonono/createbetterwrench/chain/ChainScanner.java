package com.nonono.createbetterwrench.chain;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 锁链传动轮的局部检索, 供规划器收集可复用的既有传动轮。
 *
 * <p>只读、不加载区块: 每个候选坐标先过 {@code Level#isLoaded}, 未加载的区块整块跳过
 * (项目审计 A-5 口径, 避免 {@code getBlockState} 阻塞式加载/生成区块)。</p>
 */
public final class ChainScanner {

    private ChainScanner() {
    }

    /**
     * 在 center 周围的正方体范围(切比雪夫距离 {@code <= radius}, 含 center 自身)内,
     * 返回所有锁链传动轮({@code create:chain_conveyor})的坐标。
     *
     * <p>{@code radius} 会被夹到 {@code [0, ChainRules.maxDistance()]}: 超过连接上限的搜索半径对规划无意义,
     * 且可避免超大半径的正方体扫描拖垮服务端。{@code radius < 0} 或参数为空时返回空列表。</p>
     *
     * <p>Y 范围同时按世界建筑高度裁剪。结果按「到 center 的距离平方升序, 再按 y/x/z 字典序」排序,
     * 保证同一世界状态下多次调用得到稳定顺序。</p>
     */
    public static List<BlockPos> conveyorsNear(Level level, BlockPos center, int radius) {
        List<BlockPos> found = new ArrayList<>();
        if (level == null || center == null || radius < 0)
            return found;

        int r = Math.min(radius, ChainRules.maxDistance());
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - r);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + r);
        if (minY > maxY)
            return found;

        int minX = center.getX() - r;
        int maxX = center.getX() + r;
        int minZ = center.getZ() - r;
        int maxZ = center.getZ() + r;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (!level.isLoaded(cursor))
                        continue;
                    if (ChainRules.isConveyor(level, cursor))
                        found.add(cursor.immutable());
                }
            }
        }

        found.sort((p, q) -> {
            int byDistance = Double.compare(p.distSqr(center), q.distSqr(center));
            return byDistance != 0 ? byDistance : p.compareTo(q);
        });
        return found;
    }
}
