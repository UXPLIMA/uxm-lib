package com.uxplima.uxmlib.hook;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Server;

/**
 * A plugin's optional integrations, each resolved once to its real capability or its no-op.
 *
 * <p>Resolved at enable and never again, because whether a plugin is there does not change during a run.
 * A caller asks for the capability by type and uses it without a presence check. An integration whose real
 * side throws while it is built, which is how an incompatible release of the other plugin shows itself,
 * falls back to its no-op and says so once, rather than taking this plugin's enable down with it.
 *
 * <p>An instance, not static state: uxmLib is relocated into every plugin, and each plugin resolves its own.
 * Written first in uxmEssentials and moved here on 2026-09-22, because it is a mechanism every plugin with an
 * optional dependency can use.
 */
public final class Integrations {

    private final Map<Class<?>, Object> capabilities;

    private Integrations(Map<Class<?>, Object> capabilities) {
        this.capabilities = capabilities;
    }

    /** Resolve every integration against {@code server}, once, to its real or its no-op capability. */
    public static Integrations resolve(Server server, System.Logger log, List<? extends Integration<?>> integrations) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(integrations, "integrations");
        Map<Class<?>, Object> resolved = new HashMap<>();
        for (Integration<?> integration : integrations) {
            bind(resolved, integration, server, log);
        }
        return new Integrations(Map.copyOf(resolved));
    }

    private static <T> void bind(
            Map<Class<?>, Object> into, Integration<T> integration, Server server, System.Logger log) {
        Class<T> capability = Objects.requireNonNull(integration.capability(), "capability");
        if (into.containsKey(capability)) {
            throw new IllegalStateException("two integrations claim the capability " + capability.getName());
        }
        into.put(capability, resolveOne(integration, server, log));
    }

    private static <T> T resolveOne(Integration<T> integration, Server server, System.Logger log) {
        if (!integration.isPresent(server)) {
            return Objects.requireNonNull(integration.whenAbsent(), "whenAbsent");
        }
        try {
            T present = Objects.requireNonNull(integration.whenPresent(server), "whenPresent");
            log.log(System.Logger.Level.INFO, "Bound to {0}", integration.pluginName());
            return present;
        } catch (RuntimeException | LinkageError failure) {
            // An incompatible release of the other plugin surfaces here as a linkage error or a constructor
            // that throws. One broken integration costs its own feature and nothing else.
            log.log(
                    System.Logger.Level.WARNING,
                    "Could not bind to " + integration.pluginName() + ", so that feature stays off",
                    failure);
            return Objects.requireNonNull(integration.whenAbsent(), "whenAbsent");
        }
    }

    /** The capability for {@code type}, never null. An unregistered type is a wiring mistake and throws. */
    public <T> T capability(Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object capability = capabilities.get(type);
        if (capability == null) {
            throw new IllegalArgumentException("no integration provides " + type.getName());
        }
        return type.cast(capability);
    }

    /** Whether an integration was registered for {@code type}, whether or not its plugin is present. */
    public boolean provides(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return capabilities.containsKey(type);
    }
}
