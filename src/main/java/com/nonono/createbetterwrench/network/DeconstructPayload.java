package com.nonono.createbetterwrench.network;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.deconstruct.DeconstructLogic;
import com.nonono.createbetterwrench.mode.DeconstructScope;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
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

    /** 单个轴向上的最大边长(用户指定: 选区最大 128×128×128)。 */
    private static final int MAX_EDGE = 128;

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp))
                return;

            // ===== 服务端校验(绝不信任客户端)=====
            // ① 资格: 旁观者/无建造权限者一律拒绝(对照 Create 的 WrenchItem.useOn 会先查 mayBuild)
            if (sp.isSpectator() || !sp.mayBuild())
                return;
            // ② 必须手持本模组的扳手
            if (!sp.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH)
                && !sp.getOffhandItem().is(BetterWrenchMod.BETTER_WRENCH))
                return;
            // ③ 选区尺寸上限: 每轴 ≤ 128(挡住"改包发超大区域 ⇒ 服务端死循环"的卡服路径)
            int dx = Math.abs(cornerA.getX() - cornerB.getX()) + 1;
            int dy = Math.abs(cornerA.getY() - cornerB.getY()) + 1;
            int dz = Math.abs(cornerA.getZ() - cornerB.getZ()) + 1;
            if (dx > MAX_EDGE || dy > MAX_EDGE || dz > MAX_EDGE)
                return;
            if (!sp.level().hasChunkAt(cornerA) || !sp.level().hasChunkAt(cornerB))
                return;

            DeconstructScope scope;
            try {
                scope = DeconstructScope.valueOf(scopeName);
            } catch (Exception e) {
                scope = DeconstructScope.ALL;
            }
            int removed = DeconstructLogic.deconstructRegion(
                (net.minecraft.server.level.ServerLevel) sp.level(), cornerA, cornerB, scope, sp);
            // 始终提示(含 0), 显示在 actionbar; 文案在语言文件 msg.<modid>.deconstruct.count
            sp.displayClientMessage(net.minecraft.network.chat.Component
                .translatable("msg." + BetterWrenchMod.MODID + ".deconstruct.count", removed), true);
        });
    }
}
