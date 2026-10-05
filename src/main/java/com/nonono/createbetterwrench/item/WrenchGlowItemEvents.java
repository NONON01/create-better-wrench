package com.nonono.createbetterwrench.item;

import java.util.concurrent.atomic.AtomicBoolean;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.AllItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * 实验(分支 exp/wrench-glow): 铁砧升级 —— 普通扳手 + 一颗材料变成材质扳手。
 *
 * <pre>
 *   扳手 + 光辉石(create:refined_radiance) -> 光辉石扳手: 写 create_better_wrench:glow
 *   扳手 + 暗影钢(create:shadow_steel)     -> 暗影钢扳手: 写 create_better_wrench:shadow_steel
 * </pre>
 *
 * <p>两个标记互相独立, 同一次升级只写自己那一个, 因此可以在已升级的扳手上再叠加另一种材料
 * (光辉石扳手 + 暗影钢, 或暗影钢扳手 + 光辉石), 两种顺序都会得到"两者都有"的结果。名字颜色按组合刷新:
 * 两者都有 = {@code Rarity.EPIC}(紫色, 与原版附魔金苹果同级), 只有一种 = {@code Rarity.UNCOMMON}(黄色, 与材料
 * 同色), 都没有 = 移除该组件(回到物品默认的白色)。外发光只由光辉石标记驱动, 因此暗影钢单独存在时不发光。</p>
 *
 * <p><b>入口与判定顺序</b>(NeoForge 21.1.249 源码 {@code CommonHooks#onAnvilChange}): 由
 * {@code AnvilMenu#createResult} 触发 {@link AnvilUpdateEvent} 后, 事件被取消则清空产物、花费置 0;
 * 否则若 {@code getOutput()} 非空, 就用它作为产物、用 {@code getCost()} 设置等级花费、
 * 用 {@code getMaterialCost()} 设置右槽消耗数量, 并跳过原版合成逻辑。因此本监听只设置产物与花费,
 * <b>不取消事件</b>(取消会把产物清空)。</p>
 *
 * <p><b>等级不足</b>由原版处理: {@code AnvilMenu#mayPickup} 要求
 * {@code player.hasInfiniteMaterials() || player.experienceLevel >= cost} 且 {@code cost > 0},
 * 不满足时产物取不走; 取走时 {@code onTake} 按 cost 扣级。因此这里把 cost 设为 1 级(不能被设为 0, 否则
 * 产物永远取不走)。</p>
 */
public final class WrenchGlowItemEvents {

    /** 等级花费(级); 两类升级都用 1 级。 */
    private static final long COST_LEVELS = 1L;

    /** 右槽消耗数量(颗)。 */
    private static final int MATERIAL_COST = 1;

    /** 两类升级各自首次成功时打印一次结果组件, 便于实机核对。 */
    private static final AtomicBoolean LOGGED_RADIANCE = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_SHADOW_STEEL = new AtomicBoolean();

    private WrenchGlowItemEvents() {
    }

    /** GAME 总线监听: 两类铁砧升级(各花费 1 级、消耗 1 颗材料)。 */
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || !left.is(BetterWrenchMod.BETTER_WRENCH.get()))
            return;

        boolean radiance = right.is(AllItems.REFINED_RADIANCE.get());
        boolean shadowSteel = right.is(AllItems.SHADOW_STEEL.get());
        if (!radiance && !shadowSteel)
            return;
        // 已带同一种标记就不再处理(交回原版逻辑), 因此同一材料不会重复叠加; 另一种标记不受影响, 可在之后叠加。
        if (radiance ? WrenchGlowComponent.isGlowing(left) : WrenchShadowSteelComponent.isShadowSteel(left))
            return;

        ItemStack output = left.copyWithCount(1);
        if (radiance)
            WrenchGlowComponent.applyRadiance(output);
        else
            WrenchShadowSteelComponent.applyShadowSteel(output);
        refreshRarity(output);

        event.setOutput(output);
        event.setCost(COST_LEVELS);
        event.setMaterialCost(MATERIAL_COST);
        logOnce(radiance, output);
    }

    /**
     * 按两个标记的组合刷新名字颜色。
     *
     * <p>1.21.1 的物品名颜色来自 {@code minecraft:rarity} 组件({@code ItemStack#getTooltipLines} 用
     * {@code getRarity().getStyleModifier()} 给名字上色), 因此这里直接写组件: 两者都有 = EPIC,
     * 只有一种 = UNCOMMON(光辉石与暗影钢在各自模组里的稀有度都是 UNCOMMON, 即黄色), 都没有 = 移除组件。</p>
     */
    private static void refreshRarity(ItemStack stack) {
        boolean radiance = WrenchGlowComponent.isGlowing(stack);
        boolean shadowSteel = WrenchShadowSteelComponent.isShadowSteel(stack);
        if (radiance && shadowSteel)
            stack.set(DataComponents.RARITY, Rarity.EPIC);
        else if (radiance || shadowSteel)
            stack.set(DataComponents.RARITY, Rarity.UNCOMMON);
        else
            stack.remove(DataComponents.RARITY);
    }

    /** 每类升级首次成功时打印一次结果组件。 */
    private static void logOnce(boolean radiance, ItemStack output) {
        AtomicBoolean flag = radiance ? LOGGED_RADIANCE : LOGGED_SHADOW_STEEL;
        if (!flag.compareAndSet(false, true))
            return;
        BetterWrenchMod.LOGGER.info(
            "wrench glow 铁砧: 普通扳手 + {} -> {}; glow={}, shadowSteel={}, rarity={}, foil={}, cost={}, materialCost={}",
            radiance ? "光辉石" : "暗影钢", radiance ? "光辉石扳手" : "暗影钢扳手",
            WrenchGlowComponent.componentValue(output), WrenchShadowSteelComponent.componentValue(output),
            output.get(DataComponents.RARITY), output.hasFoil(), COST_LEVELS, MATERIAL_COST);
    }
}
