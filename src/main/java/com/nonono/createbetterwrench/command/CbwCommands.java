package com.nonono.createbetterwrench.command;

import java.util.Collection;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.combat.WrenchCombat;
import com.nonono.createbetterwrench.combat.WrenchCombatGrant;
import com.nonono.createbetterwrench.network.FeatureSyncServer;
import com.nonono.createbetterwrench.util.ChatFeedback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * 服务端指令 {@code /cbw}(玩家指令的另一半 {@code /cbw config} 在客户端类 {@code client/CbwClientCommands})。
 *
 * <pre>
 *   /cbw version                        查看模组版本
 *   /cbw combat &lt;目标选择器&gt; &lt;true|false&gt;  单独给玩家开/关战斗模式(需要 OP 2 级)
 * </pre>
 *
 * <p>为什么要拆成"服务端 + 客户端"两半: {@code /cbw config} 要打开**客户端**的界面,
 * 而本类在通用代码里, 绝不能引用客户端类(专用服务器会加载它, 见 docs/reference/03-known-issues.md B-1)。</p>
 */
public final class CbwCommands {

    private CbwCommands() {
    }

    /** GAME 总线: {@code RegisterCommandsEvent} → 服务端指令树。 */
    public static void registerServer(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cbw")
            .then(Commands.literal("version")
                .executes(CbwCommands::version))
            .then(Commands.literal("combat")
                // 授权/取消授权 = 管理类操作: 需要 OP 2 级(与模组里其它 "OP" 判定的等级一致)
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(CbwCommands::combat)))));
    }

    /** {@code /cbw version} —— 直接读 mod 容器里的版本号(与 gradle.properties 同源)。 */
    private static int version(CommandContext<CommandSourceStack> ctx) {
        String version = ModList.get()
            .getModContainerById(BetterWrenchMod.MODID)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse("?");
        ctx.getSource().sendSuccess(
            () -> Component.translatable("command." + BetterWrenchMod.MODID + ".version", version), false);
        return 1;
    }

    /** {@code /cbw combat <目标选择器> <true|false>} —— 单独授权; 取消时同时把该玩家的战斗模式关掉。 */
    private static int combat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");

        for (ServerPlayer sp : targets) {
            WrenchCombatGrant.set(sp, enabled);
            if (!enabled) {
                // 立即收回加成(否则他会一直挂着 +5 伤害 / +20 攻速 直到重新登录)
                WrenchCombat.setServer(sp.getUUID(), false);
                WrenchCombat.apply(sp, false);
            }
            // 把新的授权同步给该客户端(它据此决定战斗模式还能不能切)
            FeatureSyncServer.sendTo(sp);
            ChatFeedback.warn(sp, Component.translatable(
                "command." + BetterWrenchMod.MODID + (enabled ? ".combat.granted" : ".combat.revoked")));
        }

        final int count = targets.size();
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "command." + BetterWrenchMod.MODID + (enabled ? ".combat.summary.granted" : ".combat.summary.revoked"), count), true);
        return count;
    }
}
