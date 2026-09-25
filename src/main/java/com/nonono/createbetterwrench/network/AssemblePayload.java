package com.nonono.createbetterwrench.network;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.config.WrenchConfig;
import com.nonono.createbetterwrench.assemble.AssembleLock;
import com.nonono.createbetterwrench.assemble.AssembleLogic;
import com.nonono.createbetterwrench.permission.WrenchPermissions;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
            // ⓪ 功能开关: 配置里把「加工」关掉后, 连"锁定/解锁置物台"也一并拒绝
            //    (用户 2026-09-25: 关掉的功能要"阻止玩家使用", 并且每次尝试都提示)
            if (!WrenchConfig.processEnabled()) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "msg." + BetterWrenchMod.MODID + ".feature_disabled"), true);
                return;
            }
            // ① 资格: 旁观者/无建造权限者一律拒绝;冒险模式下额外提示「当前是冒险模式」
            if (WrenchPermissions.rejectIfCannotBuild(sp))
                return;
            // ② 必须**主手**手持本模组的扳手(锁定/解锁是扳手模式下的行为)
            // ⚠️ 2026-09-20(用户约定): 扳手在副手时"只作普通扳手", 不参与本模组的模式功能
            if (!sp.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH))
                return;
            // ③ 距离: 必须在可交互范围内
            if (sp.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                > MAX_INTERACTION_DISTANCE_SQR)
                return;
            // ④ 区块已加载 + 目标确实是置物台
            if (!sp.level().isLoaded(pos))
                return;
            if (!(sp.level().getBlockEntity(pos) instanceof DepotBlockEntity depot))
                return;
            boolean now = !AssembleLock.isLocked(depot);
            AssembleLock.setLocked(depot, now);
            if (!now) {
                // 解锁: 把台面上的物品 + 两个料堆(含被弹出的成品)全部**返还到玩家背包**
                // (用户 2026-09-17 要求; 装不下的会由原版逻辑掉在玩家脚下, 不会丢)
                AssembleLogic.returnHeldAndPiles((ServerLevel) sp.level(), pos, depot, sp);
            } else {
                // 刚锁定: 台面若空而原料堆还有货, 自动续一个上去(「自动续料」)
                AssembleLogic.refillIfEmpty(sp.level(), pos, depot);
            }
            sp.displayClientMessage(Component.translatable("msg." + BetterWrenchMod.MODID
                + (now ? ".assemble.locked" : ".assemble.unlocked")), true);
        });
    }
}
