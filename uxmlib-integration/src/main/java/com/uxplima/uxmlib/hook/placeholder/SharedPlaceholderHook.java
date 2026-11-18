package com.uxplima.uxmlib.hook.placeholder;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.events.ExpansionsLoadedEvent;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.manager.LocalExpansionManager;

/**
 * {@link SharedTable} over the running PlaceholderAPI and the plugin's own events. Reached only past the
 * PlaceholderAPI-present guard in {@link SharedPlaceholders}, so the {@code me.clip} types load only where it is.
 *
 * <p>The events are taken on whatever thread raises them, which on Folia need not be a region's; the table holds its
 * own monitor for every change, so the thread does not matter.
 */
final class SharedPlaceholderHook implements Listener {

    private final Plugin plugin;
    private final SharedTable table;

    private SharedPlaceholderHook(Plugin plugin, SharedTable table) {
        this.plugin = plugin;
        this.table = table;
    }

    static Optional<Runnable> join(Plugin plugin, String prefix, PlaceholderRegistry registry) {
        Objects.requireNonNull(plugin, "plugin");
        LocalExpansionManager manager = PlaceholderAPIPlugin.getInstance().getLocalExpansionManager();
        SharedTable table = new SharedTable(
                plugin.getName(),
                plugin.getPluginMeta().getVersion(),
                () -> manager.getExpansion(SharedTable.IDENTIFIER),
                PlaceholderExpansion::register,
                PlaceholderExpansion::unregister,
                plugin.getLogger());
        if (!table.join(prefix, registry::resolve, registry::resolveRelational)) {
            return Optional.empty();
        }
        SharedPlaceholderHook hook = new SharedPlaceholderHook(plugin, table);
        plugin.getServer().getPluginManager().registerEvents(hook, plugin);
        return Optional.of(hook::leave);
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        Plugin going = event.getPlugin();
        if (going.getName().equals(PlaceholderAPIPlugin.getInstance().getName())) {
            // Every expansion goes with PlaceholderAPI; the loaded event brings the shared one back with it.
            return;
        }
        table.pluginDisabled(going.getName(), going.getClass().getClassLoader());
        if (going == plugin) {
            HandlerList.unregisterAll(this);
        }
    }

    @EventHandler
    public void onExpansionsLoaded(ExpansionsLoadedEvent event) {
        table.expansionsLoaded();
    }

    private void leave() {
        table.leave();
        HandlerList.unregisterAll(this);
    }
}
