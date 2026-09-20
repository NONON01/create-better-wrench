package com.nonono.createbetterwrench.client;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.combat.CombatModeState;
import com.nonono.createbetterwrench.combat.WrenchCombat;
import com.nonono.createbetterwrench.network.CombatModePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端: 本地玩家按战斗模式开关应用加成(与 HUD 显示一致); 进入世界时把开关同步给服务端。
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class WrenchCombatClient {

    private WrenchCombatClient() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = event.getEntity();
        if (player != mc.player)
            return;
        // 落地服务端回传的**权威**开关;若是玩家自己发起的切换, 就用权威结果**再显示一次** actionbar。
        // ⚠️ 2026-09-20 修 bug: 客户端按下时是**乐观翻转 + 立刻显示**(保持原有手感), 无权限时那条
        //    "战斗模式" 会挂在 actionbar 上 2~3 秒不动 ⇒ 用户看到"文本卡死在战斗模式"。
        //    现在:权威回包到达后立刻用**正确值**(此时是"正常模式")覆盖它, 于是既保留即时反馈,
        //    也不会停在错误状态。聊天栏那边的 [CBW]: 拒绝提示由服务端单独发出, 两者同时出现。
        CombatModeState.Sync synced = CombatModeState.poll();
        if (synced != null) {
            WrenchModeSwitcher.combatMode = synced.combat();
            if (synced.announce())
                WrenchHud.showCtrlOptionHint();
        }

        WrenchCombat.apply(player, WrenchCombat.holdsWrench(player) && WrenchModeSwitcher.combatMode);
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        // 进世界时的自动同步: notify=false, 免得一进游戏就冒出一条无意义的提示
        sendCombatMode();
    }

    /** 进世界时把**当前**开关同步给服务端(不要求提示)。 */
    public static void sendCombatMode() {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn != null)
            PacketDistributor.sendToServer(new CombatModePayload(WrenchModeSwitcher.combatMode, false));
    }

    /**
     * 玩家主动切换战斗模式时调用: 只把**想要的值**发给服务端, **不改本地开关、不显示提示** ——
     * 等权威回包到达后由 {@link #onPlayerTick} 统一处理(见那里的说明)。
     */
    public static void sendCombatModeRequest(boolean wanted) {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn != null)
            PacketDistributor.sendToServer(new CombatModePayload(wanted, true));
    }
}
