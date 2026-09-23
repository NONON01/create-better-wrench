package com.nonono.createbetterwrench.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.client.gui.WrenchConfigScreen;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * **打开专属配置页面**的组合键(默认 **B + C**, 两个键都能在"按键设置"里改)。
 *
 * <p>为什么用两个按键映射而不是一个: 原版 {@link KeyMapping} 只能绑**单个**键, 想要"组合键"就得自己配一对。
 * 所以这里注册两个条目 —— 「配置页面(主键)」默认 {@code B}、「配置页面(组合键)」默认 {@code C} ——
 * 两个**同时按住**时才打开页面。玩家想换成别的组合, 直接在按键设置里分别改这两个即可。</p>
 *
 * <p>触发条件: 两个键都按下 + 没有别的界面开着({@code mc.screen == null})+ 还没触发过(边沿触发, 见 {@link #latched})。
 * 用<em>边沿</em>触发是为了避免"按住不放 ⇒ 页面开了又开"。屏幕里按住不放也不会重开, 松开任一键即复位。</p>
 */
public final class WrenchConfigKeybinds {

    private static final String CATEGORY = "key.categories." + BetterWrenchMod.MODID;

    /** 主键(默认 B)。 */
    public static final KeyMapping OPEN_KEY = new KeyMapping(
        "key." + BetterWrenchMod.MODID + ".config_page", InputConstants.Type.KEYSYM, InputConstants.KEY_B, CATEGORY);
    /** 组合键(默认 C)。 */
    public static final KeyMapping OPEN_MODIFIER = new KeyMapping(
        "key." + BetterWrenchMod.MODID + ".config_page_modifier", InputConstants.Type.KEYSYM, InputConstants.KEY_C, CATEGORY);

    /** 边沿触发闩: 两个键都按住时只开一次, 松开任一键即复位。 */
    private static boolean latched;

    private WrenchConfigKeybinds() {
    }

    /** 由客户端入口在 mod 事件总线上注册(mod 总线: {@code RegisterKeyMappingsEvent})。 */
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_KEY);
        event.register(OPEN_MODIFIER);
    }

    @EventBusSubscriber(modid = BetterWrenchMod.MODID, value = Dist.CLIENT)
    public static final class Handler {

        private Handler() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            boolean both = OPEN_KEY.isDown() && OPEN_MODIFIER.isDown();
            if (!both) {
                latched = false;
                return;
            }
            if (latched)
                return;
            latched = true;
            if (mc.screen == null && mc.player != null)
                mc.setScreen(WrenchConfigScreen.create(null));
        }
    }
}
