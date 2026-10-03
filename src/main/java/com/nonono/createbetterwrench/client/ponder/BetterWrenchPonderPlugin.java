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
 * 本模组的<b>思索(Ponder)插件</b> —— 整个思索接入的入口。
 *
 * <p>注册方式(照 Create 的做法, {@code CreateClient.java:112}): 在<b>客户端</b>初始化时调用
 * {@code PonderIndex.addPlugin(new BetterWrenchPonderPlugin())}
 * —— 见 {@link com.nonono.createbetterwrench.client.BetterWrenchClient}。
 * 必须放在客户端: 思索索引是纯客户端概念(上游库注释注明 PonderRegistry 无法在服务端分发侧加载),
 * 这样服务端零改动, 也满足本项目关于通用代码不引用客户端类的约束(docs/design/known-issues.md B-1)。</p>
 *
 * <h2>场景的归属与顺序(设计约定, 2026-09-23)</h2>
 * <pre>
 *   直接附属于万能扳手:  使用万能扳手连接应力 / 使用万能扳手批量拆除
 *   归在置物台(同时也关联到万能扳手):
 *       使用万能扳手进行加工(总述), 以及随后的进行装配 / 注液 / 洗涤 / 冶炼 / 烤制 / 缠魂
 * </pre>
 * 顺序<b>就是注册顺序</b>(Create 也不用 {@code orderBefore/orderAfter}, 见 docs/modules/ponder.md §13);
 * 所以下面刻意按这个顺序写 {@code addStoryBoard}。
 */
public class BetterWrenchPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return BetterWrenchMod.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        // 把注册键的类型从 ResourceLocation 换成物品, 这样可以直接写本模组的物品
        PonderSceneRegistrationHelper<ItemLike> h = helper.withKeyFunction(RegisteredObjectsHelper::getKeyOrThrow);

        // ---- ① 直接附属于万能扳手的两段 ----
        h.forComponents(BetterWrenchMod.BETTER_WRENCH.get())
            .addStoryBoard("wrench/connect", WrenchScenes::connect, BetterWrenchPonderTags.WRENCH)
            .addStoryBoard("wrench/deconstruct", WrenchScenes::deconstruct, BetterWrenchPonderTags.WRENCH);

        // ---- ② 加工那 7 段: 归在置物台, 同时也关联到万能扳手 ----
        //      (Create 的做法: 同一段场景可以注册给多个组件 —— 例如 cog/speedup 同时挂小/大齿轮)
        //
        // 注意: 场景路径的尾段必须等于 assets/create_better_wrench/ponder/wrench/<尾段>.nbt 的文件名
        //    (2026-09-23 的实例: 曾生成成 wrench_process.nbt, 于是 9 段全部报 schematic missing,
        //     场景照常播放但世界里没有任何方块 —— 见 docs/log/01-operations.md)。
        ItemLike[] holders = depotHolders();
        h.forComponents(holders)
            .addStoryBoard("wrench/process", DepotScenes::process, BetterWrenchPonderTags.WRENCH)
            .addStoryBoard("wrench/process_assembly", DepotScenes::assembly, BetterWrenchPonderTags.WRENCH)
            .addStoryBoard("wrench/process_filling", DepotScenes::filling, BetterWrenchPonderTags.WRENCH)
            .addStoryBoard("wrench/process_splash", DepotScenes::splash, BetterWrenchPonderTags.WRENCH)
            .addStoryBoard("wrench/process_blasting", DepotScenes::blasting, BetterWrenchPonderTags.WRENCH)
            .addStoryBoard("wrench/process_smoking", DepotScenes::smoking, BetterWrenchPonderTags.WRENCH)
            .addStoryBoard("wrench/process_haunting", DepotScenes::haunting, BetterWrenchPonderTags.WRENCH)
            .addStoryBoard("wrench/process_forging", DepotScenes::forging, BetterWrenchPonderTags.WRENCH);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        BetterWrenchPonderTags.register(helper);
    }

    // 注: 2026-09-23 起不再注册共享文本(`registerSharedText`) ——
    // 原先那两条(`hold_alt` / `ctrl_scroll`)是给早期场景当独立提示条用的, 按 Create 场景重写后已改用
    // 正常陈述句文字(`Ctrl and Scroll will switch ...`), 对应的两条 lang 键也一并删除。
    // 注意: 将来若重新启用共享文本, 需同时补 `<modid>.ponder.shared.<名字>` 的中英键, 否则会显示裸键。

    /**
     * 「加工」那 7 段的宿主组件: <b>本模组的扳手</b> + <b>Create 的置物台</b>。
     *
     * <p>置物台拿不到时(理论上不该发生)就只挂扳手, 避免 {@code RegisteredObjectsHelper.getKeyOrThrow}
     * 在注册期抛异常导致客户端启动失败。</p>
     */
    private static ItemLike[] depotHolders() {
        Item depot = AllBlocks.DEPOT.get().asItem();
        if (depot == Items.AIR)
            return new ItemLike[] {BetterWrenchMod.BETTER_WRENCH.get()};
        return new ItemLike[] {BetterWrenchMod.BETTER_WRENCH.get(), depot};
    }
}
