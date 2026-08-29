package com.uxplima.uxmlib.hook;

import java.util.Objects;

import org.bukkit.Bukkit;

/**
 * Plugin-presence checks for soft dependencies. A hook's third-party classes must only be referenced
 * after {@link #isPresent(String)} confirms the plugin is enabled, so a server without it still loads:
 * the JVM resolves a class lazily, the first time a method that uses it runs.
 *
 * <p>A plugin name is not always the right question. A drop-in replacement takes the name of the plugin it
 * replaces, so a hook for an API that only the replacement ships has to ask for the class instead. That check
 * belongs in the hook that needs it, next to the type it is guarding, until a second hook needs it too.
 */
public final class Hooks {

    private Hooks() {}

    /** Whether a plugin with this name is installed and enabled. */
    public static boolean isPresent(String pluginName) {
        Objects.requireNonNull(pluginName, "pluginName");
        return Bukkit.getPluginManager().isPluginEnabled(pluginName);
    }
}
