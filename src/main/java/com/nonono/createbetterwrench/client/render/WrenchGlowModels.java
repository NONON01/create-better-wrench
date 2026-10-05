package com.nonono.createbetterwrench.client.render;

import java.util.Map;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ModelEvent;

/**
 * 在模型烘焙完成时, 把万能扳手的物品模型换成带外发光层的包装模型(见 {@link WrenchGlowModel})。
 *
 * <p>挂在 MOD 总线的 {@code ModelEvent.ModifyBakingResult} 上, 由 {@code client/BetterWrenchClient}
 * 显式登记。该事件在资源重载时每次烘焙都会触发, 事件里的映射是本次刚烘焙出的新模型, 因此不存在重复包装。</p>
 *
 * <p><b>与 1.21.1 的差异</b>: 1.21.1 用 {@code ModelResourceLocation.inventory(id)} 造键;1.20.1 的
 * {@code ModelResourceLocation} 没有该静态方法, 而 {@code ModifyBakingResult#getModels()} 的键类型是
 * {@code Map<ResourceLocation, BakedModel>} —— 物品模型的键是
 * {@code new ModelResourceLocation(new ResourceLocation(MODID, "item/better_wrench"), "inventory")}
 * ({@code ModelResourceLocation} 是 {@code ResourceLocation} 的子类, 相等性包含 variant)。找不到精确键时
 * 再按"路径 = item/better_wrench 且 variant = inventory"兜底扫描一遍, 只记警告、不抛异常。</p>
 *
 * <p>开关方式: 移除 {@code BetterWrenchClient} 里对本方法的登记即可, 不影响其它任何行为。</p>
 *
 * <p>该事件在客户端资源重载的工作线程上触发, 本类只读该映射并做一次等值替换, 不访问工作线程之外的任何状态
 * (与事件文档给出的约束一致)。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class WrenchGlowModels {

    /** 万能扳手的物品 id, 烘焙映射里对应 {@code ModelResourceLocation.inventory(该 id)}。 */
    private static final ResourceLocation WRENCH_ITEM_ID =
        new ResourceLocation(BetterWrenchMod.MODID, "better_wrench");

    /** 物品模型在映射里的路径(variant 固定为 {@code inventory})。 */
    private static final String ITEM_MODEL_PATH = "item/better_wrench";

    private WrenchGlowModels() {
    }

    /** 把扳手的物品模型包上发光层; 找不到该模型时只记一条警告, 不抛异常(该效果不阻断模型加载)。 */
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        ModelResourceLocation key = new ModelResourceLocation(
            new ResourceLocation(WRENCH_ITEM_ID.getNamespace(), ITEM_MODEL_PATH), "inventory");
        BakedModel base = models.get(key);
        if (base == null)
            base = findInventoryModel(models);
        if (base == null) {
            BetterWrenchMod.LOGGER.warn("wrench glow: 物品模型 {} 不在本次烘焙结果中, 外发光层未挂载", key);
            return;
        }
        models.put(key, WrenchGlowModel.wrap(base));
    }

    /** 兜底: 在烘焙映射里按"路径 + inventory variant"找物品模型(应对键的实际构造方式与预期不一致)。 */
    private static BakedModel findInventoryModel(Map<ResourceLocation, BakedModel> models) {
        BakedModel fallback = null;
        for (Map.Entry<ResourceLocation, BakedModel> entry : models.entrySet()) {
            ResourceLocation location = entry.getKey();
            if (!WRENCH_ITEM_ID.getNamespace().equals(location.getNamespace())
                || !ITEM_MODEL_PATH.equals(location.getPath()))
                continue;
            if (location instanceof ModelResourceLocation modelLocation
                && !"inventory".equals(modelLocation.getVariant()))
                continue;
            fallback = entry.getValue();
            if (location instanceof ModelResourceLocation)
                break;
        }
        return fallback;
    }
}
