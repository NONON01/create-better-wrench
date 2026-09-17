package com.nonono.createbetterwrench.assemble;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.nonono.createbetterwrench.mode.AssembleStay;

/**
 * 服务端: 每个玩家选的「**成品停留时间**」(由客户端发包同步, 见 {@code network/AssembleStayPayload})。
 *
 * <p>为什么需要它: 弹出延时是**服务端**逻辑(见 {@link DepotProductEjector}), 而"加工"是在
 * {@code PlayerInteractEvent.RightClickBlock} 上触发的 —— 那是一条普通右键, 没有自定义包携带客户端选项,
 * 所以只能像战斗模式那样**先把选择同步到服务端存着**。</p>
 *
 * <p>收到同步前 / 玩家从未调过时用 {@link AssembleStay#DEFAULT} 兜底, 因此即使客户端一个包都没发,
 * 服务端行为也与默认档一致。</p>
 */
public final class DepotStayState {

    private static final Map<UUID, Integer> STAY_TICKS = new HashMap<>();

    private DepotStayState() {
    }

    /** 记录某玩家选的停留 tick 数(已由调用方夹过范围)。 */
    public static void set(UUID playerId, int ticks) {
        STAY_TICKS.put(playerId, AssembleStay.clampTicks(ticks));
    }

    /** 取某玩家的停留 tick 数; 没同步过则返回默认档。 */
    public static int get(UUID playerId) {
        return STAY_TICKS.getOrDefault(playerId, AssembleStay.DEFAULT.ticks());
    }

    /** 玩家登出时清掉, 避免长年运行的服务端无上限累积 UUID。 */
    public static void clear(UUID playerId) {
        STAY_TICKS.remove(playerId);
    }
}
