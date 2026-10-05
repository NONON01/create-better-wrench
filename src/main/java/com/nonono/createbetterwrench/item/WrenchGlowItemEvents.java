package com.nonono.createbetterwrench.item;

import java.util.concurrent.atomic.AtomicBoolean;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.AllItems;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AnvilUpdateEvent;

/**
 * 铁砧升级 —— 普通扳手 + 一颗材料变成材质扳手(1.20.1 版, 与 1.21.1 行为一致)。
 *
 * <pre>
 *   扳手 + 光辉石(create:refined_radiance) -> 光辉石扳手: 写 NBT create_better_wrench:glow
 *   扳手 + 暗影钢(create:shadow_steel)     -> 暗影钢扳手: 写 NBT create_better_wrench:shadow_steel
 * </pre>
 *
 * <p>两个标记互相独立, 同一次升级只写自己那一个, 因此可以在已升级的扳手上再叠加另一种材料
 * (光辉石扳手 + 暗影钢, 或暗影钢扳手 + 光辉石), 两种顺序都会得到"两者都有"的结果。名字颜色按组合决定:
 * 两者都有 = {@code Rarity.EPIC}(紫色, 与原版附魔金苹果同级), 只有一种 = {@code Rarity.UNCOMMON}(黄色,
 * 与材料同色), 都没有 = 物品默认的白色。外发光只由光辉石标记驱动, 因此暗影钢单独存在时不发光。</p>
 *
 * <p><b>与 1.21.1 的唯一实现差异</b>: 1.21.1 在升级时把 {@code minecraft:rarity} 组件写进产物;
 * 1.20.1 没有 rarity 组件, 稀有度由 {@code Item#getRarity(ItemStack)} 决定, 因此这里不写颜色, 改由
 * {@link BetterWrenchItem#getRarity(ItemStack)} 在读的时候按两个标记现算 —— 对外表现相同。</p>
 *
 * <p><b>入口与判定顺序</b>(Forge 1.20.1 {@code AnvilMenu#createResult} 触发 {@link AnvilUpdateEvent}):
 * 事件被取消则清空产物、花费置 0;否则若 {@code getOutput()} 非空, 就用它作为产物、用 {@code getCost()}
 * 设置等级花费、用 {@code getMaterialCost()} 设置右槽消耗数量, 并跳过原版合成逻辑。因此本监听只设置产物与
 * 花费, <b>不取消事件</b>(取消会把产物清空)。</p>
 *
 * <p><b>等级不足</b>由原版处理: {@code AnvilMenu#mayPickup} 要求
 * {@code player.getAbilities().instabuild || player.experienceLevel >= cost} 且 {@code cost > 0},
 * 不满足时产物取不走; 取走时 {@code onTake} 按 cost 扣级。因此这里把 cost 设为 1 级(不能设为 0,
 * 否则产物永远取不走)。</p>
 */
public final class WrenchGlowItemEvents {

    /** 等级花费(级); 两类升级都用 1 级。 */
    private static final int COST_LEVELS = 1;

    /** 右槽消耗数量(颗)。 */
    private static final int MATERIAL_COST = 1;

    /** 两类升级各自首次成功时打印一次结果标记, 便于实机核对。 */
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

        event.setOutput(output);
        event.setCost(COST_LEVELS);
        event.setMaterialCost(MATERIAL_COST);
        logOnce(radiance, output);
    }

    /** 每类升级首次成功时打印一次结果标记。 */
    private static void logOnce(boolean radiance, ItemStack output) {
        AtomicBoolean flag = radiance ? LOGGED_RADIANCE : LOGGED_SHADOW_STEEL;
        if (!flag.compareAndSet(false, true))
            return;
        BetterWrenchMod.LOGGER.info(
            "wrench glow 铁砧: 普通扳手 + {} -> {}; glow={}, shadowSteel={}, rarity={}, foil={}, cost={}, materialCost={}",
            radiance ? "光辉石" : "暗影钢", radiance ? "光辉石扳手" : "暗影钢扳手",
            WrenchGlowComponent.componentValue(output), WrenchShadowSteelComponent.componentValue(output),
            output.getItem().getRarity(output), output.hasFoil(), COST_LEVELS, MATERIAL_COST);
    }
}
