package com.nonono.createbetterwrench.client;

import org.lwjgl.glfw.GLFW;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.mode.AssembleStay;
import com.nonono.createbetterwrench.mode.ConnectCorner;
import com.nonono.createbetterwrench.mode.DeconstructScope;
import com.nonono.createbetterwrench.mode.WrenchMode;
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
    public static WrenchMode current = WrenchMode.WRENCH;

    /** 「拆除」模式当前的 Ctrl 范围过滤(全部/仅机械动力/仅红石)。 */
    public static DeconstructScope deconstructScope = DeconstructScope.ALL;

    /** 「连接」模式当前的 Ctrl 拐角类型(齿轮箱/大齿轮)。 */
    public static ConnectCorner connectCorner = ConnectCorner.GEARBOX;

    /** 「加工」模式当前的 Ctrl 成品停留时间(不停留/短/中/长 = 0/2/4/8 tick)。 */
    public static AssembleStay assembleStay = AssembleStay.DEFAULT;

    /** 「模组描述」里的彩蛋开关: false=正常模式, true=战斗模式(才应用伤害/攻速/取消无敌)。 */
    public static boolean combatMode = false;

    private WrenchModeSwitcher() {
    }

    /**
     * 断开连接(退出世界 / 换服务器)时把客户端状态恢复成**出厂默认**。
     *
     * <p>审计发现: 这些静态字段没有登出清理 ⇒ 换到一个新服务器后会出现
     * 「本地显示已开战斗模式、服务端却完全没收到」这类假象(进入世界时的同步包只在新世界建立时发,
     * 而旧值一直留着)。所以登出时统一归零, 与"出厂默认 = 扳手模式"的设计一致。</p>
     */
    public static void reset() {
        current = WrenchMode.WRENCH;
        deconstructScope = DeconstructScope.ALL;
        connectCorner = ConnectCorner.GEARBOX;
        assembleStay = AssembleStay.DEFAULT;
        combatMode = false;
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

    /**
     * 循环切换"当前模式自己的 Ctrl 选项"。
     * 拆除 → 拆除范围(全部/仅机械动力/仅红石); 连接 → 拐角类型(齿轮箱/大齿轮);
     * 加工 → 成品停留时间(不停留/短/中/长); 模组描述 → 彩蛋开关(正常/战斗); 其它模式返回 null。
     */
    public static Object cycleCtrlOption(int direction) {
        if (current == WrenchMode.DECONSTRUCT) {
            DeconstructScope[] scopes = DeconstructScope.values();
            int idx = deconstructScope.ordinal() + (direction < 0 ? -1 : 1);
            deconstructScope = scopes[((idx % scopes.length) + scopes.length) % scopes.length];
            return deconstructScope;
        }
        if (current == WrenchMode.CONNECT) {
            ConnectCorner[] corners = ConnectCorner.values();
            int idx = connectCorner.ordinal() + (direction < 0 ? -1 : 1);
            connectCorner = corners[((idx % corners.length) + corners.length) % corners.length];
            return connectCorner;
        }
        if (current == WrenchMode.ASSEMBLE) {
            assembleStay = assembleStay.cycle(direction);
            // 停留时间由**服务端**执行(弹出延时), 所以切换后必须同步过去
            AssembleStayClient.send();
            return assembleStay;
        }
        if (current == WrenchMode.COMING_SOON) {
            // 彩蛋: Ctrl 切换 正常模式 / 战斗模式。
            // 保持"按下即反馈": 本地乐观翻转并立刻由 HUD 显示 actionbar(与原来一模一样)。
            // 同时把"想要的值"发给服务端; 若被权限拒绝, 权威回包到达时会**再显示一次**正确值把它覆盖掉
            // (见 WrenchCombatClient#onPlayerTick), 因此不会停在"战斗模式"上。
            combatMode = !combatMode;
            WrenchCombatClient.sendCombatModeRequest(combatMode);
            return combatMode;
        }
        return null;
    }

    /** 当前模式 Ctrl 选项的完整展示文案; 无 Ctrl 选项则返回 null。文案在语言文件: hint.<modid>.*。 */
    public static net.minecraft.network.chat.Component ctrlOptionHint() {
        if (current == WrenchMode.DECONSTRUCT)
            return net.minecraft.network.chat.Component.translatable(
                "hint." + BetterWrenchMod.MODID + ".deconstruct", deconstructScope.displayName());
        if (current == WrenchMode.CONNECT)
            return net.minecraft.network.chat.Component.translatable(
                "hint." + BetterWrenchMod.MODID + ".corner", connectCorner.displayName());
        if (current == WrenchMode.ASSEMBLE)
            return net.minecraft.network.chat.Component.translatable(
                "hint." + BetterWrenchMod.MODID + ".stay", assembleStay.displayName());
        if (current == WrenchMode.COMING_SOON)
            return net.minecraft.network.chat.Component.translatable(
                "hint." + BetterWrenchMod.MODID + (combatMode ? ".combat.on" : ".combat.off"));
        return null;
    }
}
