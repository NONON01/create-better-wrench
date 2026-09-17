package com.nonono.createbetterwrench.network;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.assemble.AssembleLock;
import com.nonono.createbetterwrench.assemble.DepotPiles;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端: 在「装配」模式下右击置物台, 请求**切换锁定状态**(锁定/解锁)。
 */
public record AssemblePayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<AssemblePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "assemble_toggle"));

    public static final StreamCodec<ByteBuf, AssemblePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, AssemblePayload::pos,
        AssemblePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 允许的最大交互距离(格)的**平方**。
     *
     * <p>取 8 格(8²=64): 比原版 4.5 格的正常触及范围宽松, 但足以挡住"改包远程操作"。
     * 之所以不直接用原版触及距离, 是为了避免高延迟/创造模式/移动中的正常操作被误判。</p>
     */
    private static final double MAX_INTERACTION_DISTANCE_SQR = 64.0;

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp))
                return;

            // ===== 服务端校验(绝不信任客户端; 审计发现: 原本这里一条校验都没有)=====
            // 原本只查了"区块已加载 + 目标是置物台", 于是任何人改包就能
            // **远程上锁/解锁别人的置物台**(解锁后原料堆变成可抢的掉落物; 上锁后对方彻底不可交互)。
            // 照 ConnectPayload.handle 里那套校验抄一份:
            // ① 资格: 旁观者/无建造权限者一律拒绝
            if (sp.isSpectator() || !sp.mayBuild())
                return;
            // ② 必须手持本模组的扳手(锁定/解锁是扳手模式下的行为)
            if (!sp.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH)
                && !sp.getOffhandItem().is(BetterWrenchMod.BETTER_WRENCH))
                return;
            // ③ 距离: 必须在可交互范围内
            if (sp.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                > MAX_INTERACTION_DISTANCE_SQR)
                return;
            // ④ 区块已加载 + 目标确实是置物台
            if (!sp.level().hasChunkAt(pos))
                return;
            if (!(sp.level().getBlockEntity(pos) instanceof DepotBlockEntity depot))
                return;
            boolean now = !AssembleLock.isLocked(depot);
            AssembleLock.setLocked(depot, now);
            if (!now) {
                // 解锁: 把原料堆/成品堆都释放成普通掉落物, 让玩家把东西收回去
                DepotPiles.releaseAll(sp.level(), pos);
            }
            sp.displayClientMessage(Component.translatable("msg." + BetterWrenchMod.MODID
                + (now ? ".assemble.locked" : ".assemble.unlocked")), true);
        });
    }
}
