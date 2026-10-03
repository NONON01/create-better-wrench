package com.nonono.createbetterwrench.assemble;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 置物台锁定状态的服务端存储(1.20.1 的<b>附着数据替代方案</b>, 即调研第 7.3 节的方案 C)。
 *
 * <p><b>为什么不能用附件</b>: NeoForge 的数据附件({@code AttachmentType})在 1.20.1 上不存在;
 * 而锁定状态必须"随世界保存", 因此改为本模组自建、<b>按维度分表</b>的服务端数据 ——
 * 用原版 {@link SavedData} 存进该维度的存档, 天然获得持久化与按维度隔离。</p>
 *
 * <p>需要显式清理的两种情形(附件时代由方块实体消失自动完成, 现在必须自己删):
 * 置物台被破坏与被爆炸摧毁 —— 两处都在 {@code AssembleInteractionHandler} 里调用
 * {@link AssembleLock#clear(Level, BlockPos)}。</p>
 */
public class AssembleLockData extends SavedData {

    private static final String DATA_NAME = "create_better_wrench_locks";
    private static final String KEY_LOCKED = "Locked";
    private static final String KEY_FACING = "Facing";
    private static final String KEY_POS = "Pos";
    private static final String KEY_DIR = "Dir";

    /** 已锁定的坐标 -> 锁定时的玩家水平朝向。未出现在表里即"未锁定"。 */
    private final Map<BlockPos, Direction> locked = new HashMap<>();

    public AssembleLockData() {
    }

    /** 取该维度的数据; 客户端或数据不可用时返回 null(调用方按"未锁定/NORTH"处理)。 */
    public static AssembleLockData get(Level level) {
        if (!(level instanceof ServerLevel serverLevel))
            return null;
        return serverLevel.getDataStorage()
            .computeIfAbsent(AssembleLockData::load, AssembleLockData::new, DATA_NAME);
    }

    public static AssembleLockData load(CompoundTag tag) {
        AssembleLockData data = new AssembleLockData();
        ListTag list = tag.getList(KEY_LOCKED, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos pos = BlockPos.of(entry.getLong(KEY_POS));
            Direction dir = Direction.byName(entry.getString(KEY_DIR));
            data.locked.put(pos, dir == null ? Direction.NORTH : dir);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, Direction> e : locked.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong(KEY_POS, e.getKey().asLong());
            entry.putString(KEY_DIR, e.getValue().getName());
            list.add(entry);
        }
        tag.put(KEY_LOCKED, list);
        return tag;
    }

    public boolean isLocked(BlockPos pos) {
        return locked.containsKey(pos);
    }

    /** 记录锁定与其基准朝向。 */
    public void lock(BlockPos pos, Direction facing) {
        Direction dir = facing != null && facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        locked.put(pos.immutable(), dir);
        setDirty();
    }

    /** 解锁: 直接移除条目(附件时代是写一个 false, 这里不留残条目)。 */
    public void unlock(BlockPos pos) {
        if (locked.remove(pos) != null)
            setDirty();
    }

    public Direction facing(BlockPos pos) {
        Direction dir = locked.get(pos);
        return dir == null ? Direction.NORTH : dir;
    }
}
