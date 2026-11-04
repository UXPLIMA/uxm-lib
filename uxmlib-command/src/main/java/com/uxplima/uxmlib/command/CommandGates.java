package com.uxplima.uxmlib.command;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.jspecify.annotations.Nullable;

/**
 * Makes the {@link Gate}s of a command tree hold.
 *
 * <p>{@link #seal} rebuilds a tree so that every executor and every suggestion provider in it first asks each gate on
 * its own path from the root, and answers the first refusal with the plugin's {@link Refusal} instead of running.
 * The gates on a path are read once, when the tree is sealed, and kept with the executor, so the check does not
 * depend on which nodes a parse visited: a server alias that redirects to the root still passes the root's gate.
 *
 * <p>{@link #prune} takes out of a command list a client is about to receive every node whose gate does not show
 * itself to that player. {@link GateVisibility} calls it for the trees a plugin registered.
 */
public final class CommandGates {

    /**
     * {@code CommandNode.removeCommand}, which the server's Brigadier has and the published one does not. Without it,
     * a child is taken out through the children view, which is what a client's command list is written from.
     */
    private static final @Nullable MethodHandle REMOVE_COMMAND = removeCommand();

    private CommandGates() {}

    private static @Nullable MethodHandle removeCommand() {
        try {
            return MethodHandles.publicLookup()
                    .findVirtual(CommandNode.class, "removeCommand", MethodType.methodType(void.class, String.class));
        } catch (NoSuchMethodException | IllegalAccessException absent) {
            return null;
        }
    }

    private static void remove(CommandNode<?> parent, String name) {
        MethodHandle removeCommand = REMOVE_COMMAND;
        if (removeCommand != null) {
            try {
                removeCommand.invoke(parent, name);
                return;
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable unexpected) {
                throw new IllegalStateException("could not remove command node " + name, unexpected);
            }
        }
        parent.getChildren().removeIf(child -> child.getName().equals(name));
    }

    /** What a sender is told when a gate refuses them: the plugin's own line in their language. */
    @FunctionalInterface
    public interface Refusal {

        /** Tell {@code source}'s sender that {@code gate} refuses them. */
        void refuse(CommandSourceStack source, Gate gate);
    }

