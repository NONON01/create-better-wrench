package com.nonono.createbetterwrench.client.ponder;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.createmod.catnip.registry.RegisteredObjectsHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

/**
 * 本模组的**思索标签**(= 思索索引界面里的"分类")。
 *
 * <p>设计(用户 2026-09-22 给出): 扳手功能分三类 —— **连接 / 拆除 / 加工**(加工下再分装配/注液/洗涤/熔炼/烟熏/缠魂),
 * 另加一个总标签把整个 mod 收在一起。标签名与说明的文本键 = {@code <modid>.ponder.tag.<id>(.description)}。</p>
 *
 * <p><b>图标</b>: 三个分类标签用的是**我们 HUD 工具栏那套模式小图标**, 但**不是**直接指向
 * {@code textures/gui/mode_*.png} —— 见下面的"图标路径"说明; 总标签则直接用**物品图标**(万能扳手)。</p>
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

    /** 总标签: 本模组的全部功能。 */
    public static final ResourceLocation WRENCH_TOOLS = loc("wrench_tools");
    /** 分类: 连接。 */
    public static final ResourceLocation CONNECT = loc("connect");
    /** 分类: 拆除。 */
    public static final ResourceLocation DECONSTRUCT = loc("deconstruct");
    /** 分类: 加工(下含装配/注液/洗涤/熔炼/烟熏/缠魂)。 */
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

        // ---- 总标签: 图标用物品(万能扳手) ----
        helper.registerTag(WRENCH_TOOLS)
            .addToIndex()
            .item(BetterWrenchMod.BETTER_WRENCH.get(), true, false)
            .title("Universal Wrench")
            .description("Everything added by this mod: the wrench itself and its five modes")
            .register();

        // ---- 三个分类标签: 图标用我们的模式小图标(裸文件名! 见类注释) ----
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
        itemHelper.addTagToComponent(BetterWrenchMod.BETTER_WRENCH.get(), WRENCH_TOOLS);
        itemHelper.addTagToComponent(BetterWrenchMod.BETTER_WRENCH.get(), CONNECT);
        itemHelper.addTagToComponent(BetterWrenchMod.BETTER_WRENCH.get(), DECONSTRUCT);
        itemHelper.addTagToComponent(BetterWrenchMod.BETTER_WRENCH.get(), PROCESS);
        // ℹ️ 以后把加工那 6 段挂到 `create:depot` 时, 这里再加一行:
        //    itemHelper.addTagToComponent(BuiltInRegistries.ITEM.get(...depot...), PROCESS);
    }
}
