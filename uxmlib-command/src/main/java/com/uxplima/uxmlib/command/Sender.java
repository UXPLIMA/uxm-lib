package com.uxplima.uxmlib.command;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;

/**
 * A small read view over a {@link CommandSourceStack}: who ran the command and how to reply to them.
 * Wrap the source from an {@code executes} handler with {@link #of} to get {@link #player()} as an
 * {@link Optional} and a direct {@link #send(Component)} without repeating the unwrap each time.
 *
 * <p>The player a command acts for is the executor before the sender. {@code execute as <player> run ...}, the
 * way an NPC click or another plugin's button runs a command, keeps the console as the sender and makes the
 * player the executor. Read from the sender alone, every player-only command refused it.
 */
public final class Sender {

    private final CommandSourceStack source;

    private Sender(CommandSourceStack source) {
        this.source = source;
    }

    /** Wrap a command source. */
    public static Sender of(CommandSourceStack source) {
        Objects.requireNonNull(source, "source");
        return new Sender(source);
    }

    /**
     * Who the command speaks for: the player it acts for, else the sender (the console or a command block).
     *
     * <p>A handler asks this for a reply, a permission or a name, and each of those belongs to the player a command
     * runs as. The raw sender, which a permission gate reads, stays on {@link #source()}.
     */
    public CommandSender bukkit() {
        return audience(source);
    }

    /**
     * The player a command acts for: the executor when it is a player, else the sender when it is one, else
     * empty, for the console or a command block acting for nobody.
     */
    public static Optional<Player> actingPlayer(CommandSourceStack source) {
        if (source.getExecutor() instanceof Player player) {
            return Optional.of(player);
        }
        return source.getSender() instanceof Player player ? Optional.of(player) : Optional.empty();
    }

    /** Who a reply goes to: the player the command acts for, else the sender. */
    public static CommandSender audience(CommandSourceStack source) {
        return actingPlayer(source).<CommandSender>map(player -> player).orElseGet(source::getSender);
    }

    /** The player the command acts for, or empty when it came from console or a block for nobody. */
    public Optional<Player> player() {
        return actingPlayer(source);
    }

    /** Whether the command acts for a player. */
    public boolean isPlayer() {
        return actingPlayer(source).isPresent();
    }

    /** Send a message to the player the command acts for, else to the sender. */
    public void send(Component message) {
        Objects.requireNonNull(message, "message");
        audience(source).sendMessage(message);
    }

    /** The raw source, for callers that need location or executor. */
    public CommandSourceStack source() {
        return source;
    }
}
