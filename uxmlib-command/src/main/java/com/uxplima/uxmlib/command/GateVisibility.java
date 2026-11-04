package com.uxplima.uxmlib.command;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent;
import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.LiteralCommandNode;

/**
 * Keeps out of each player's command list, and out of the completions the server sends them, the nodes of a plugin's
 * commands whose {@link Gate} does not show itself to them.
 *
 * <p>Gates always pass as Brigadier requirements, so Paper builds every player the full tree; this takes the hidden
 * nodes back out of the copy built for that one player. Geyser translates the same list for a Bedrock player, so they
 * see what a Java player sees. One listener serves all of a plugin's commands: each command is added under its labels,
 * and a reload clears them and adds them again.
 */
public final class GateVisibility implements Listener {

    private final String prefix;
    private volatile Map<String, LiteralCommandNode<CommandSourceStack>> byLabel = Map.of();

    /** A listener for commands Paper registers under {@code namespace} ({@code uxmcrates:crates}). */
    public GateVisibility(String namespace) {
        this.prefix = Objects.requireNonNull(namespace, "namespace").toLowerCase(Locale.ROOT) + ":";
    }

    /** A listener for {@code plugin}'s commands, registered with the server. */
    public static GateVisibility listen(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        GateVisibility listener = new GateVisibility(plugin.getName());
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        return listener;
    }

    /**
     * Watch {@code registered}, the tree a plugin handed Paper, under its own name, {@code aliases}, and each of those
     * with the namespace in front.
     */
    public synchronized void add(LiteralCommandNode<CommandSourceStack> registered, List<String> aliases) {
        Objects.requireNonNull(registered, "registered");
        Objects.requireNonNull(aliases, "aliases");
        Map<String, LiteralCommandNode<CommandSourceStack>> next = new HashMap<>(byLabel);
        put(next, registered.getLiteral(), registered);
        aliases.forEach(alias -> put(next, alias, registered));
        byLabel = Map.copyOf(next);
    }

    /** Forget every command, before a reload adds them again. */
    public synchronized void clear() {
        byLabel = Map.of();
    }

    /** Every label a watched command is sent under. */
    public Set<String> labels() {
        return byLabel.keySet();
    }

    private void put(
            Map<String, LiteralCommandNode<CommandSourceStack>> into,
            String label,
            LiteralCommandNode<CommandSourceStack> node) {
        into.put(label, node);
        into.put(prefix + label, node);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSendCommands(AsyncPlayerSendCommandsEvent<?> event) {
        if (!event.isAsynchronous() && event.hasFiredAsync()) {
            // The tree was already trimmed when Paper built it off the main thread.
            return;
        }
        Map<String, LiteralCommandNode<CommandSourceStack>> watched = byLabel;
        if (!watched.isEmpty()) {
            CommandGates.prune(event.getCommandNode(), watched, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSendSuggestions(AsyncPlayerSendSuggestionsEvent event) {
        Map<String, LiteralCommandNode<CommandSourceStack>> watched = byLabel;
        if (watched.isEmpty()) {
            return;
        }
        Suggestions offered = event.getSuggestions();
        Suggestions visible = CommandGates.visibleSuggestions(event.getBuffer(), offered, watched, event.getPlayer());
        if (visible != offered) {
            event.setSuggestions(visible);
        }
    }
}
