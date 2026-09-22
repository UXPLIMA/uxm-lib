package com.uxplima.uxmlib.hook;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;

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

    /**
     * Resolve every integration now, and again once the server has finished loading, behind the same capability.
     *
     * <p>For a plugin that enables before the plugins it reads. One that loads at {@code STARTUP} enables before
     * every ordinary plugin, and no {@code load: BEFORE} reaches across load phases, so an integration resolved
     * while it enables never finds Vault. uxmEssentials is such a plugin, and on 2026-09-22 its Vault economy and
     * permission capabilities were the no-op for the whole run on a server with Vault.
     *
     * <p>Each capability is handed out as a stand-in that answers with the no-op until {@link ServerLoadEvent}
     * fires, when every plugin has enabled; an integration that was absent is resolved again then, and the
     * stand-in answers with the real side from that moment. A caller keeps what it was given. So the capability
     * type has to be an interface, which a stand-in can implement; a class is refused here rather than later.
     */
    public static Integrations resolveWhenLoaded(
            Plugin plugin, System.Logger log, List<? extends Integration<?>> integrations) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(integrations, "integrations");
        Server server = plugin.getServer();
        Map<Class<?>, Object> standIns = new HashMap<>();
        List<Late<?>> waiting = new ArrayList<>();
        for (Integration<?> integration : integrations) {
            Late<?> late = Late.of(integration, server, log);
            if (standIns.containsKey(late.type())) {
                throw new IllegalStateException(
                        "two integrations claim the capability " + late.type().getName());
            }
            standIns.put(late.type(), late.standIn());
            if (!late.bound()) {
                waiting.add(late);
            }
        }
        if (!waiting.isEmpty()) {
            server.getPluginManager().registerEvents(new WhenLoaded(List.copyOf(waiting), server, log), plugin);
        }
        return new Integrations(Map.copyOf(standIns));
    }

    /** Resolves the integrations that were absent at enable, once, when the server has finished loading. */
    static final class WhenLoaded implements Listener {

        private final List<Late<?>> waiting;
        private final Server server;
        private final System.Logger log;

        private WhenLoaded(List<Late<?>> waiting, Server server, System.Logger log) {
            this.waiting = waiting;
            this.server = server;
            this.log = log;
        }

        @EventHandler
        void onLoaded(ServerLoadEvent event) {
            Objects.requireNonNull(event, "event");
            waiting.forEach(late -> late.retry(server, log));
            HandlerList.unregisterAll(this);
        }
    }

    /** One integration behind a stand-in whose answer can change once. */
    private static final class Late<T> {

        private final Integration<T> integration;
        private final AtomicReference<T> target;
        private final T standIn;
        private volatile boolean bound;

        private Late(Integration<T> integration, T initial, boolean bound) {
            this.integration = integration;
            this.target = new AtomicReference<>(initial);
            this.bound = bound;
            this.standIn = standIn(integration.capability(), target);
        }

        static <T> Late<T> of(Integration<T> integration, Server server, System.Logger log) {
            Class<T> type = Objects.requireNonNull(integration.capability(), "capability");
            if (!type.isInterface()) {
                throw new IllegalArgumentException("a capability resolved when the server has loaded has to be an"
                        + " interface, so it can be stood in for until then: " + type.getName());
            }
            boolean present = integration.isPresent(server);
            return new Late<>(integration, resolveOne(integration, server, log), present);
        }

        Class<T> type() {
            return integration.capability();
        }

        T standIn() {
            return standIn;
        }

        boolean bound() {
            return bound;
        }

        void retry(Server server, System.Logger log) {
            if (bound || !integration.isPresent(server)) {
                return;
            }
            target.set(resolveOne(integration, server, log));
            bound = true;
        }

        private static <T> T standIn(Class<T> type, AtomicReference<T> target) {
            Object proxy =
                    Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "equals" -> args != null && args.length == 1 && self == args[0];
                                case "hashCode" -> System.identityHashCode(self);
                                default -> "Integration[" + type.getSimpleName() + " -> " + target.get() + "]";
                            };
                        }
                        try {
                            return method.invoke(target.get(), args);
                        } catch (InvocationTargetException thrown) {
                            throw thrown.getCause();
                        }
                    });
            return type.cast(proxy);
        }
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
