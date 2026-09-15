package com.nonono.createbetterwrench.mode;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.foundation.gui.AllIcons;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 扳手的"模式"(用 ALT 呼出底部工具条 + 滚轮循环切换)。
 *
 * <p>图标: 扳手/连接/装配/模组描述 用自绘 PNG(ResourceLocation, 机械动力风格);
 * 拆除 用 Create 的蓝图垃圾桶小图标({@link AllIcons#I_TRASH})。</p>
 */
public enum WrenchMode {
    WRENCH("wrench", ResourceLocation.fromNamespaceAndPath(
        BetterWrenchMod.MODID, "textures/gui/mode_wrench.png")),
    CONNECT("connect", ResourceLocation.fromNamespaceAndPath(
        BetterWrenchMod.MODID, "textures/gui/mode_connect.png")),
    DECONSTRUCT("deconstruct", AllIcons.I_TRASH),
    ASSEMBLE("assemble", ResourceLocation.fromNamespaceAndPath(
        BetterWrenchMod.MODID, "textures/gui/mode_assemble.png")),
    COMING_SOON("coming_soon", ResourceLocation.fromNamespaceAndPath(
        BetterWrenchMod.MODID, "textures/gui/mode_coming_soon.png"));

    private final String id;
    private final Object icon; // ResourceLocation 或 AllIcons, 或 null(暂不放图标)

    WrenchMode(String id, Object icon) {
        this.id = id;
        this.icon = icon;
    }

    public Component displayName() {
        return Component.translatable("mode." + BetterWrenchMod.MODID + "." + id);
    }

    /** 该模式小图标(ResourceLocation=自绘 PNG, AllIcons=蓝图白色小图标, null=暂未放)。 */
    public Object icon() {
        return icon;
    }

    /** 该模式的描述(用于选择器 tooltip); 文案在语言文件: mode.<modid>.<id>.desc。 */
    public Component description() {
        return Component.translatable("mode." + BetterWrenchMod.MODID + "." + id + ".desc");
    }
}
