package com.nonono.createbetterwrench.client;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.network.AssembleStayPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端: 把「加工」模式当前选的**成品停留时间**同步给服务端。
 *
 * <p>弹出延时由服务端决定({@code DepotProductEjector}), 而"加工"本身是一条普通右键、没有自定义包,
 * 所以必须在**切换选项时**以及**进入世界时**把值发过去(与战斗模式的同步方式一致)。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class AssembleStayClient {

    private AssembleStayClient() {
    }

    /** 把当前档位的 tick 数发给服务端(切换时 / 进入世界时)。 */
    public static void send() {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn != null)
            PacketDistributor.sendToServer(new AssembleStayPayload(WrenchModeSwitcher.assembleStay.ticks()));
    }

    /** 进入世界时补发一次: 服务端只存内存, 重连后要重新同步。 */
    @EventBusSubscriber(modid = BetterWrenchMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class Join {
        private Join() {
        }

        @SubscribeEvent
        public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
            send();
        }
    }
}
