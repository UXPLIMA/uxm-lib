package com.uxplima.uxmlib.command;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import org.jspecify.annotations.Nullable;

/**
 * Who sees a command node and who may use it, kept apart.
 *
 * <p>Brigadier's {@code requires} does both jobs at once: a node whose requirement fails is left out of the command
 * list a client gets, and it is also left out of the parse. A typed word that matches such a node then fails with
 * Brigadier's own "Incorrect argument for command" text: the player is not told they lack anything, and the operator
 * is not told which node. A gate takes the second job away from Brigadier. Given to {@code requires}, it always
 * passes, so the parser accepts the word; {@link CommandGates#seal} makes every executor and suggestion below it ask
 * {@link #allows} first and answer a refusal with the plugin's own line; the command list a player is sent loses the
 * nodes {@link #shows} keeps from them.
 *
 * <p>A gate means nothing to Brigadier on its own: a tree that uses gates is registered through {@link
 * CommandRegistrar}, or sealed with {@link CommandGates#seal} by whoever registers it, or a gated node runs for
 * everyone.
 */
public final class Gate implements Predicate<CommandSourceStack> {

    private static final Gate OPEN = new Gate(sender -> true, sender -> true, null, List.of());

    private final Predicate<CommandSender> shown;
    private final Predicate<CommandSender> allowed;
    private final @Nullable String permission;
    private final List<Gate> parts;

    private Gate(
            Predicate<CommandSender> shown,
            Predicate<CommandSender> allowed,
            @Nullable String permission,
            List<Gate> parts) {
        this.shown = shown;
        this.allowed = allowed;
        this.permission = permission;
        this.parts = parts;
    }

    /** Shown to and usable by a sender who holds {@code node}. */
    public static Gate permission(String node) {
        Objects.requireNonNull(node, "node");
        Predicate<CommandSender> holds = sender -> sender.hasPermission(node);
        return new Gate(holds, holds, node, List.of());
    }

    /**
     * Usable by a sender who holds {@code node}, and shown to them; shown as well to a sender who does not hold it
     * when the server declares the node {@code default: true}, so a player who lost a verb that belongs to every
     * player still finds it and is told why it refuses them.
     */
    public static Gate visibility(String node) {
        Objects.requireNonNull(node, "node");
        return new Gate(
                sender -> sender.hasPermission(node) || Cmd.meantForEveryone(node),
                sender -> sender.hasPermission(node),
                node,
                List.of());
    }

    /**
     * Shown only to the senders {@code shownTo} accepts and usable by everyone, as a word kept for the readers of
     * some languages: it stays out of other readers' lists, and still runs when one of them types it.
     */
    public static Gate shownTo(Predicate<CommandSender> shownTo) {
        Objects.requireNonNull(shownTo, "shownTo");
        return new Gate(shownTo, sender -> true, null, List.of());
    }

    /**
     * Shown to and usable by the senders {@code condition} accepts, for a reason that is not a permission, such as a
     * feature the server turned off. A refusal names no node.
     */
    public static Gate when(Predicate<CommandSender> condition) {
        Objects.requireNonNull(condition, "condition");
        return new Gate(condition, condition, null, List.of());
    }

    /** Shown to and usable by everyone. */
    public static Gate open() {
        return OPEN;
    }

    /** The gate {@code requirement} is, or an open gate when it is anything else or nothing. */
    public static Gate of(@Nullable Predicate<CommandSourceStack> requirement) {
        return requirement instanceof Gate gate ? gate : OPEN;
    }

    /** Whether {@code sender}'s command list carries the node. */
    public boolean shows(CommandSender sender) {
        Objects.requireNonNull(sender, "sender");
        return shown.test(sender);
    }

    /** Whether {@code sender} may use the node and everything below it. */
    public boolean allows(CommandSender sender) {
        Objects.requireNonNull(sender, "sender");
        return allowed.test(sender);
    }

    /**
     * The permission node that refuses {@code sender}: empty when the gate allows them, or when what refuses them is
     * not a permission.
     */
    public Optional<String> refusingNode(CommandSender sender) {
        Objects.requireNonNull(sender, "sender");
        if (allows(sender)) {
            return Optional.empty();
        }
        for (Gate part : parts) {
            if (!part.allows(sender)) {
                return part.refusingNode(sender);
            }
        }
        return Optional.ofNullable(permission);
    }

    /** Shown when either gate shows, usable when either allows, as a word two branches share. */
    public Gate or(Gate other) {
        Objects.requireNonNull(other, "other");
        return new Gate(
                shown.or(other.shown),
                allowed.or(other.allowed),
                permission != null ? permission : other.permission,
                List.of());
    }

    /** Shown when both gates show, usable when both allow; a refusal names the gate that refused. */
    public Gate and(Gate other) {
        Objects.requireNonNull(other, "other");
        return new Gate(shown.and(other.shown), allowed.and(other.allowed), null, List.of(this, other));
    }

    /**
     * Always true, so the parser accepts every word and Brigadier never refuses with its own text. Refusing belongs to
     * the sealed executors, hiding to the command list a client is sent.
     */
    @Override
    public boolean test(CommandSourceStack source) {
        return true;
    }

    /**
     * Joins only another gate. {@link Predicate}'s own {@code and} would return a plain predicate that is always true,
     * and the node under it would run for everyone with nothing left to refuse them.
     */
    @Override
    public Gate and(Predicate<? super CommandSourceStack> other) {
        if (other instanceof Gate gate) {
            return and(gate);
        }
        throw new UnsupportedOperationException("a gate joins only another gate; wrap the check in a Gate");
    }

    /** Joins only another gate, for the reason {@link #and(Predicate)} gives. */
    @Override
    public Gate or(Predicate<? super CommandSourceStack> other) {
        if (other instanceof Gate gate) {
            return or(gate);
        }
        throw new UnsupportedOperationException("a gate joins only another gate; wrap the check in a Gate");
    }

    /** Not supported: a gate that shows what it refuses, or refuses what it shows, has no meaning. */
    @Override
    public Predicate<CommandSourceStack> negate() {
        throw new UnsupportedOperationException("a gate cannot be negated");
    }
}
