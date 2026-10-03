package com.nonono.createbetterwrench.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * 「万能扳手」战斗模式(战斗加成, 可选)的运行时加成。
 *
 * <p>扳手原始的 +5 伤害 / +20 攻速原本烘焙在物品属性里(永远生效); 现改为<b>运行时条件加成</b>:
 * 只有在「模组描述」工具里用 Ctrl 切到<b>战斗模式</b>且手持扳手时, 才把两个瞬态修饰符加到玩家的
 * ATTACK_DAMAGE / ATTACK_SPEED 上; 正常模式下移除。</p>
 *
 * <p>服务端按玩家 UUID 记录开关(由客户端发包同步), 客户端使用本地开关(见 client/WrenchCombatClient)。</p>
 */
public final class WrenchCombat {

    /** 稳定的修饰符标识: 1.20.1 的 AttributeModifier 以 UUID 为主键(1.21 才改成 ResourceLocation)。 */
    private static final UUID DAMAGE_UUID = UUID.fromString("6d1c2f2e-1c2b-4f3a-9b1e-0c2f5a7d1001");
    private static final UUID SPEED_UUID = UUID.fromString("6d1c2f2e-1c2b-4f3a-9b1e-0c2f5a7d1002");

    public static final AttributeModifier DAMAGE =
        new AttributeModifier(DAMAGE_UUID, "cbw_combat_attack_damage", 5.0, AttributeModifier.Operation.ADDITION);
    public static final AttributeModifier SPEED =
        new AttributeModifier(SPEED_UUID, "cbw_combat_attack_speed", 20.0, AttributeModifier.Operation.ADDITION);

    /** 服务端: 每个玩家的战斗模式开关(由客户端发包同步)。 */
    private static final Map<UUID, Boolean> SERVER = new HashMap<>();

    private WrenchCombat() {
    }

    public static void setServer(UUID id, boolean combat) {
        SERVER.put(id, combat);
    }

    public static boolean getServer(UUID id) {
        return SERVER.getOrDefault(id, false);
    }

    public static void clearServer(UUID id) {
        SERVER.remove(id);
    }

    /**
     * 玩家<b>主手</b>是否持有万能扳手。
     *
     * <p>2026-09-20(设计约定): 扳手在<b>副手</b>时一律"只作普通扳手" —— 不显示 HUD、模式功能不生效,
     * 战斗加成也随之不生效, 因此这里只检查主手。</p>
     */
    public static boolean holdsWrench(Player player) {
        return player.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH.get());
    }

    /** 按 enabled 应用/移除战斗加成(幂等; 仅状态变化时才动属性, 避免每 tick 抖动)。 */
    public static void apply(Player player, boolean enabled) {
        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            boolean has = damage.getModifier(DAMAGE_UUID) != null;
            if (enabled && !has)
                damage.addTransientModifier(DAMAGE);
            else if (!enabled && has)
                damage.removeModifier(DAMAGE_UUID);
        }
        AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speed != null) {
            boolean has = speed.getModifier(SPEED_UUID) != null;
            if (enabled && !has)
                speed.addTransientModifier(SPEED);
            else if (!enabled && has)
                speed.removeModifier(SPEED_UUID);
        }
    }

    /** 服务端每 tick: 依据该玩家开关 + 是否持扳手应用加成; 若开关开着但已失去权限则强制关闭。 */
    public static void tickServer(Player player) {
        boolean on = holdsWrench(player) && getServer(player.getUUID());
        if (on && player instanceof net.minecraft.server.level.ServerPlayer sp
            && !com.nonono.createbetterwrench.permission.WrenchPermissions.canUseCombatMode(sp)) {
            on = false;
            setServer(player.getUUID(), false);
        }
        apply(player, on);
    }
}
