package com.nonono.createbetterwrench.client;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.mode.WrenchMode;
import com.nonono.createbetterwrench.network.AssemblePayload;
import com.simibubi.create.content.logistics.depot.DepotBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端: <b>主手</b>持有扳手且处于<b>[加工] / [模组描述]</b> 模式时, 消费右键点击。
 *
 * <ul>
 *   <li><b>[加工]</b>: 对着置物台右键: 发包切换锁定状态; 对着<b>其它方块</b>右键: 只消费, 不做事。</li>
 *   <li><b>[模组描述]</b>: 右键一律消费。</li>
 * </ul>
 *
 * <p><b>为什么必须消费</b>: 不消费的话, 这次右键会正常发到服务端, 于是 Create 的扳手逻辑照常生效
 * (扭方块 / 拆方块 / 开界面) —— 也就是说这两个模式里右键还带着"扳手"语义, 这与模式设计冲突。</p>
 *
 * <p>消费 {@code MouseButton.Pre} 会让 {@code keyUse} 不被置为按下, MC 也就不会再连发右键, 正好符合需要。</p>
 *
 * <p><b>只有主手握扳手才消费(审计 A-9)</b>: 扳手在<b>副手</b>、主手拿的是加工材料时, 本处理器
 * <b>不 cancel</b> —— 让这次右键照常发到服务端, 由 {@code AssembleInteractionHandler} 在已锁定的
 * 置物台上执行加工。旧实现只要"任一只手"拿扳手就消费且不发包, 于是副手扳手 + 主手材料
 * 完全无法加工(服务端本就允许这种组合, 见 {@code AssemblePayload} 对主手物品的校验)。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID, value = Dist.CLIENT)
public final class AssembleSelectionHandler {

    private AssembleSelectionHandler() {
    }

    /** 主手是否持有本模组扳手(只有这种情况才消费右键)。 */
    private static boolean mainHandWrench(Minecraft mc) {
        return mc.player != null && mc.player.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH);
    }

    private static boolean modeActive() {
        WrenchMode mode = WrenchModeSwitcher.current;
        return mode == WrenchMode.ASSEMBLE || mode == WrenchMode.COMING_SOON;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.level == null)
            return;
        if (event.getAction() != 1 || event.getButton() != 1) // 右键按下
            return;
        // 副手扳手 + 主手材料: 放行, 让服务端在已锁定的置物台上施加材料
        if (!mainHandWrench(mc))
            return;
        if (!modeActive())
            return;
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK)
            return;

        // 2026-09-22(设计约定): 潜行 + 右键置物台 = 从锁定台面上取回物品, 由服务端
        //    ({@code AssembleInteractionHandler}) 处理, 因此这里必须放行, 否则那次点击被消费、服务端永远收不到。
        //    刻意只对"潜行 + 目标是置物台"放行(而不是对所有潜行放行): 这样加工模式下
        //    "潜行 + 右键其它方块"仍然是原来的"消费点击", 不会突然变成 Create 的潜行扳手语义(拆方块)。
        if (mc.player.isShiftKeyDown() && WrenchModeSwitcher.current == WrenchMode.ASSEMBLE) {
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            if (mc.level.getBlockState(pos).getBlock() instanceof DepotBlock)
                return;
        }

        // [加工] 且目标是置物台 -> 请求切换锁定
        if (WrenchModeSwitcher.current == WrenchMode.ASSEMBLE) {
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            if (mc.level.getBlockState(pos).getBlock() instanceof DepotBlock) {
                // 功能被配置关掉: 只提示功能未启用, 不发包(服务端也会再拦一次)
                if (!ClientFeatureGate.blockIfDisabled(WrenchMode.ASSEMBLE)) {
                    ClientPacketListener conn = mc.getConnection();
                    if (conn != null)
                        PacketDistributor.sendToServer(new AssemblePayload(pos));
                }
            }
        }

        // 主手持扳手时, 无论目标是哪种方块都消费本次点击, 避免右键落到扳手语义上
        event.setCanceled(true);
    }
}
