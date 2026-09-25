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
 * 本模组的**思索标签**(= 思索索引界面里的"分类")。
 *
 * <p>设计(用户 2026-09-22 给出): 扳手功能分三类 —— **连接 / 拆除 / 加工**(加工下再分装配/注液/洗涤/熔炼/烟熏/缠魂)。
 * ⚠️ 2026-09-23 用户确认「**应该是三种模式**」⇒ **就这三个标签, 没有第四个"总标签"** ——
 * 索引页与"按 W 打开的物品界面"里都只出现这三个分类。
 * 标签名与说明的文本键 = {@code <modid>.ponder.tag.<id>(.description)}。</p>
 *
 * <p><b>图标</b>: 三个分类标签用的是**我们 HUD 工具栏那三个模式的小图标**
 * (连接=mode_connect / 拆除=mode_deconstruct / 加工=mode_assemble), 但**不是**直接指向
 * {@code textures/gui/mode_*.png} —— 见下面的"图标路径"说明。</p>
 *
 * <p><b>⚠️ 图标路径(踩过坑, 实测字节码)</b>: {@code TagBuilder.icon(String)} 只接受**裸文件名**,
 * 它会自己补成 {@code <标签命名空间>:textures/ponder/tag/<字符串>.png}。所以</p>
 * <ul>
 *   <li>写 {@code .icon("connect")} ⇒ {@code create_better_wrench:textures/ponder/tag/connect.png} ✅</li>
 *   <li>写 {@code .icon("textures/gui/mode_connect.png")} ⇒ 指向
 *       {@code textures/ponder/tag/textures/gui/mode_connect.png.png} ⇒ **找不到纹理 = 黑紫格** ❌</li>
 * </ul>
 * <p>另: {@code PonderTag} 渲染时按 **64×64** 区域 blit, 所以图标 PNG 必须是 64×64。
 * 两个条件都满足才不会出现黑紫块。三个图标由 {@code scripts/gen_ponder_tag_icons.ps1}
 * 从 16×16 的 {@code textures/gui/mode_*.png} 最近邻放大生成。</p>
 *
 * <p>⚠️ 库里的"章节(PonderChapter)"是空实现({@code of()} 直接 return null), 所以**分类只能用标签** —— 详见 docs/13 §4.7。</p>
 */
public final class BetterWrenchPonderTags {

    /** 分类: 连接(图标 = HUD 的 `mode_connect`)。 */
    public static final ResourceLocation CONNECT = loc("connect");
    /** 分类: 拆除(图标 = HUD 的 `mode_deconstruct`)。 */
    public static final ResourceLocation DECONSTRUCT = loc("deconstruct");
    /** 分类: 加工(图标 = HUD 的 `mode_assemble`; 下含装配/注液/洗涤/熔炼/烟熏/缠魂)。 */
    public static final ResourceLocation PROCESS = loc("process");

    private static ResourceLocation loc(String path) {
        return ResourceLocation.fromNamespaceAndPath(BetterWrenchMod.MODID, path);
    }

    private BetterWrenchPonderTags() {
    }

    /** 由插件在 {@code registerTags} 回调里调用。 */
    static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {
        // ⚠️ 关键: 还要把**组件(物品)**挂到标签上, 否则:
        //    ① 打开该物品的 PonderUI 时看不到"分类" —— PonderUI 是用
        //       `PonderIndex.getTagAccess().getTags(<该物品的注册名>)` 取分类的(实测字节码);
        //    ② 标签页里点进去也没有条目 —— 那一页列的是 `getItems(<标签>)`。
        //    Create 的写法是 `HELPER.addToTag(TAG).add(方块/物品...)`(AllCreatePonderTags), 这里同样处理。
        PonderTagRegistrationHelper<ItemLike> itemHelper =
            helper.withKeyFunction(RegisteredObjectsHelper::getKeyOrThrow);

        // ---- 只有这三个分类标签(= 用户要的"三种模式"): 图标用我们的模式小图标(裸文件名! 见类注释) ----
        helper.registerTag(CONNECT)
            .addToIndex()
            .icon("connect")
            .title("Connect")
            .description("Link kinetic blocks into a drivetrain, paying the materials from your inventory")
            .register();

        helper.registerTag(DECONSTRUCT)
            .addToIndex()
            .icon("deconstruct")
            .title("Deconstruct")
            .description("Remove every wrenchable block inside a selection")
            .register();

        helper.registerTag(PROCESS)
            .addToIndex()
            .icon("process")
            .title("Process")
            .description("Work a locked depot by hand: assembly, filling, and fan-style washing / blasting / smoking / haunting")
            .register();

        // ---- 组件 ↔ 标签 ----
        // 扳手同时属于这三个分类; 加工相关的 7 段场景归在置物台上 ⇒ **置物台也要挂"加工"**
        // (否则悬停置物台时左侧不出现"加工"那一栏 —— 见上面那条实测结论)。
        itemHelper.addTagToComponent(BetterWrenchMod.BETTER_WRENCH.get(), CONNECT);
        itemHelper.addTagToComponent(BetterWrenchMod.BETTER_WRENCH.get(), DECONSTRUCT);
        itemHelper.addTagToComponent(BetterWrenchMod.BETTER_WRENCH.get(), PROCESS);
        Item depot = AllBlocks.DEPOT.get().asItem();
        if (depot != Items.AIR)
            itemHelper.addTagToComponent(depot, PROCESS);
    }
}
