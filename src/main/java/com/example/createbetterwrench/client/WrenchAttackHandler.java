package com.example.createbetterwrench.client;

import com.example.createbetterwrench.BetterWrenchMod;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * 「万能扳手」攻击增强: 取消攻击命中后实体的受伤冷却(无敌帧)。
 *
 * <p>当玩家用万能扳手攻击时, 在 vanilla hurt 结算前把目标的 invulnerableTime/hurtTime 清零,
 * 使每次攻击都能立刻造成完整伤害(不像普通武器那样受受击冷却限制)。</p>
 */
@EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WrenchAttackHandler {

    private WrenchAttackHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player == null)
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
