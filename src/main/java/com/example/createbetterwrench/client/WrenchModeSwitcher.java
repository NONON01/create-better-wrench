package com.example.createbetterwrench.client;

import org.lwjgl.glfw.GLFW;

import com.example.createbetterwrench.BetterWrenchMod;
import com.example.createbetterwrench.mode.WrenchMode;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * 客户端:ALT 呼出/聚焦底部工具条所需的按键绑定与当前模式状态。
 */
@OnlyIn(Dist.CLIENT)
public final class WrenchModeSwitcher {

    /** 呼出/聚焦工具条用的键(默认左 ALT, 用户可改)。 */
    public static final KeyMapping TOOLS_KEY = new KeyMapping(
        "key." + BetterWrenchMod.MODID + ".tools",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT,
        "key.categories." + BetterWrenchMod.MODID);

    /** 当前选中的模式。 */
    public static WrenchMode current = WrenchMode.CONNECT;

    private WrenchModeSwitcher() {
    }

    @EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        private Register() {
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(TOOLS_KEY);
        }
    }

    /** 滚轮循环切换模式(direction>0 向前)。 */
    public static WrenchMode cycle(int direction) {
        WrenchMode[] all = WrenchMode.values();
        int idx = current.ordinal() + (direction < 0 ? -1 : 1);
        int len = all.length;
        current = all[((idx % len) + len) % len];
        return current;
    }
}
