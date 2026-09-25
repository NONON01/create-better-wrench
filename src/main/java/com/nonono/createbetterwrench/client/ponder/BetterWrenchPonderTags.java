package com.nonono.createbetterwrench.client.ponder;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.AllBlocks;

import net.createmod.catnip.registry.RegisteredObjectsHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * 本模组的**思索标签**(= 悬停组件按 W 时左栏那块"分类/条目", 以及 `/ponder` 索引里的条目)。
 *
 * <h2>2026-09-23 用户定案: 只保留一个</h2>
 * 原先有三个分类标签(`connect` / `deconstruct` / `process`), 用户看过界面后判定
 * 「**这 3 个小分类删掉…它们没有实际作用**」; 现在改为**一个**名为「万能扳手」的标签:
 * <ul>
 *   <li>挂在**置物台**上 ⇒ 悬停置物台按 W 时, 左栏出现「万能扳手」这一条(用户说的"置物台思索页面左上角那块");</li>
 *   <li>同时挂在**扳手**上 ⇒ 点进标签页后能看到**万能扳手这件物品**, 也就是"**定向到万能扳手**"的落点
 *       (Ponder 没有跨物品跳转的 API, 这是官方机制里最接近"把人引过去"的做法);</li>
 *   <li>9 段场景全部带这个标签 ⇒ 标签下能看到全部场景。</li>
 * </ul>
 *
 * <p><b>图标</b>: 用**扳手物品图标**(`.item(扳手, useAsIcon=true, useAsMainItem=true)`), 因此
 * `textures/ponder/tag/*.png` 那三张自绘图标已随三个分类一起删除(要恢复可用
 * `scripts/gen_ponder_tag_icons.ps1` 重新生成, 但**记得** {@code icon(String)} 只吃裸文件名、
 * 且 PNG 必须是 64×64 这两个硬条件 —— 见 docs/dev/02-item-and-visuals.md 与 docs/dev/06-ponder.md §4.1)。</p>
 *
 * <p>⚠️ **组件↔标签的订阅关系必须显式建立**({@code addTagToComponent}): PonderUI 的左栏是用
 * `PonderIndex.getTagAccess().getTags(<组件注册名>)` 取的, 只把标签挂到**场景**上不会出现分类
 * (docs/dev/06-ponder.md §4.6 的坑)。</p>
 */
public final class BetterWrenchPonderTags {

    /** 唯一的标签: 「万能扳手」。 */
    public static final ResourceLocation WRENCH = loc("wrench");

    private static ResourceLocation loc(String path) {
        return ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, path);
    }

    private BetterWrenchPonderTags() {
    }

    /** 由插件在 {@code registerTags} 回调里调用。 */
    static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {
        PonderTagRegistrationHelper<ItemLike> itemHelper =
            helper.withKeyFunction(RegisteredObjectsHelper::getKeyOrThrow);

        helper.registerTag(WRENCH)
            .addToIndex()                                           // 也进 /ponder 索引, 当作入口
            .item(BetterWrenchMod.BETTER_WRENCH.get(), true, true)   // 图标 + "主物品" 都用扳手
            .title("Universal Wrench")
            .description("Everything the wrench can do - open its Ponder to see all nine scenes")
            .register();

        // ---- 组件 ↔ 标签 ----
        // 扳手: 让标签页里能列出这件物品 —— 这就是"定向到万能扳手"
        itemHelper.addTagToComponent(BetterWrenchMod.BETTER_WRENCH.get(), WRENCH);
        // 置物台: 让"悬停置物台按 W"时左栏出现这个分类(用户明确要求的位置)
        Item depot = AllBlocks.DEPOT.get().asItem();
        if (depot != Items.AIR)
            itemHelper.addTagToComponent(depot, WRENCH);
    }
}
