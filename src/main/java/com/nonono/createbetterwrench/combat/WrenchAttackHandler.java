package com.nonono.createbetterwrench.combat;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * 「万能扳手」攻击彩蛋: 取消攻击命中后实体的受伤冷却(无敌帧)。
 *
 * <p><b>仅在战斗模式生效</b>(彩蛋的一部分): 只有玩家在「模组描述」里切到战斗模式且手持扳手时,
 * 才在 vanilla hurt 结算前把目标的 invulnerableTime/hurtTime 清零, 使每次攻击都立刻完整结算。
 * 无敌帧清零是服务端权威行为, 故这里只在服务端分支处理。</p>
 *
 * <p>⚠️ <b>为什么放在 {@code combat/} 而不是 {@code client/}</b>(2026-09-20 移动):
 * 本类必须在**两端**都注册({@code @EventBusSubscriber} 未指定 {@code value = Dist.CLIENT}),
 * 因为它做的是服务端结算。它此前放在 {@code client/} 包里 —— 虽然当时**只引用通用类**、运行无碍,
 * 但那是个**雷**: 任何人顺手在这里加一行 {@code net.minecraft.client.*} 的 import,
 * 专用服务器就会在注册时 {@code NoClassDefFoundError}。
 * 移到通用包后, 这个隐患从结构上消失(见 docs/07 §6 B-1)。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WrenchAttackHandler {

    private WrenchAttackHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide)
            return;
        // 彩蛋: 未开战斗模式则保持原版受伤冷却
        if (!WrenchCombat.getServer(player.getUUID()))
            return;
        boolean mainHand = player.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH);
        boolean offHand = player.getOffhandItem().is(BetterWrenchMod.BETTER_WRENCH);
        if (!mainHand && !offHand)
            return;

        Entity target = event.getTarget();
        if (target == null)
            return;
        // 取消受伤冷却(无敌帧), 使每次攻击都完整结算
        target.invulnerableTime = 0;
        if (target instanceof LivingEntity living)
            living.hurtTime = 0;
    }
}
