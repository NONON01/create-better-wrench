package com.nonono.createbetterwrench.client.ponder;

import com.nonono.createbetterwrench.BetterWrenchMod;

import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * 本模组的**思索标签**(= 思索索引界面里的"分类")。
 *
 * <p>设计(用户 2026-09-22 给出): 扳手功能分三类 —— **连接 / 拆除 / 加工**(加工下再分装配/注液/洗涤/熔炼/烟熏/缠魂),
 * 另加一个总标签把整个 mod 收在一起。标签名与说明的文本键 = {@code <modid>.ponder.tag.<id>(.description)}。</p>
 *
 * <p><b>图标</b>: 三个分类标签用的是**我们 HUD 工具栏那套模式小图标**
 * ({@code assets/create_better_wrench/textures/gui/mode_*.png})。
 * 依据(实测字节码): {@code TagBuilder.icon(String)} 会把字符串拼成
 * {@code <标签命名空间>:<该字符串>} 再**当纹理 ResourceLocation 直接用**, 所以这里要给**完整纹理路径**
 * (含 {@code textures/} 与 {@code .png})。总标签则直接用**物品图标**(万能扳手)。</p>
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
        // ---- 总标签: 图标用物品(万能扳手) ----
        helper.registerTag(WRENCH_TOOLS)
            .addToIndex()
            .item(BetterWrenchMod.BETTER_WRENCH.get(), true, false)
            .title("Universal Wrench")
            .description("Everything added by this mod: the wrench itself and its five modes")
            .register();

        // ---- 三个分类标签: 图标用我们的模式小图标(完整纹理路径!) ----
        helper.registerTag(CONNECT)
            .addToIndex()
            .icon("textures/gui/mode_connect.png")
            .title("Connect")
            .description("Link kinetic blocks into a drivetrain, paying the materials from your inventory")
            .register();

        helper.registerTag(DECONSTRUCT)
            .addToIndex()
            .icon("textures/gui/mode_deconstruct.png")
            .title("Deconstruct")
            .description("Remove every wrenchable block inside a selection")
            .register();

        helper.registerTag(PROCESS)
            .addToIndex()
            .icon("textures/gui/mode_assemble.png")
            .title("Process")
            .description("Work a locked depot by hand: assembly, filling, and fan-style washing / blasting / smoking / haunting")
            .register();
    }
}
