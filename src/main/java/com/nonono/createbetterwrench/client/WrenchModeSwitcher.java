package com.nonono.createbetterwrench.client;

import org.lwjgl.glfw.GLFW;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.config.WrenchConfig;
import com.nonono.createbetterwrench.mode.AssembleStay;
import com.nonono.createbetterwrench.mode.ChainSubMode;
import com.nonono.createbetterwrench.mode.ConnectCorner;
import com.nonono.createbetterwrench.mode.DeconstructScope;
import com.nonono.createbetterwrench.mode.WrenchMode;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

/**
 * 客户端: ALT 呼出/聚焦底部工具条所需的按键绑定与当前模式状态。
 */
@OnlyIn(Dist.CLIENT)
public final class WrenchModeSwitcher {

    /** 呼出/聚焦工具条用的键(默认左 ALT, 可在原版按键设置里改绑)。 */
    public static final KeyMapping TOOLS_KEY = new KeyMapping(
        "key." + BetterWrenchMod.MODID + ".tools",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT,
        "key.categories." + BetterWrenchMod.MODID);

    /** 当前选中的模式。 */
    public static WrenchMode current = WrenchMode.WRENCH;

    /** 「拆除」模式当前的 Ctrl 范围过滤(全部 / 仅机械动力 / 仅红石)。 */
    public static DeconstructScope deconstructScope = DeconstructScope.ALL;

    /** 「连接」模式当前的 Ctrl 拐角类型(齿轮箱 / 大齿轮)。 */
    public static ConnectCorner connectCorner = ConnectCorner.GEARBOX;

    /** 「加工」模式当前的 Ctrl 成品停留时间(不停留 / 短 / 中 / 长 = 0 / 2 / 4 / 8 tick)。 */
    public static AssembleStay assembleStay = AssembleStay.DEFAULT;

    /** 「锁链传动」模式当前的 Ctrl 子模式(标准 / 直线 / 半自动)。 */
    public static ChainSubMode chainSubMode = ChainSubMode.STANDARD;

    /** 「模组描述」里的战斗加成(可选)开关: false = 正常模式, true = 战斗模式(才应用伤害/攻速/取消无敌)。 */
    public static boolean combatMode = false;

    private WrenchModeSwitcher() {
    }

    /**
     * 断开连接(退出世界 / 换服务器)时把客户端状态恢复成<b>出厂默认</b>。
     *
     * <p>审计发现: 这些静态字段此前没有登出清理, 于是换到一个新服务器后会出现
     * 本地已开战斗模式、服务端却完全没收到同步这类假象(进入世界时的同步包只在新世界建立时发,
     * 而旧值一直留着)。因此登出时统一归零, 与"出厂默认 = 扳手模式"的设计一致。</p>
     */
    public static void reset() {
        current = WrenchMode.WRENCH;
        deconstructScope = DeconstructScope.ALL;
        connectCorner = ConnectCorner.GEARBOX;
        assembleStay = AssembleStay.DEFAULT;
        chainSubMode = ChainSubMode.STANDARD;
        combatMode = false;
    }

