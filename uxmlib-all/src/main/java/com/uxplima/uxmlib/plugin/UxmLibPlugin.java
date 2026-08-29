package com.uxplima.uxmlib.plugin;

import org.bukkit.plugin.java.JavaPlugin;

import com.uxplima.uxmlib.item.ItemActionListener;

/**
 * Lifecycle shell for the standalone distribution. The library's value is its API surface, which dependent
 * plugins call directly; the shell registers only the handful of listeners whose behaviour is driven entirely
 * by item persistent data and so makes sense to run server-wide. Today that is {@link ItemActionListener},
 * which enforces per-item action blocks. Plugins that shade individual modules register it themselves.
 */
public final class UxmLibPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new ItemActionListener(), this);
        getLogger().info("uxmLib " + getPluginMeta().getVersion() + " loaded: toolkit available to dependent plugins.");
    }
}
