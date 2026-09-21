package com.nonono.createbetterwrench.network;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.config.WrenchConfig;
import com.nonono.createbetterwrench.deconstruct.DeconstructJob;
import com.nonono.createbetterwrench.deconstruct.DeconstructLogic;
import com.nonono.createbetterwrench.mode.DeconstructScope;
import com.nonono.createbetterwrench.permission.WrenchPermissions;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端: 请求对某区域执行一次「拆除」。
 *
 * <p>载荷: 两角 BlockPos + 拆除范围档名(String, 解码后转 DeconstructScope)。
 * 服务端收到后遍历区域拆除并入背包。</p>
 */
public record DeconstructPayload(BlockPos cornerA, BlockPos cornerB, String scopeName)
    implements CustomPacketPayload {

    public static final Type<DeconstructPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "deconstruct"));

    public static final StreamCodec<ByteBuf, DeconstructPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, DeconstructPayload::cornerA,
        BlockPos.STREAM_CODEC, DeconstructPayload::cornerB,
        ByteBufCodecs.STRING_UTF8, DeconstructPayload::scopeName,
        DeconstructPayload::new
    );

    public static DeconstructPayload create(BlockPos a, BlockPos b, DeconstructScope scope) {
        return new DeconstructPayload(a, b, scope.name());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // 单轴最大边长改为**读配置**: config/WrenchConfig → deconstruct.max_edge(默认 64)。
    // 客户端 client/DeconstructSelectionHandler 读的是同一份配置, 因此不会再出现"两份常量不同步"的问题。

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp))
                return;

            // ===== 服务端校验(绝不信任客户端)=====
            // ① 资格: 旁观者/无建造权限者一律拒绝;冒险模式下额外提示「当前是冒险模式」
            if (WrenchPermissions.rejectIfCannotBuild(sp))
                return;
            // ② 必须**主手**手持本模组的扳手
            // ⚠️ 2026-09-20(用户约定): 扳手在副手时"只作普通扳手", 不参与本模组的模式功能
            if (!sp.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH))
                return;
            // ③ 选区尺寸上限: 每轴 ≤ 配置值(默认 64) —— 挡住"改包发超大区域 ⇒ 服务端长时间遍历"的卡服路径
            int maxEdge = WrenchConfig.deconstructMaxEdge();
            int dx = Math.abs(cornerA.getX() - cornerB.getX()) + 1;
            int dy = Math.abs(cornerA.getY() - cornerB.getY()) + 1;
            int dz = Math.abs(cornerA.getZ() - cornerB.getZ()) + 1;
            if (dx > maxEdge || dy > maxEdge || dz > maxEdge) {
                // 用户要求: 选区过大时明确提示(客户端那边同时会把选区框画成红色)
                sp.displayClientMessage(
                    Component.translatable("msg." + BetterWrenchMod.MODID + ".deconstruct.too_large"), true);
                return;
            }
            if (!sp.level().hasChunkAt(cornerA) || !sp.level().hasChunkAt(cornerB))
                return;

            DeconstructScope scope;
            try {
                scope = DeconstructScope.valueOf(scopeName);
            } catch (Exception e) {
                scope = DeconstructScope.ALL;
            }
            // 交给分帧执行器: 小选区当场完成;大选区按**固定片大小**(每服务端刻最多 1024 格)分帧, 避免卡服。
            // 拆除数量由执行器统一回报(actionbar), 文案见 msg.<modid>.deconstruct.count / .batching
            DeconstructJob.start((net.minecraft.server.level.ServerLevel) sp.level(),
                sp, cornerA, cornerB, scope);
        });
    }
}
