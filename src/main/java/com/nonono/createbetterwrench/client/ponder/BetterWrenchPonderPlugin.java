package com.nonono.createbetterwrench.client.ponder;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.client.ponder.scenes.WrenchScenes;

import net.createmod.catnip.registry.RegisteredObjectsHelper;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

/**
 * 本模组的**思索(Ponder)插件** —— 整个接入的入口。
 *
 * <p>注册方式(照 Create 的做法, {@code CreateClient.java:112}): 在**客户端**初始化时调用
 * {@code PonderIndex.addPlugin(new BetterWrenchPonderPlugin())}
 * —— 见 {@link com.nonono.createbetterwrench.client.BetterWrenchClient}。
 * ⚠️ 必须放在客户端: 思索索引是纯客户端概念(库源码注释: "PonderRegistry can't be loaded on Server Dist"),
 * 这样服务端零改动, 也满足本项目"通用代码不引用客户端类"的约束(docs/07 §6 B-1)。</p>
 */
public class BetterWrenchPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return BetterWrenchMod.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        // 把"注册键"从 ResourceLocation 换成物品, 这样可以直接写我们的物品
        PonderSceneRegistrationHelper<ItemLike> h = helper.withKeyFunction(RegisteredObjectsHelper::getKeyOrThrow);

        // 我们只有一件物品(万能扳手) ⇒ 场景都挂在它上面; 悬停它 + 按住 W 就能看。
        // ⚠️ 一个场景可以挂多个标签(会同时出现在多个分类下) ⇒ 总览挂到全部三个分类里都能看到。
        h.forComponents(BetterWrenchMod.BETTER_WRENCH.get())
            .addStoryBoard("wrench/overview", WrenchScenes::overview,
                BetterWrenchPonderTags.CONNECT,
                BetterWrenchPonderTags.DECONSTRUCT,
                BetterWrenchPonderTags.PROCESS);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        BetterWrenchPonderTags.register(helper);
    }

    @Override
    public void registerSharedText(net.createmod.ponder.api.registration.SharedTextRegistrationHelper helper) {
        // 跨场景复用的短文本(键 = <modid>.ponder.shared.<名字>)
        helper.registerSharedText("hold_alt", "Hold ALT");
        helper.registerSharedText("ctrl_scroll", "Ctrl + Scroll");
    }
}
