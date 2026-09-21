package com.uxplima.uxmlib.command;

import java.util.Objects;
import java.util.function.Predicate;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

/**
 * Node-builder factories bound to {@link CommandSourceStack}. Paper's {@code Commands.literal} /
 * {@code Commands.argument} already return the right builders, but callers otherwise have to spell out
 * {@code <CommandSourceStack>} repeatedly and fight generic inference; these delegate so the source type
 * is implicit. {@link #OK} is Brigadier's success code, returned from an {@code executes} handler.
 */
public final class Cmd {

    /** The integer an {@code executes} handler returns on success. */
    public static final int OK = Command.SINGLE_SUCCESS;

    private Cmd() {}

    /** A literal (keyword) command node, e.g. {@code Cmd.literal("home")}. */
    public static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        Objects.requireNonNull(name, "name");
        return Commands.literal(name);
    }

    /** A typed argument node, e.g. {@code Cmd.argument("amount", IntegerArgumentType.integer(1))}. */
    public static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        return Commands.argument(name, type);
    }

    /**
     * A {@code requires} predicate that decides whether this branch is there at all.
     *
     * <p>A sender who holds the node always sees it. A sender who does not sees it anyway when the node is
     * one the descriptor declares {@code default: true}, because that is how a plugin says a verb belongs
     * to every player: hiding it from the player who lost it reads as a broken plugin rather than as a
     * locked feature, and they are then told, by the refusal the executor sends. Every other node stays
     * hidden, so nobody learns an operator's verb exists by being refused it.
     */
    public static Predicate<CommandSourceStack> visibility(String permission) {
        Objects.requireNonNull(permission, "permission");
        return source -> source.getSender().hasPermission(permission) || meantForEveryone(permission);
    }

    /**
     * Whether the server has this node registered with {@code default: true}.
     *
     * <p>Read off the server rather than out of a list of ours, so the descriptor stays the one place that
     * says which verbs belong to a player. A node nothing registered, and a call made before a server
     * exists, answer no: the safe half of the question, which is to hide.
     */
    public static boolean meantForEveryone(String permission) {
        Objects.requireNonNull(permission, "permission");
        try {
            org.bukkit.permissions.Permission declared =
                    org.bukkit.Bukkit.getPluginManager().getPermission(permission);
            return declared != null && declared.getDefault() == org.bukkit.permissions.PermissionDefault.TRUE;
        } catch (RuntimeException noServer) {
            return false;
        }
    }

    /** A {@code requires} predicate that passes when the sender holds {@code permission}. */
    public static Predicate<CommandSourceStack> permission(String permission) {
        Objects.requireNonNull(permission, "permission");
        return source -> source.getSender().hasPermission(permission);
    }
}
