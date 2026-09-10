package com.example.createbetterwrench.mode;

import com.example.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.foundation.gui.AllIcons;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 扳手的"模式"(用 ALT 呼出底部工具条 + 滚轮循环切换)。
 *
 * <p>图标: 连接 用自绘"两方块+连线"图标(ResourceLocation, 机械动力风格);
 * 拆除 用 Create 的垃圾桶图标({@link AllIcons#I_TRASH})。</p>
 */
public enum WrenchMode {
    WRENCH("wrench", ResourceLocation.fromNamespaceAndPath(
        BetterWrenchMod.MODID, "textures/item/better_wrench.png")),
    CONNECT("connect", ResourceLocation.fromNamespaceAndPath(
        BetterWrenchMod.MODID, "textures/gui/mode_connect.png")),
    DECONSTRUCT("deconstruct", AllIcons.I_TRASH),
    LOGISTICS("logistics", ResourceLocation.fromNamespaceAndPath(
        BetterWrenchMod.MODID, "textures/gui/mode_logistics.png")),
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

    /** 该模式的中文描述(用于选择器 tooltip)。 */
    public String description() {
        return switch (this) {
            case WRENCH -> "标准机械动力扳手功能";
            case CONNECT -> "连接两点: 自动生成传动结构";
            case DECONSTRUCT -> "框选区域: 批量拆除可拆方块并入背包";
            case LOGISTICS -> "敬请期待";
            case COMING_SOON -> "mod内测版 v0.1.0";
        };
    }
}
