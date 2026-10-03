package com.nonono.createbetterwrench.network;

import java.util.function.Supplier;

import java.util.ArrayList;
import java.util.List;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.config.WrenchConfig;
import com.nonono.createbetterwrench.connect.ConnectLogic;
import com.nonono.createbetterwrench.mode.ConnectCorner;
import com.nonono.createbetterwrench.permission.WrenchPermissions;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * 客户端到服务端: 请求在起点 S、若干拐点与终点 E 之间铺设传动结构。
 *
 * <p>载荷: start(起点) + corners(按序的拐点列表, 可为空=直线直达) + end(终点) +
 * cornerTypeName(拐角类型: 齿轮箱/大齿轮)。服务端用 {@link ConnectLogic#connect} 按
 * "每段边同轴=直线 / 同平面=自动一次拐弯 / 非平面=拒连"路由, 校验轴线/占位/材料后落块扣料;
 * 功能开关、建造权限、主手持扳手、拐点数量与节点区块加载依次在 {@link #handle} 里校验,
 * 任一不通过即拒绝返回(功能开关与拐点超限会给出提示, 其余静默返回)。</p>
 */
public record ConnectPayload(BlockPos start, List<BlockPos> corners, BlockPos end, String cornerTypeName)
    {

    public static int maxCorners() {
        return WrenchConfig.connectMaxCorners();
    }

    /**
     * 协议层的<b>固定</b>拐点上限 —— 与配置取值范围(`connect.max_corners` = 1..256)<b>同一上界</b>。
     *
     * <p>注意: 复审 A-18 —— 这里<b>绝不能</b>用 {@link #maxCorners()}。那个 getter 读的是"<b>本进程</b>的配置",
     * 而专用服务器上客户端读不到服务端配置、会回落到默认 32; 服务端却可能被管理员调成 8,
     * 于是<b>合法客户端</b>送 9 个以上拐点时, 解码层 `throw` 会引发 Netty 解码异常, 最终导致<b>玩家被踢下线</b>
     * (这正是审计 A-2 当初的症状, 只是触发条件从"固定 32"变成"配置漂移")。</p>
     *
     * <p>所以解码阶段<b>只做防 OOM 的固定硬闸</b>(任何合法客户端都不可能超过它); 真正的配置上限
     * 留到 {@link #handle} 里做业务校验并<b>友好拒绝</b>(聊天提示, 不抛异常)。</p>
     */
    public static final int PROTOCOL_MAX_CORNERS = 256;

;

    /** 客户端构造: 把拐角枚举转成协议里的名字。 */
    public static ConnectPayload create(BlockPos start, List<BlockPos> corners, BlockPos end,
                                        com.nonono.createbetterwrench.mode.ConnectCorner corner) {
        return new ConnectPayload(start, corners, end, corner == null ? "GEARBOX" : corner.name());
    }

    public static void encode(ConnectPayload p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.start());
        buf.writeInt(p.corners().size());
        for (BlockPos c : p.corners())
            buf.writeBlockPos(c);
        buf.writeBlockPos(p.end());
        buf.writeUtf(p.cornerTypeName());
    }

    public static ConnectPayload decode(FriendlyByteBuf buf) {
        BlockPos start = buf.readBlockPos();
        int n = Math.min(buf.readInt(), 4096);
        java.util.List<BlockPos> corners = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++)
            corners.add(buf.readBlockPos());
        BlockPos end = buf.readBlockPos();
        return new ConnectPayload(start, corners, end, buf.readUtf());
    }

    public static void handle(ConnectPayload p, Supplier<NetworkEvent.Context> ctx) {

        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null)
                return;

            // ===== 服务端校验(绝不信任客户端)=====
            // ⓪ 功能开关: 配置里把「连接」关闭后, 任何请求一律拒绝(改包客户端也绕不过)
            if (!WrenchConfig.connectEnabled()) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "msg." + BetterWrenchMod.MODID + ".feature_disabled"), true);
                return;
            }
            // ① 资格: 旁观者/无建造权限者一律拒绝;冒险模式下额外提示「当前是冒险模式」
            if (WrenchPermissions.rejectIfCannotBuild(sp))
                return;
            // ② 必须是主手持本模组的扳手
            // 注意: 自 2026-09-20 起扳手在副手时"只作普通扳手", 不参与本模组的模式功能
            if (!sp.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH.get()))
                return;
            // ③ 拐点数量上限(解码层只有固定硬闸; 这里按服务端自己的配置做业务校验, 结果是友好拒绝而不是掉线)
            if (p.corners.size() > maxCorners()) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "msg." + BetterWrenchMod.MODID + ".connect.too_many_corners", maxCorners()), true);
                return;
            }
            // ④ 2026-09-25: 原 `connect.max_end_distance`(终点距离上限)已按设计约定删除配置项,
            //    这条距离校验随之去掉 —— 终点位置仍受下面"区块已加载"+服务端上限约束。
            // ⑤ 所有节点所在区块必须已加载(避免被用来强制生成/加载区块)
            //    isLoaded: hasChunkAt 家族已被弃用(原版, 见 ConnectLogic#loadedCached 的说明) ——
            //    它比 hasChunkAt 多一条"超出建筑高度则返回 false": 站在世界顶端对着开阔空气选拐点时, 那个拐点可能
            //    落在建筑高度之外, 于是这里就会拦下(以前是后面的 plan() 用 UNLOADED 拦)。
            //    注意: 因此拦下时必须给出与 plan() 相同的提示, 否则玩家只看到红框、没有任何文字反馈(审计发现)。
            boolean nodesLoaded = sp.level().isLoaded(p.start) && sp.level().isLoaded(p.end);
            if (nodesLoaded)
                for (BlockPos c : p.corners)
                    if (!sp.level().isLoaded(c)) {
                        nodesLoaded = false;
                        break;
                    }
            if (!nodesLoaded) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "msg." + BetterWrenchMod.MODID + ".connect.unloaded"), true);
                return;
            }

            ConnectCorner cornerType = ConnectCorner.byName(p.cornerTypeName);

            ConnectLogic.Result result = ConnectLogic.connect(
                (net.minecraft.server.level.ServerLevel) sp.level(), sp, p.start, p.corners, p.end, cornerType);

            // 结果提示文案在语言文件: msg.<modid>.connect.<result 小写>。见 lang/*.json。
            String key = "msg." + BetterWrenchMod.MODID + ".connect."
                + result.name().toLowerCase(java.util.Locale.ROOT);
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(key), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
