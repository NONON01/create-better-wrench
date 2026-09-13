package com.nonono.createbetterwrench.assemble;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.mojang.serialization.Codec;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * 「装配」模式的置物台"锁定"状态。
 *
 * <p>用 NeoForge 数据附件(AttachmentType)挂在方块实体上, 会随世界保存。锁定的置物台:
 * ①不会被右键把物品取走; ②可被玩家用对应物品右键, 按 Create 的 {@code create:sequenced_assembly}(序列装配)推进。</p>
 */
public final class AssembleLock {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BetterWrenchMod.MODID);

    /** 置物台是否锁定。默认 false。 */
    public static final Supplier<AttachmentType<Boolean>> LOCKED =
        ATTACHMENTS.register("depot_locked",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).build());

    private AssembleLock() {
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
    }
}
