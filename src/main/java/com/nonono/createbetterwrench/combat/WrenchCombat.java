package com.nonono.createbetterwrench.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * 「万能扳手」战斗模式(彩蛋)的运行时加成。
 *
 * <p>扳手原始的 +5 伤害 / +20 攻速原本烘焙在物品属性里(永远生效); 现改为**运行时条件加成**:
 * 只有在「模组描述」工具里用 Ctrl 切到**战斗模式**且手持扳手时, 才把两个瞬态修饰符加到玩家的
 * ATTACK_DAMAGE / ATTACK_SPEED 上; 正常模式下移除。</p>
 *
 * <p>服务端按玩家 UUID 记录开关(由客户端发包同步), 客户端使用本地开关(见 client/WrenchCombatClient)。</p>
 */
public final class WrenchCombat {

    public static final ResourceLocation DAMAGE_ID =
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "combat_attack_damage");
    public static final ResourceLocation SPEED_ID =
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "combat_attack_speed");

    private static final AttributeModifier DAMAGE =
        new AttributeModifier(DAMAGE_ID, 5.0, AttributeModifier.Operation.ADD_VALUE);
    private static final AttributeModifier SPEED =
        new AttributeModifier(SPEED_ID, 20.0, AttributeModifier.Operation.ADD_VALUE);

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

    /** 玩家(主手或副手)是否持有万能扳手。 */
    public static boolean holdsWrench(Player player) {
        return player.getMainHandItem().is(BetterWrenchMod.BETTER_WRENCH)
            || player.getOffhandItem().is(BetterWrenchMod.BETTER_WRENCH);
    }

    /** 按 enabled 应用/移除战斗加成(幂等; 仅状态变化时才动属性, 避免每 tick 抖动)。 */
    public static void apply(Player player, boolean enabled) {
        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            boolean has = damage.hasModifier(DAMAGE_ID);
            if (enabled && !has)
                damage.addOrUpdateTransientModifier(DAMAGE);
            else if (!enabled && has)
                damage.removeModifier(DAMAGE_ID);
        }
        AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speed != null) {
            boolean has = speed.hasModifier(SPEED_ID);
            if (enabled && !has)
                speed.addOrUpdateTransientModifier(SPEED);
            else if (!enabled && has)
                speed.removeModifier(SPEED_ID);
        }
    }

    /** 服务端每 tick: 依据该玩家开关 + 是否持扳手应用加成; 若开关开着但已失去权限则强制关闭。 */
    public static void tickServer(Player player) {
        boolean on = holdsWrench(player) && getServer(player.getUUID());
        if (on && player instanceof net.minecraft.server.level.ServerPlayer sp
            && !com.nonono.createbetterwrench.permission.WrenchPermissions.canUseBattleMode(sp)) {
            on = false;
            setServer(player.getUUID(), false);
        }
        apply(player, on);
    }
}
