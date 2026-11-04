package com.uxplima.uxmlib.command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent;
import com.mojang.brigadier.tree.LiteralCommandNode;

/**
 * Keeps out of each player's command list the nodes of one registered command whose {@link Gate} does not show
 * itself to them.
 *
 * <p>Gates always pass as Brigadier requirements, so Paper builds every player the full tree; this takes the hidden
 * nodes back out of the copy built for that one player. Geyser translates the same list for a Bedrock player, so
 * they see what a Java player sees. A plugin registers one listener per command; each walks only its own command's
 * labels.
 */
public final class GateVisibility implements Listener {

    private final LiteralCommandNode<CommandSourceStack> registered;
    private final Set<String> labels;

    /**
     * A listener for {@code registered}, sent to clients under its own name, {@code aliases}, and each of those with
     * {@code namespace} in front as Paper adds it ({@code uxmcrates:crates}).
     */
    public GateVisibility(LiteralCommandNode<CommandSourceStack> registered, List<String> aliases, String namespace) {
        this.registered = Objects.requireNonNull(registered, "registered");
        Objects.requireNonNull(aliases, "aliases");
        String prefix = Objects.requireNonNull(namespace, "namespace").toLowerCase(Locale.ROOT) + ":";
        Set<String> all = new LinkedHashSet<>();
        all.add(registered.getLiteral());
        all.addAll(aliases);
        Set<String> withPrefix = new LinkedHashSet<>(all);
        all.forEach(label -> withPrefix.add(prefix + label));
        this.labels = Set.copyOf(withPrefix);
    }

    /** A listener for {@code registered} under {@code plugin}'s namespace, registered with the server. */
    public static GateVisibility listen(
            Plugin plugin, LiteralCommandNode<CommandSourceStack> registered, List<String> aliases) {
        Objects.requireNonNull(plugin, "plugin");
        GateVisibility listener = new GateVisibility(registered, aliases, plugin.getName());
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        return listener;
    }

    /** The labels this listener walks: the name, every alias, and each of them with the namespace. */
    public Set<String> labels() {
        return labels;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSendCommands(AsyncPlayerSendCommandsEvent<?> event) {
        if (!event.isAsynchronous() && event.hasFiredAsync()) {
            // The tree was already trimmed when Paper built it off the main thread.
            return;
        }
        CommandGates.prune(event.getCommandNode(), labels, registered, event.getPlayer());
    }
}
