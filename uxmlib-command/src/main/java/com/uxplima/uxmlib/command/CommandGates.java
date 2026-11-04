package com.uxplima.uxmlib.command;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
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
     * @throws IllegalArgumentException when a node at or below a gate redirects: the command it reaches runs in a
     *     context of its own, past every gate before the redirect
     */
    public static LiteralCommandNode<CommandSourceStack> seal(
            LiteralCommandNode<CommandSourceStack> root, Refusal refusal) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(refusal, "refusal");
        @SuppressWarnings("unchecked") // a literal node's builder is a literal builder
        LiteralArgumentBuilder<CommandSourceStack> builder =
                (LiteralArgumentBuilder<CommandSourceStack>) rebuild(root, List.of(), refusal);
        return builder.build();
    }

    private static ArgumentBuilder<CommandSourceStack, ?> rebuild(
            CommandNode<CommandSourceStack> node, List<Gate> above, Refusal refusal) {
        List<Gate> path = withOwnGate(node, above);
        if (node.getRedirect() != null && !path.isEmpty()) {
            throw new IllegalArgumentException("the gated command node '" + node.getName()
                    + "' redirects; a redirect leaves its gates behind, so gate the node it reaches instead");
        }
        ArgumentBuilder<CommandSourceStack, ?> builder;
        if (node instanceof ArgumentCommandNode<CommandSourceStack, ?> argument) {
            RequiredArgumentBuilder<CommandSourceStack, ?> required = argument.createBuilder();
            SuggestionProvider<CommandSourceStack> suggestions = argument.getCustomSuggestions();
            if (suggestions != null && !path.isEmpty()) {
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
            builder.then(rebuild(child, path, refusal));
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
     * Remove from {@code sent}, the root of a command list about to go to {@code sender}, every node under the
     * labels in {@code labels} whose gate in {@code registered} does not show itself to them. Nodes are matched by
     * name, level by level; a node {@code registered} does not know is left alone.
     */
    public static void prune(
            CommandNode<?> sent, Set<String> labels, CommandNode<CommandSourceStack> registered, CommandSender sender) {
        Objects.requireNonNull(sent, "sent");
        Objects.requireNonNull(labels, "labels");
        Objects.requireNonNull(registered, "registered");
        Objects.requireNonNull(sender, "sender");
        List<String> hidden = new ArrayList<>();
        for (CommandNode<?> command : sent.getChildren()) {
            if (!labels.contains(command.getName())) {
                continue;
            }
            if (Gate.of(registered.getRequirement()).shows(sender)) {
                pruneBelow(command, registered, sender);
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
