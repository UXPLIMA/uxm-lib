package com.uxplima.uxmlib.hook;

import java.util.Objects;

import org.bukkit.Bukkit;

/**
 * Presence checks for soft dependencies. A hook's third-party classes must only be referenced after a check
 * here has passed, so a server without the dependency still loads: the JVM resolves a class lazily, the first
 * time a method that uses it runs.
 *
 * <p>Two questions are on offer, and they are not the same one. {@link #isPresent(String)} asks whether a
 * plugin is running, which is what a hook that calls that plugin's own API needs. {@link #hasClass(String)}
 * asks whether a class is reachable, which is what a hook that touches a third-party type needs.
 */
public final class Hooks {

    private Hooks() {}

    /** Whether a plugin with this name is installed and enabled. */
    public static boolean isPresent(String pluginName) {
        Objects.requireNonNull(pluginName, "pluginName");
        return Bukkit.getPluginManager().isPluginEnabled(pluginName);
    }

    /**
     * Whether a class is on this plugin's class path.
     *
     * <p>The name of a plugin is not the name of the API it brings. A drop-in replacement takes the name of
     * the plugin it replaces: VaultUnlocked declares itself as {@code Vault}, and it ships an API that classic
     * Vault never had. So a hook guarded on {@code "VaultUnlocked"} never runs on any server, and a hook
     * guarded on {@code "Vault"} can still run on a server whose Vault has no such class.
     *
     * <p>Ask for the class the hook actually needs. That is the condition the JVM applies one line later, and
     * it is true exactly when the call is safe.
     */
    public static boolean hasClass(String className) {
        Objects.requireNonNull(className, "className");
        try {
            Class.forName(className, false, Hooks.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }
}
