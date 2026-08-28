package com.uxplima.uxmlib.command.annotation;

import java.lang.reflect.Method;
import java.time.Duration;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmlib.command.annotation.annotations.Cooldown;
import com.uxplima.uxmlib.common.Durations;
import org.jspecify.annotations.Nullable;

/**
 * Derives the implicit {@link CommandCondition} for a {@code @}{@link Cooldown} on a branch, the way the
 * player-only gate is folded in. The condition keys {@link Cooldowns} by the command path and the
 * player's UUID, so each branch and player cool down independently; a console sender has no UUID and is
 * never gated. On an active window it vetoes with the remaining time rendered by {@link Durations#format}.
 * Built once at registration (the duration is parsed up front so a malformed value fails fast).
 */
final class CooldownCondition {

    private CooldownCondition() {}

    /**
     * The cooldown condition for {@code branch} under {@code commandPath}, or {@code null} when neither the
     * branch method nor its declaring class effectively carries {@code @Cooldown}. The annotation is read off
     * the branch's effective views, so a replacer that synthesises {@code @Cooldown} gates the branch too.
     *
     * @throws CommandParseException if the {@code @Cooldown} duration is not a valid human duration
     */
    static @Nullable CommandCondition forBranch(BranchModel branch, String commandPath, ParamResolvers resolvers) {
        Cooldown cooldown = effective(branch);
        if (cooldown == null) {
            return null;
        }
        long durationMillis = parse(cooldown, branch.method());
        String keyPrefix = commandPath + '|';
        return ctx -> test(ctx, keyPrefix, durationMillis, resolvers);
    }

    private static @Nullable Cooldown effective(BranchModel branch) {
        Cooldown onMethod = branch.methodView().get(Cooldown.class);
        return onMethod != null ? onMethod : branch.classView().get(Cooldown.class);
    }

    private static long parse(Cooldown cooldown, Method method) {
        try {
            return Durations.parse(cooldown.value()).toMillis();
        } catch (IllegalArgumentException bad) {
            throw new CommandParseException(
                    "@Cooldown on " + method.getName() + " has an invalid duration '" + cooldown.value() + "'", bad);
        }
    }

    private static void test(
            CommandContext<CommandSourceStack> ctx, String keyPrefix, long durationMillis, ParamResolvers resolvers) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            return; // the console and command blocks have no per-player identity to rate-limit
        }
        long remaining = resolvers.cooldowns().check(keyPrefix + player.getUniqueId(), durationMillis);
        if (remaining > 0L) {
            // The time left travels as a Duration, not as "3m 20s": no two languages spell or order the parts
            // the same way, so the wording is the message layer's to choose.
            throw new CommandCondition.CommandConditionException(resolvers
                    .messages()
                    .onCooldown(resolvers.locales().localeOf(player), Duration.ofMillis(remaining)));
        }
    }
}
