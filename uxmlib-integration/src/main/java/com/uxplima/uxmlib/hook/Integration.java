package com.uxplima.uxmlib.hook;

import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

/**
 * One optional-plugin integration, resolved once to a typed capability that always has a safe default.
 *
 * <p>An integration names the plugin it reaches, the capability callers hold, the no-op that capability is
 * when the plugin is absent, and how to build the real one when it is present. A caller holding the
 * capability never checks for the plugin and never catches a missing class: {@link Integrations} has already
 * decided which of the two it gets.
 *
 * <p>The trap this shape avoids is {@link NoClassDefFoundError}. The plugin's own types appear only inside
 * {@link #whenPresent}, and {@link #whenAbsent} carries none of them, so on a server without the plugin the
 * real side is never constructed and none of its classes are loaded.
 *
 * <p>{@link PluginHook} is the older, smaller contract: a name and a flag, which a caller checks before every
 * use. {@link HookRegistry} binds a hook whose plugin enables after this one. This is the one to reach for
 * when the plugin is declared {@code load: BEFORE} and the capability should simply be there.
 *
 * @param <T> the capability callers hold; its absent default is a complete no-op
 */
public interface Integration<T> {

    /** The plugin name, the same one declared under {@code dependencies.server} in {@code paper-plugin.yml}. */
    String pluginName();

    /** The type {@link Integrations#capability(Class)} is keyed by. */
    Class<T> capability();

    /** The capability when the plugin is absent. It names none of the plugin's types, and it is never null. */
    T whenAbsent();

    /**
     * The capability backed by the plugin. The only place its types are named, called only once
     * {@link #isPresent} is true, and never null.
     */
    T whenPresent(Server server);

    /** Whether the plugin is installed and enabled. Stable for the length of a server run. */
    default boolean isPresent(Server server) {
        Objects.requireNonNull(server, "server");
        Plugin plugin = server.getPluginManager().getPlugin(pluginName());
        return plugin != null && plugin.isEnabled();
    }
}
