package com.nonono.createbetterwrench.client.render;

import java.util.Map;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * 实验: 在模型烘焙完成时, 把万能扳手的物品模型换成带外发光层的包装模型(见 {@link WrenchGlowModel})。
 *
 * <p>挂在 MOD 总线的 {@code ModelEvent.ModifyBakingResult} 上, 由 {@code client/BetterWrenchClient}
 * 显式登记。该事件在资源重载时每次烘焙都会触发, 事件里的映射是本次刚烘焙出的新模型, 因此不存在重复包装。</p>
 *
 * <p>开关方式: 移除 {@code BetterWrenchClient} 里对本方法的登记即可, 不影响其它任何行为。</p>
 *
 * <p>该事件在客户端资源重载的工作线程上触发, 本类只读该映射并做一次等值替换, 不访问工作线程之外的
 * 任何状态(与事件文档给出的约束一致)。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class WrenchGlowModels {

    /** 万能扳手的物品 id, 烘焙映射里对应 {@code ModelResourceLocation.inventory(该 id)}。 */
    private static final ResourceLocation WRENCH_ITEM_ID =
        ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, "better_wrench");

    private WrenchGlowModels() {
    }

    /** 把扳手的物品模型包上发光层; 找不到该模型时只记一条警告, 不抛异常(实验功能不阻断模型加载)。 */
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        ModelResourceLocation key = ModelResourceLocation.inventory(WRENCH_ITEM_ID);
        BakedModel base = models.get(key);
        if (base == null) {
            BetterWrenchMod.LOGGER.warn("wrench glow: 物品模型 {} 不在本次烘焙结果中, 外发光层未挂载", key);
            return;
        }
        models.put(key, WrenchGlowModel.wrap(base));
    }
}
