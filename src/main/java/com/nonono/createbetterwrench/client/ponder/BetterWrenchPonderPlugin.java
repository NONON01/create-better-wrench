package com.nonono.createbetterwrench.client.ponder;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.client.ponder.scenes.DepotScenes;
import com.nonono.createbetterwrench.client.ponder.scenes.WrenchScenes;
import com.simibubi.create.AllBlocks;

import net.createmod.catnip.registry.RegisteredObjectsHelper;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * 本模组的**思索(Ponder)插件** —— 整个接入的入口。
 *
 * <p>注册方式(照 Create 的做法, {@code CreateClient.java:112}): 在**客户端**初始化时调用
 * {@code PonderIndex.addPlugin(new BetterWrenchPonderPlugin())}
 * —— 见 {@link com.nonono.createbetterwrench.client.BetterWrenchClient}。
 * ⚠️ 必须放在客户端: 思索索引是纯客户端概念(库源码注释: "PonderRegistry can't be loaded on Server Dist"),
 * 这样服务端零改动, 也满足本项目"通用代码不引用客户端类"的约束(docs/reference/03-known-issues.md B-1)。</p>
 *
 * <h2>场景的归属与顺序(用户 2026-09-23 定案)</h2>
 * <pre>
 *   直接附属于万能扳手:  使用万能扳手连接应力 / 使用万能扳手批量拆除
 *   归在置物台(同时也关联到万能扳手):
 *       使用万能扳手进行加工(总述) → 进行装配 / 注液 / 洗涤 / 冶炼 / 烤制 / 缠魂
 * </pre>
 * 顺序**就是注册顺序**(Create 也不用 {@code orderBefore/orderAfter}, 见 docs/dev/06-ponder.md §13);
 * 所以下面刻意按这个顺序写 {@code addStoryBoard}。
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

        // ---- ① 直接附属于万能扳手的两段 ----
        h.forComponents(BetterWrenchMod.BETTER_WRENCH.get())
            .addStoryBoard("wrench/connect", WrenchScenes::connect, BetterWrenchPonderTags.WRENCH)
            .addStoryBoard("wrench/deconstruct", WrenchScenes::deconstruct, BetterWrenchPonderTags.WRENCH);

        // ---- ② 加工那 7 段: 归在置物台, 同时**也**关联到万能扳手 ----
        //      (Create 的做法: 同一段场景可以喂给多个组件 —— 例如 cog/speedup 同时挂小/大齿轮)
        //
        // ⚠️ 场景路径的**尾段必须等于** assets/create_better_wrench/ponder/wrench/<尾段>.nbt 的文件名
        //    (2026-09-23 踩过: 曾经生成成 wrench_process.nbt, 于是 9 段全部 "schematic missing",
        //     场景照播但世界里没有任何方块 —— 见 docs/log/01-operations.md)。
        ItemLike[] holders = depotHolders();
        h.forComponents(holders)
            .addStoryBoard("wrench/process", DepotScenes::process, BetterWrenchPonderTags.WRENCH)
            .addStoryBoard("wrench/process_assembly", DepotScenes::assembly, BetterWrenchPonderTags.WRENCH)
            .addStoryBoard("wrench/process_filling", DepotScenes::filling, BetterWrenchPonderTags.WRENCH)
            .addStoryBoard("wrench/process_splash", DepotScenes::splash, BetterWrenchPonderTags.WRENCH)
            .addStoryBoard("wrench/process_blasting", DepotScenes::blasting, BetterWrenchPonderTags.WRENCH)
            .addStoryBoard("wrench/process_smoking", DepotScenes::smoking, BetterWrenchPonderTags.WRENCH)
            .addStoryBoard("wrench/process_haunting", DepotScenes::haunting, BetterWrenchPonderTags.WRENCH);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        BetterWrenchPonderTags.register(helper);
    }

    // 注: 2026-09-23 起**不再注册共享文本**(`registerSharedText`) ——
    // 原先那两条(`hold_alt` / `ctrl_scroll`)是给早期场景当"独立提示条"用的, 按 Create 场景重写后已改用
    // 正常陈述句文字("Ctrl and Scroll will switch ..."), 对应的两条 lang 键也一并删除。
    // ⚠️ 将来若重新启用共享文本, 记得同时补 `<modid>.ponder.shared.<名字>` 的中英键, 否则会显示裸键。

    /**
     * "加工"那 7 段的宿主组件: **我们的扳手** + **Create 的置物台**。
     *
     * <p>置物台拿不到时(理论上不该发生)就只挂扳手, 免得 {@code RegisteredObjectsHelper.getKeyOrThrow}
     * 在注册期抛异常把客户端启动搞崩。</p>
     */
    private static ItemLike[] depotHolders() {
        Item depot = AllBlocks.DEPOT.get().asItem();
        if (depot == Items.AIR)
            return new ItemLike[] {BetterWrenchMod.BETTER_WRENCH.get()};
        return new ItemLike[] {BetterWrenchMod.BETTER_WRENCH.get(), depot};
    }
}
