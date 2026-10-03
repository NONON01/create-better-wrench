package com.nonono.createbetterwrench.assemble;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.mojang.serialization.Codec;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * 「装配」模式的置物台锁定状态。
 *
 * <p>用 NeoForge 数据附件(AttachmentType)挂在方块实体上, 会随世界保存。锁定的置物台:
 * ①右键不会把物品取走; ②可被玩家用对应物品右键, 按 Create 的 {@code create:sequenced_assembly}(序列装配)推进。</p>
 */
public final class AssembleLock {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BetterWrenchMod.MODID);

    /** 置物台是否锁定。默认 false。 */
    public static final Supplier<AttachmentType<Boolean>> LOCKED =
        ATTACHMENTS.register("depot_locked",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).build());

    /**
     * 锁定时的<b>玩家水平朝向</b>(2026-10-03 新增)。两个料堆的位置以它为基准:
     * 原料堆在玩家右手侧的前角, 半成品堆在左手侧的前角 —— 锁定后玩家转身不再改变布局。
     * 默认 NORTH, 于是老存档(没有这个附件)也有一致的布局。
     */
    public static final Supplier<AttachmentType<Direction>> LOCK_FACING =
        ATTACHMENTS.register("depot_lock_facing",
            () -> AttachmentType.builder(() -> Direction.NORTH).serialize(Direction.CODEC).build());

    private AssembleLock() {
    }

    /** 该机器的锁定朝向; 未设置时为 NORTH。 */
    public static Direction facing(BlockEntity be) {
        if (be == null)
            return Direction.NORTH;
        Direction v = be.getData(LOCK_FACING.get());
        return v == null ? Direction.NORTH : v;
    }

    /** 记录锁定时的玩家朝向(由服务端在锁定请求里写入, 不信任客户端传来的方向)。 */
    public static void setFacing(BlockEntity be, Direction facing) {
        if (be == null || facing == null)
            return;
        be.setData(LOCK_FACING.get(), facing.getAxis().isHorizontal() ? facing : Direction.NORTH);
        be.setChanged();
    }

    public static boolean isLocked(BlockEntity be) {
        if (be == null)
            return false;
        Boolean v = be.getData(LOCKED.get());
        return v != null && v;
    }

    public static void setLocked(BlockEntity be, boolean locked) {
        if (be == null)
            return;
        be.setData(LOCKED.get(), locked);
        be.setChanged();
        // 锁定的置物台在下方是灵魂底座时要冒灵魂火焰粒子, 因此上锁/解锁时同步登记表(见 DepotSoulFlames)。
        // 只有置物台需要登记: 工作盆没有"台面下方灵魂底座"这套玩法(2026-10-03 起工作盆也可锁定)。
        if (be instanceof com.simibubi.create.content.logistics.depot.DepotBlockEntity)
            DepotSoulFlames.setTracked(be.getLevel(), be.getBlockPos(), locked);
    }
}
