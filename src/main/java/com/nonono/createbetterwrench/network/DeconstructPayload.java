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
 * 客户端到服务端: 请求对某区域执行一次「拆除」。
 *
 * <p>载荷: 两个对角的 BlockPos + 拆除范围档名(String, 解码后转 {@code DeconstructScope})。
 * 服务端依次校验功能开关、建造权限、主手持扳手、选区尺寸与两角区块加载, 任一不通过即拒绝返回
 * (功能开关被关闭、选区过大以及冒险模式下无建造权限这三种情形会给出提示, 其余静默返回);
 * 通过后把范围档按配置的强制档覆盖, 再交给分帧执行器拆除并入背包。详见 {@link #handle}。</p>
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

    // 单轴最大边长改为读配置: config/WrenchConfig 的 deconstruct.max_edge(默认 64)。
    // 客户端 client/DeconstructSelectionHandler 读的是同一份配置, 因此不会再出现"两份常量不同步"的问题。

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp))
                return;

            // ===== 服务端校验(绝不信任客户端)=====
            // ⓪ 功能开关: 配置里把「拆除」关闭后, 任何请求一律拒绝(改包客户端也绕不过)
            if (!WrenchConfig.deconstructEnabled()) {
                sp.displayClientMessage(Component.translatable(
                    "msg." + BetterWrenchMod.MODID + ".feature_disabled"), true);
                return;
            }
            // ① 资格: 旁观者/无建造权限者一律拒绝;冒险模式下额外提示「当前是冒险模式」
            if (WrenchPermissions.rejectIfCannotBuild(sp))
                return;
            // ② 必须是主手持本模组的扳手
            // 注意: 自 2026-09-20 起扳手在副手时"只作普通扳手", 不参与本模组的模式功能
            if (!sp.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH))
                return;
            // ③ 选区尺寸上限: 每轴 ≤ 配置值(默认 64) —— 挡住"改包发超大区域导致服务端长时间遍历"的卡服路径
            int maxEdge = WrenchConfig.deconstructMaxEdge();
            int dx = Math.abs(cornerA.getX() - cornerB.getX()) + 1;
            int dy = Math.abs(cornerA.getY() - cornerB.getY()) + 1;
            int dz = Math.abs(cornerA.getZ() - cornerB.getZ()) + 1;
            if (dx > maxEdge || dy > maxEdge || dz > maxEdge) {
                // 设计约定: 选区过大时明确提示(客户端那边同时会把选区框画成红色)
                sp.displayClientMessage(
                    Component.translatable("msg." + BetterWrenchMod.MODID + ".deconstruct.too_large"), true);
                return;
            }
            // isLoaded: hasChunkAt 家族已被弃用(原版, 见 ConnectLogic#loadedCached 的说明) ——
            // 它多一条"超出建筑高度则返回 false", 所以越界的角点现在会被整单拦下(以前会照跑并拆掉高度内的那部分);
            // 合法客户端不可能产生越界角点(角点只来自 {@code mc.hitResult} 的方块命中), 故这是纯粹的加固。
            if (!sp.level().isLoaded(cornerA) || !sp.level().isLoaded(cornerB))
                return;

            DeconstructScope scope;
            try {
                scope = DeconstructScope.valueOf(scopeName);
            } catch (Exception e) {
                scope = DeconstructScope.ALL;
            }
            // ⓪b 配置里"不允许破坏机械动力/红石方块"时, 服务端强制用被锁定的那一档覆盖客户端发来的档
            //     (设计约定, 2026-09-25: 不允许机械动力时只能"仅红石"; 不允许红石时只能"仅机械动力")
            scope = WrenchConfig.effectiveScope(scope);
            // 交给分帧执行器: 小选区当场完成;大选区按固定片大小(每服务端刻一片,
            // 片大小见配置 `deconstruct.blocks_per_tick`, 默认 1024 格)分帧, 避免卡服。
            // 拆除数量由执行器统一回报(actionbar), 文案见 msg.<modid>.deconstruct.count / .batching
            DeconstructJob.start((net.minecraft.server.level.ServerLevel) sp.level(),
                sp, cornerA, cornerB, scope);
        });
    }
}
