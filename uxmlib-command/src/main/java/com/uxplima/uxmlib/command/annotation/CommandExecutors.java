package com.uxplima.uxmlib.command.annotation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import net.kyori.adventure.text.Component;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmlib.command.Cmd;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * Builds the Brigadier {@code executes} body for a {@code @}{@link
 * com.uxplima.uxmlib.command.annotation.annotations.Subcommand} method: run the pre-execute
 * {@link CommandCondition}s, bind the arguments through {@link ArgBinder}, invoke the handler, and route
 * every failure to a clean red reply rather than a leaked Brigadier stacktrace. Split out of
 * {@code AnnotatedCommands} so that class stays within its size budget and keeps to tree building.
 */
final class CommandExecutors {

    private CommandExecutors() {}

    /**
     * The executor for {@code method} with its pre-resolved {@code args} and the active {@code resolvers}.
     * {@code commandPath} is the full literal path of this branch (root name plus subcommand spine); it
     * keys the per-branch cooldown so different branches never share a window. {@code scheduler} routes the
     * completion of an async ({@link CompletableFuture}-returning) handler back onto a Bukkit-safe thread.
     */
    static com.mojang.brigadier.Command<CommandSourceStack> executorFor(
            Object handler,
            BranchModel branch,
            List<ArgBinder.ParamArg> args,
            List<FlagModel> flags,
            ParamResolvers resolvers,
            String commandPath,
            Scheduler scheduler) {
        Method method = branch.method();
        List<CommandCondition> conditions = conditionsFor(branch, resolvers, commandPath);
        boolean async = AsyncCompletion.isAsync(method.getReturnType());
        CommandMessages messages = resolvers.messages();
        return ctx -> {
            Locale locale = localeOf(resolvers, ctx);
            try {
                checkConditions(conditions, ctx);
            } catch (CommandCondition.CommandConditionException vetoed) {
                reply(ctx, vetoed.message());
                return 0;
            }
            Object[] callArgs;
            try {
                callArgs = ArgBinder.bind(ctx, method, args, flags, resolvers);
            } catch (CommandCondition.CommandConditionException vetoed) {
                // A context parameter or resolver refused outright rather than rejecting one value.
                reply(ctx, vetoed.message());
                return 0;
            } catch (ArgumentResolveException typed) {
                // A resolver/validator rejected one argument; reply naming which argument and the bad input.
                reply(ctx, rejection(messages, locale, typed.context()));
                return 0;
            } catch (IllegalArgumentException badArgument) {
                // A rejection with no per-argument context (a flag value, say). Reply with its flat message
                // rather than letting it surface as a server error.
                String detail = badArgument.getMessage();
                reply(ctx, messages.invalidArgument(locale, detail == null ? "" : detail));
                return 0;
            }
            return invoke(handler, method, callArgs, ctx, async, scheduler, messages, locale);
        };
    }

    /** The locale the sender of this dispatch is answered in. */
    private static Locale localeOf(ParamResolvers resolvers, CommandContext<CommandSourceStack> ctx) {
        return resolvers.locales().localeOf(ctx.getSource().getSender());
    }

    /**
     * Render a per-argument rejection. A rejection that knows the whole accepted set is handed to the
     * dedicated method, so a message layer words "not one of these" itself rather than translating a sentence.
     */
    private static Component rejection(CommandMessages messages, Locale locale, ErrorContext context) {
        if (!context.allowed().isEmpty()) {
            return messages.notOneOf(locale, context.argument(), context.input(), context.allowed());
        }
        return messages.invalidValue(locale, context.argument(), context.input(), context.reason());
    }

    private static int invoke(
            Object handler,
            Method method,
            Object[] callArgs,
            CommandContext<CommandSourceStack> ctx,
            boolean async,
            Scheduler scheduler,
            CommandMessages messages,
            Locale locale) {
        try {
            Object returned = method.invoke(handler, callArgs);
            if (async) {
                routeAsync(returned, method, ctx, scheduler, messages, locale);
            }
            return Cmd.OK;
        } catch (InvocationTargetException thrownByHandler) {
            reportError(method, ctx, thrownByHandler.getCause(), thrownByHandler, messages, locale);
            return 0;
        } catch (IllegalAccessException unreachable) {
            // setAccessible(true) ran at registration, so this cannot happen for a registered handler.
            throw new CommandParseException("could not invoke " + method.getName(), unreachable);
        }
    }

