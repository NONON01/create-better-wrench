package com.nonono.createbetterwrench.client.ponder;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.AllBlocks;

import com.nonono.createbetterwrench.client.ponder.PonderKeys;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * 本模组的<b>思索标签</b>(= 悬停组件按 W 时左栏那块分类/条目, 以及 {@code /ponder} 索引里的条目)。
 *
 * <h2>标签归并(设计约定, 2026-09-23): 只保留一个</h2>
 * 原先有三个分类标签(`connect` / `deconstruct` / `process`), 实机查看界面后判定这 3 个小分类
 * 没有实际作用, 因此删除; 现在改为<b>一个</b>名为「万能扳手」的标签:
 * <ul>
 *   <li>挂在<b>置物台</b>上, 因此悬停置物台按 W 时, 左栏出现「万能扳手」这一条(即置物台思索页面左上角那块);</li>
 *   <li>同时挂在<b>扳手</b>上, 因此点进标签页后能看到<b>万能扳手这件物品</b>, 也就是定向到万能扳手的落点
 *       (Ponder 没有跨物品跳转的 API, 这是官方机制里最接近把玩家引导过去的做法);</li>
 *   <li>9 段场景全部带这个标签, 因此标签下能看到全部场景。</li>
 * </ul>
 *
 * <p><b>图标</b>: 用<b>扳手物品图标</b>({@code .item(扳手, useAsIcon=true, useAsMainItem=true)}), 因此
 * {@code textures/ponder/tag/*.png} 那三张自绘图标已随三个分类一起删除(要恢复可用
 * {@code scripts/gen_ponder_tag_icons.ps1} 重新生成, 但需注意 {@code icon(String)} 只接受裸文件名、
 * 且 PNG 必须是 64×64 这两个硬条件 —— 见 docs/modules/item-and-visuals.md 与 docs/modules/ponder.md §4.1)。</p>
 *
 * <p>注意: <b>组件与标签的订阅关系必须显式建立</b>({@code addTagToComponent}): PonderUI 的左栏是用
 * {@code PonderIndex.getTagAccess().getTags(<组件注册名>)} 取的, 只把标签挂到<b>场景</b>上不会出现分类
 * (参见 docs/modules/ponder.md §4.6)。</p>
 */
public final class BetterWrenchPonderTags {

    /** 唯一的标签: 「万能扳手」。 */
    public static final ResourceLocation WRENCH = loc("wrench");

    private static ResourceLocation loc(String path) {
        return new ResourceLocation(BetterWrenchMod.MODID, path);
    }

    private BetterWrenchPonderTags() {
    }

    /** 由插件在 {@code registerTags} 回调里调用。 */
    static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {
        PonderTagRegistrationHelper<ItemLike> itemHelper =
            helper.withKeyFunction(PonderKeys::key);

        helper.registerTag(WRENCH)
            .addToIndex()                                           // 也进 /ponder 索引, 当作入口
            .item(BetterWrenchMod.BETTER_WRENCH.get(), true, true)   // 图标与主物品都用扳手
            .title("Universal Wrench")
            .description("Everything the wrench can do - open its Ponder to see all nine scenes")
            .register();

        // ---- 组件与标签 ----
        // 扳手: 让标签页里能列出这件物品 —— 这就是定向到万能扳手
        itemHelper.addTagToComponent(BetterWrenchMod.BETTER_WRENCH.get(), WRENCH);
        // 置物台: 让悬停置物台按 W 时左栏出现这个分类(设计约定的位置, 2026-09-23)
        Item depot = AllBlocks.DEPOT.get().asItem();
        if (depot != Items.AIR)
            itemHelper.addTagToComponent(depot, WRENCH);
    }
}
