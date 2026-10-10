package com.uxplima.uxmlib.command.annotation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.CommandRegistrar;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.scheduler.PaperScheduler;
import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * Turns an {@code @}{@link Command} handler into a registered Brigadier command. The work is split into two
 * decoupled stages: {@link CommandModels} reflects the handler into a platform-neutral {@link CommandModel}
 * (a tree of nodes carrying names, permissions, ordered arguments and flags, conditions and the invoker),
 * and {@link BrigadierRenderer} walks that model to emit the Brigadier tree. This class is only the thin
 * entry point that wires the two together and registers the result. Each {@code @}{@link Subcommand} method
 * becomes a branch under the root literal; its {@code @}{@link Arg} parameters become typed arguments, its
 * {@code @}{@link com.uxplima.uxmlib.command.annotation.annotations.Flag}/{@code @}{@link
 * com.uxplima.uxmlib.command.annotation.annotations.Switch} parameters a trailing flag node; a leading
 * {@link Sender} or {@link CommandSourceStack} is injected; and {@code @}{@link Permission} becomes a
 * {@code requires} gate. Malformed handlers fail at registration with a {@link CommandParseException}.
 * <pre>{@code
 * @Command(name = "money")
 * class MoneyCommand {
 *     @Subcommand("pay") @Permission("money.pay")
 *     void pay(Sender s, @Arg("target") Player t, @Arg(value = "amount", min = 1) int n) { ... }
 * }
 * AnnotatedCommands.register(plugin, new MoneyCommand());
 * }</pre>
 */
public final class AnnotatedCommands {

    private AnnotatedCommands() {}

    /** Reflect over {@code handler} with the default resolvers, build its tree, and register it. */
    public static void register(JavaPlugin plugin, Object handler) {
        register(plugin, handler, ParamResolvers.withDefaults());
    }

    /** Reflect over {@code handler} with {@code resolvers}, build its tree, and register it. */
    public static void register(JavaPlugin plugin, Object handler, ParamResolvers resolvers) {
        Objects.requireNonNull(plugin, "plugin");
        register(plugin, handler, resolvers, new PaperScheduler(plugin));
    }

    /**
     * Reflect over {@code handler} with {@code resolvers}, build its tree, and register it, using
     * {@code scheduler} to route the completion of any async ({@link java.util.concurrent.CompletableFuture
     * CompletableFuture}-returning) branch back onto a Bukkit-safe thread.
     */
    public static void register(JavaPlugin plugin, Object handler, ParamResolvers resolvers, Scheduler scheduler) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(resolvers, "resolvers");
        Objects.requireNonNull(scheduler, "scheduler");
        CommandModel model = CommandModels.reflect(handler, resolvers);
        CommandRegistrar.register(
                plugin,
                new BrigadierRenderer(resolvers, scheduler).render(model),
                model.command().description(),
                List.of(model.command().aliases()));
        watchLocalizedWords(plugin, model, resolvers);
    }

    /**
     * Listen for the command lists a client is sent, when this command has a word for some languages.
     *
     * <p>One listener per such command, holding its own words, so nothing is shared between registrations
     * and a command with no such word costs nothing.
     */
    private static void watchLocalizedWords(JavaPlugin plugin, CommandModel model, ParamResolvers resolvers) {
        Map<String, List<String>> rootWords = new LinkedHashMap<>();
        for (String raw : model.command().localizedAliases()) {
            LocaleTags.entry(raw).ifPresent(entry -> {
                List<String> tags = rootWords.computeIfAbsent(entry.word(), ignored -> new ArrayList<>());
                if (!tags.contains(entry.tag())) {
                    tags.add(entry.tag());
                }
            });
        }
        boolean branchWords = model.branches().stream()
                .anyMatch(branch -> !branch.localizedAliases().isEmpty());
        if (rootWords.isEmpty() && !branchWords) {
            return;
        }
        plugin.getServer()
                .getPluginManager()
                .registerEvents(
                        new LocalizedAliasVisibility(rootWords, plugin.getName(), resolvers.locales(), plugin), plugin);
    }

    /** Build a handler's Brigadier tree with the default resolvers, without registering it. */
    public static LiteralCommandNode<CommandSourceStack> buildNode(Object handler) {
        return buildNode(handler, ParamResolvers.withDefaults());
    }

    /**
     * Build the Brigadier tree for an annotated {@code handler} without registering it, using
     * {@code resolvers} for its argument types. Exposed so the tree shape can be inspected and tested.
     * Async branches built this way route their completion on the calling thread (no live server here).
     */
    public static LiteralCommandNode<CommandSourceStack> buildNode(Object handler, ParamResolvers resolvers) {
        return buildNode(handler, resolvers, new SameThreadScheduler());
    }

    /**
     * Build the Brigadier tree for {@code handler} with {@code resolvers} and the {@code scheduler} that
     * async branches use to route their completion. Exposed so the async wiring can be inspected and tested
     * with a synchronous scheduler double, without a live server.
     */
    public static LiteralCommandNode<CommandSourceStack> buildNode(
            Object handler, ParamResolvers resolvers, Scheduler scheduler) {
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(resolvers, "resolvers");
        Objects.requireNonNull(scheduler, "scheduler");
        CommandModel model = CommandModels.reflect(handler, resolvers);
        return new BrigadierRenderer(resolvers, scheduler).render(model);
    }
}