    /**
     * Hand the handler's future to {@link AsyncCompletion} so an exceptional completion is logged server-side
     * immediately and replied to on the sender's thread. A handler that (wrongly) returns {@code null} is
     * treated as already done. The log runs off the scheduler hop so a dropped reply hop (the player logged
     * off before a genuinely async future settled) never erases the operator's record of the failure.
     */
    private static void routeAsync(
            Object returned,
            Method method,
            CommandContext<CommandSourceStack> ctx,
            Scheduler scheduler,
            CommandMessages messages,
            Locale locale) {
        CompletableFuture<?> future = AsyncCompletion.asFuture(returned);
        if (future == null) {
            return;
        }
        AsyncCompletion.route(
                future,
                scheduler,
                ctx.getSource(),
                cause -> logError(method, ctx, cause, cause),
                cause -> reply(ctx, messages.internalError(locale)));
    }

    /**
     * The uniform handler-failure path: log the real cause server-side and reply with a clean, generic
     * message instead of letting Brigadier dump a red stacktrace in the player's chat. Used by the
     * synchronous invoke path, where both halves run on the dispatch thread.
     */
    private static void reportError(
            Method method,
            CommandContext<CommandSourceStack> ctx,
            @org.jspecify.annotations.Nullable Throwable cause,
            Throwable fallback,
            CommandMessages messages,
            Locale locale) {
        logError(method, ctx, cause, fallback);
        reply(ctx, messages.internalError(locale));
    }

    /** Log the real (sanitized) cause server-side at {@code SEVERE}. Thread-safe; needs no Bukkit API thread. */
    private static void logError(
            Method method,
            CommandContext<CommandSourceStack> ctx,
            @org.jspecify.annotations.Nullable Throwable cause,
            Throwable fallback) {
        Throwable logged = StackTraceSanitizer.sanitize(cause != null ? cause : fallback);
        ctx.getSource()
                .getSender()
                .getServer()
                .getLogger()
                .log(Level.SEVERE, "Command '" + method.getName() + "' threw an exception", logged);
    }

    /** Send {@code message} to the dispatch's sender: the uniform clean-error reply path. */
    private static void reply(CommandContext<CommandSourceStack> ctx, Component message) {
        Sender.of(ctx.getSource()).send(message);
    }

    /**
     * The conditions to run before {@code method}: the registry conditions, plus the implicit gates derived
     * from a method- or class-level {@code @}{@link PlayerOnly} and {@code @}{@link
     * com.uxplima.uxmlib.command.annotation.annotations.Cooldown}. Folding both into the condition seam lets
     * them compose with consumer-registered conditions the same way.
     */
    private static List<CommandCondition> conditionsFor(
            BranchModel branch, ParamResolvers resolvers, String commandPath) {
        List<CommandCondition> conditions = new ArrayList<>(resolvers.conditions());
        if (branch.methodView().isPresent(PlayerOnly.class)
                || branch.classView().isPresent(PlayerOnly.class)) {
            conditions.add(playerOnlyCondition(resolvers));
        }
        CommandCondition cooldown = CooldownCondition.forBranch(branch, commandPath, resolvers);
        if (cooldown != null) {
            conditions.add(cooldown);
        }
        return conditions;
    }

    private static CommandCondition playerOnlyCondition(ParamResolvers resolvers) {
        return ctx -> {
            if (!(ctx.getSource().getSender() instanceof org.bukkit.entity.Player)) {
                throw new CommandCondition.CommandConditionException(
                        resolvers.messages().playerOnly(localeOf(resolvers, ctx)));
            }
        };
    }

    private static void checkConditions(List<CommandCondition> conditions, CommandContext<CommandSourceStack> ctx) {
        for (CommandCondition condition : conditions) {
            condition.test(ctx);
        }
    }
}