    /** MOD 总线: 注册"呼出工具条"按键。由 {@code client/BetterWrenchClient} 显式注册(不再用已废弃的 bus())。 */
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOOLS_KEY);
    }

    /** 滚轮循环切换模式: direction > 0 取枚举中的下一个模式, 越界回绕。 */
    public static WrenchMode cycle(int direction) {
        WrenchMode[] all = WrenchMode.values();
        int idx = current.ordinal() + (direction < 0 ? -1 : 1);
        int len = all.length;
        current = all[((idx % len) + len) % len];
        return current;
    }

    /**
     * 只读地取当前「锁链传动」子模式, 供客户端规划器与预览读取。
     *
     * <p>该值是 Ctrl+滚轮 切换的结果(见 {@link #cycleCtrlOption(int)}), 只存在于客户端;
     * 发送给服务端的请求会单独携带子模式, 服务端不读取本方法。未切换到锁链传动模式时
     * 返回上一次的档位(出厂值为 {@link ChainSubMode#STANDARD}, 不为 {@code null})。</p>
     */
    public static ChainSubMode chainSubMode() {
        return chainSubMode;
    }

    /**
     * 循环切换"当前模式自己的 Ctrl 选项"。
     * 拆除: 拆除范围(全部 / 仅机械动力 / 仅红石); 连接: 拐角类型(齿轮箱 / 大齿轮);
     * 加工: 成品停留时间(不停留 / 短 / 中 / 长); 锁链传动: 子模式(标准 / 直线 / 半自动);
     * 模组描述: 战斗加成(可选)开关(正常 / 战斗);
     * 其它模式返回 {@code null}。
     *
     * <p>返回值语义: 一般是切换后的选项值; 功能被配置关闭时返回当前值(档位不变);
     * 「模组描述」的战斗开关被关闭时返回 {@code null}, 正常路径下返回本地乐观翻转后的值,
     * 随后由服务端权威回包覆盖(见 {@code client/WrenchCombatClient})。</p>
     */
    public static Object cycleCtrlOption(int direction) {
        if (current == WrenchMode.DECONSTRUCT) {
            // 功能被配置关闭: 仅提示并原样返回当前范围档(不改变档位) —— 服务端也会再拦截一次
            if (ClientFeatureGate.blockIfDisabled(WrenchMode.DECONSTRUCT))
                return deconstructScope;
            // 配置里"不允许破坏机械动力/红石方块"时, 范围档被强制锁定(设计约定, 2026-09-25):
            //   不允许机械动力, 即运行于「仅红石」; 不允许红石, 即运行于「仅机械动力」。
            DeconstructScope forced = WrenchConfig.deconstructForcedScope();
            if (forced != null) {
                deconstructScope = forced;
                return forced;
            }
            DeconstructScope[] scopes = DeconstructScope.values();
            int idx = deconstructScope.ordinal() + (direction < 0 ? -1 : 1);
            deconstructScope = scopes[((idx % scopes.length) + scopes.length) % scopes.length];
            return deconstructScope;
        }
        if (current == WrenchMode.CONNECT) {
            if (ClientFeatureGate.blockIfDisabled(WrenchMode.CONNECT))
                return connectCorner;
            ConnectCorner[] corners = ConnectCorner.values();
            int idx = connectCorner.ordinal() + (direction < 0 ? -1 : 1);
            connectCorner = corners[((idx % corners.length) + corners.length) % corners.length];
            return connectCorner;
        }
        if (current == WrenchMode.ASSEMBLE) {
            if (ClientFeatureGate.blockIfDisabled(WrenchMode.ASSEMBLE))
                return assembleStay;
            assembleStay = assembleStay.cycle(direction);
            // 停留时间由服务端执行(弹出延时), 因此切换后必须把新档位同步给服务端
            AssembleStayClient.send();
            return assembleStay;
        }
        if (current == WrenchMode.CHAIN) {
            // 「锁链传动」的三个子模式只在客户端保存: 规划与预览是客户端行为, 真正发送请求时
            // 由载荷携带子模式, 服务端以其收到的值为准重新校验(见 docs/design/chain-mode-spec.md 第 7 节)。
            ChainSubMode[] subs = ChainSubMode.values();
            int idx = chainSubMode.ordinal() + (direction < 0 ? -1 : 1);
            chainSubMode = subs[((idx % subs.length) + subs.length) % subs.length];
            return chainSubMode;
        }
        if (current == WrenchMode.COMING_SOON) {
            // 配置里"是否启用战斗模式"被关掉: 由 ClientFeatureGate 提示 lang key
            // msg.create_better_wrench.feature_disabled, 不改本地开关也不发包
            if (ClientFeatureGate.isCombatDisabled()) {
                ClientFeatureGate.announceCombatDisabled();
                return null;
            }
            // 战斗加成(可选): Ctrl 切换 正常模式 / 战斗模式。
            // 保持"按下即反馈": 本地乐观翻转并立刻由 HUD 显示 actionbar, 与旧实现的表现一致。
            // 同时把"想要的值"发给服务端; 若被权限拒绝, 权威回包到达时会再显示一次正确值把它覆盖掉
            // (见 WrenchCombatClient#onPlayerTick), 因此不会停在"战斗模式"上。
            combatMode = !combatMode;
            WrenchCombatClient.sendCombatModeRequest(combatMode);
            return combatMode;
        }
        return null;
    }

    /** 当前模式 Ctrl 选项的完整展示文案; 无 Ctrl 选项则返回 {@code null}。文案在语言文件: hint.<modid>.*。 */
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
        if (current == WrenchMode.CHAIN)
            return chainSubMode.displayName();
        if (current == WrenchMode.COMING_SOON)
            return net.minecraft.network.chat.Component.translatable(
                "hint." + BetterWrenchMod.MODID + (combatMode ? ".combat.on" : ".combat.off"));
        return null;
    }
}
