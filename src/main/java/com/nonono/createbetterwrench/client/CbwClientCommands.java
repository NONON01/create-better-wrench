package com.nonono.createbetterwrench.client;

import com.mojang.brigadier.CommandDispatcher;
import com.nonono.createbetterwrench.client.gui.WrenchConfigScreen;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 客户端指令 {@code /cbw config}: 打开本模组<b>自绘的配置页面</b>。
 *
 * <p>2026-09-25(设计约定): 配置页面<b>不再用快捷键打开</b>(原来的 B + C 组合键已删除), 改用指令。
 * 放在客户端类里是因为它要打开的是客户端界面; 服务端那一半见 {@code command/CbwCommands}。</p>
 *
 * <p>单人游戏与专用服务器都可用: 客户端指令本地执行(专用服务器上页面是<b>只读</b>的,
 * 因为 SERVER 配置由服务端持有; 该提示由页面自己给出)。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class CbwClientCommands {

    private CbwClientCommands() {
    }

    /** GAME 总线(客户端): 订阅 {@code RegisterClientCommandsEvent}, 注册 {@code /cbw config}。 */
    public static void registerClient(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cbw")
            .then(Commands.literal("config")
                .executes(ctx -> {
                    net.minecraft.client.Minecraft.getInstance()
                        .setScreen(WrenchConfigScreen.create(null));
                    return 1;
                })));
    }
}
