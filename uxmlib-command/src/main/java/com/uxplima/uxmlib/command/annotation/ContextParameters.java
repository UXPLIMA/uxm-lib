package com.uxplima.uxmlib.command.annotation;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.uxplima.uxmlib.command.Sender;

/**
 * The context parameters shipped with the DSL: the four sender-shaped types a handler may inject without an
 * {@code @}{@link com.uxplima.uxmlib.command.annotation.annotations.Arg} — the library {@link Sender}, the
 * raw {@link CommandSourceStack}, the Bukkit {@link CommandSender}, and the executing {@link Player}.
 * Installed into a {@link ParamResolvers} by {@link ParamResolvers#withDefaults()} so the injection set is an
 * open registry rather than a hardcoded {@code instanceof} ladder; a consumer adds its own with
 * {@link ParamResolvers#context}.
 */
final class ContextParameters {

    private ContextParameters() {}

    static void installInto(ParamResolvers r) {
        r.context(Sender.class, ctx -> Sender.of(ctx.getSource()));
        r.context(CommandSourceStack.class, com.mojang.brigadier.context.CommandContext::getSource);
        r.context(CommandSender.class, ctx -> ctx.getSource().getSender());
        r.context(Player.class, ctx -> requirePlayer(ctx, r));
    }

    /**
     * The sender as a Player, or a refusal shown to the sender, from console. It refuses the way a condition
     * does rather than as a rejected argument, because no argument was wrong: the sender was.
     */
    private static Player requirePlayer(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, ParamResolvers resolvers) {
        if (ctx.getSource().getSender() instanceof Player player) {
            return player;
        }
        throw new CommandCondition.CommandConditionException(
                resolvers.messages().playerOnly(resolvers.locales().localeOf(ctx.getSource().getSender())));
    }
}