    /**
     * {@code root} rebuilt with every executor and suggestion provider behind the gates on its path. The nodes keep
     * their names, gates, argument types and children; a tree without gates comes back with the same behavior.
     *
     * @throws IllegalArgumentException when the tree has a gate and any node of it redirects: the command a redirect
     *     reaches runs in a context of its own, past every gate before the redirect, and the node it points at is the
     *     one given here, not the sealed copy
     */
    public static LiteralCommandNode<CommandSourceStack> seal(
            LiteralCommandNode<CommandSourceStack> root, Refusal refusal) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(refusal, "refusal");
        @SuppressWarnings("unchecked") // a literal node's builder is a literal builder
        LiteralArgumentBuilder<CommandSourceStack> builder =
                (LiteralArgumentBuilder<CommandSourceStack>) rebuild(root, List.of(), refusal, hasGates(root));
        return builder.build();
    }

    private static ArgumentBuilder<CommandSourceStack, ?> rebuild(
            CommandNode<CommandSourceStack> node, List<Gate> above, Refusal refusal, boolean gated) {
        List<Gate> path = withOwnGate(node, above);
        if (gated && node.getRedirect() != null) {
            throw new IllegalArgumentException("the command node '" + node.getName()
                    + "' redirects in a tree with gates; a redirect runs past the gates before it and points at a node"
                    + " the seal does not cover, so spell the branch out instead");
        }
        ArgumentBuilder<CommandSourceStack, ?> builder;
        if (node instanceof ArgumentCommandNode<CommandSourceStack, ?> argument) {
            RequiredArgumentBuilder<CommandSourceStack, ?> required = argument.createBuilder();
            if (!path.isEmpty()) {
                // Every argument below a gate answers through it, its type's own suggestions included, so a client
                // that asks for what it was never sent learns nothing.
                SuggestionProvider<CommandSourceStack> own = argument.getCustomSuggestions();
                SuggestionProvider<CommandSourceStack> suggestions = own != null
                        ? own
                        : (context, completions) -> argument.getType().listSuggestions(context, completions);
                required.suggests(guarded(suggestions, path));
            }
            builder = required;
        } else {
            builder = node.createBuilder();
        }
        Command<CommandSourceStack> command = node.getCommand();
        if (command != null && !path.isEmpty()) {
            builder.executes(guarded(command, path, refusal));
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            builder.then(rebuild(child, path, refusal, gated));
        }
        return builder;
    }

    private static List<Gate> withOwnGate(CommandNode<CommandSourceStack> node, List<Gate> above) {
        if (!(node.getRequirement() instanceof Gate own)) {
            return above;
        }
        List<Gate> path = new ArrayList<>(above.size() + 1);
        path.addAll(above);
        path.add(own);
        return List.copyOf(path);
    }

    private static Command<CommandSourceStack> guarded(
            Command<CommandSourceStack> command, List<Gate> path, Refusal refusal) {
        return context -> {
            Gate refused = firstRefusing(path, context.getSource().getSender());
            if (refused != null) {
                refusal.refuse(context.getSource(), refused);
                return 0;
            }
            return command.run(context);
        };
    }

    private static SuggestionProvider<CommandSourceStack> guarded(
            SuggestionProvider<CommandSourceStack> suggestions, List<Gate> path) {
        return (context, builder) -> firstRefusing(path, context.getSource().getSender()) != null
                ? builder.buildFuture()
                : suggestions.getSuggestions(context, builder);
    }

    private static @Nullable Gate firstRefusing(List<Gate> path, CommandSender sender) {
        for (Gate gate : path) {
            if (!gate.allows(sender)) {
                return gate;
            }
        }
        return null;
    }

    /**
     * Whether {@code source}'s sender is shown {@code node}: its gate's answer, or Brigadier's own for a node without a
     * gate. Code that walks a tree to list what a sender may see (help pages, usage lines) asks this, never {@code
     * canUse}, which a gate always passes.
     */
    public static boolean shows(CommandNode<CommandSourceStack> node, CommandSourceStack source) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(source, "source");
        return node.getRequirement() instanceof Gate gate ? gate.shows(source.getSender()) : node.canUse(source);
    }

    /** Whether any node of {@code root}, the root included, carries a {@link Gate}. */
    public static boolean hasGates(CommandNode<CommandSourceStack> root) {
        Objects.requireNonNull(root, "root");
        if (root.getRequirement() instanceof Gate) {
            return true;
        }
        for (CommandNode<CommandSourceStack> child : root.getChildren()) {
            if (hasGates(child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code offered} without the words a gate hides from {@code sender}: the server lists every child of a node as a
     * completion, so a word kept out of the command list would otherwise come back as a suggestion. {@code buffer} is
     * what the player typed; a suggestion is judged by the command path it completes, walked through the tree {@code
     * registered} holds for its first word, as {@link #prune} reads it.
     */
    public static Suggestions visibleSuggestions(
            String buffer,
            Suggestions offered,
            Map<String, ? extends CommandNode<CommandSourceStack>> registered,
            CommandSender sender) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(offered, "offered");
        Objects.requireNonNull(registered, "registered");
        Objects.requireNonNull(sender, "sender");
        List<Suggestion> kept = new ArrayList<>();
        for (Suggestion suggestion : offered.getList()) {
            int start = Math.min(suggestion.getRange().getStart(), buffer.length());
            if (shows(buffer.substring(0, start) + suggestion.getText(), registered, sender)) {
                kept.add(suggestion);
            }
        }
        return kept.size() == offered.getList().size() ? offered : new Suggestions(offered.getRange(), kept);
    }

    private static boolean shows(
            String typed, Map<String, ? extends CommandNode<CommandSourceStack>> registered, CommandSender sender) {
        String line = typed.startsWith("/") ? typed.substring(1) : typed;
        String[] words = line.split(" ", -1);
        CommandNode<CommandSourceStack> node = words.length == 0 ? null : registered.get(words[0]);
        if (node == null) {
            return true;
        }
        if (!Gate.of(node.getRequirement()).shows(sender)) {
            return false;
        }
        for (int i = 1; i < words.length; i++) {
            CommandNode<CommandSourceStack> next = literalChild(node, words[i]);
            if (next == null) {
                next = onlyArgumentChild(node);
            }
            if (next == null) {
                return true;
            }
            if (!Gate.of(next.getRequirement()).shows(sender)) {
                return false;
            }
            node = next;
        }
        return true;
    }

    private static @Nullable CommandNode<CommandSourceStack> literalChild(
            CommandNode<CommandSourceStack> node, String word) {
        CommandNode<CommandSourceStack> child = node.getChild(word);
        return child instanceof LiteralCommandNode<CommandSourceStack> ? child : null;
    }

    private static @Nullable CommandNode<CommandSourceStack> onlyArgumentChild(CommandNode<CommandSourceStack> node) {
        CommandNode<CommandSourceStack> only = null;
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            if (child instanceof ArgumentCommandNode<CommandSourceStack, ?>) {
                if (only != null) {
                    return null;
                }
                only = child;
            }
        }
        return only;
    }

    /**
     * Remove from {@code sent}, the root of a command list about to go to {@code sender}, every node whose gate does not
     * show itself to them. {@code registered} maps each label a command is sent under (its name, its aliases, each with
     * its namespace) to the tree registered for it; nodes are matched by name, level by level, and a command or node
     * {@code registered} does not know is left alone.
     */
    public static void prune(
            CommandNode<?> sent,
            Map<String, ? extends CommandNode<CommandSourceStack>> registered,
            CommandSender sender) {
        Objects.requireNonNull(sent, "sent");
        Objects.requireNonNull(registered, "registered");
        Objects.requireNonNull(sender, "sender");
        List<String> hidden = new ArrayList<>();
        for (CommandNode<?> command : sent.getChildren()) {
            CommandNode<CommandSourceStack> ours = registered.get(command.getName());
            if (ours == null) {
                continue;
            }
            if (Gate.of(ours.getRequirement()).shows(sender)) {
                pruneBelow(command, ours, sender);
            } else {
                hidden.add(command.getName());
            }
        }
        hidden.forEach(name -> remove(sent, name));
    }

    private static void pruneBelow(
            CommandNode<?> sent, CommandNode<CommandSourceStack> registered, CommandSender sender) {
        List<String> hidden = new ArrayList<>();
        for (CommandNode<?> child : sent.getChildren()) {
            CommandNode<CommandSourceStack> match = registered.getChild(child.getName());
            if (match == null) {
                continue;
            }
            if (Gate.of(match.getRequirement()).shows(sender)) {
                pruneBelow(child, match, sender);
            } else {
                hidden.add(child.getName());
            }
        }
        hidden.forEach(name -> remove(sent, name));
    }
}
