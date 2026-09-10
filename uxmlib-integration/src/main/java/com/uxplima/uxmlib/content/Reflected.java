package com.uxplima.uxmlib.content;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Server;

import org.jspecify.annotations.Nullable;

/**
 * The reflective way into a plugin nobody here compiles against.
 *
 * <p>Every provider in this package needs the same four things: is that plugin enabled, what is its class,
 * what is its method, and call it without letting a failure out. This is those four things once, so a
 * provider is the shape of its vendor's API and nothing else.
 *
 * <p>Lookups are cached, because a provider is asked on every block break and a class lookup that misses is
 * an exception thrown and caught. A miss is cached as a miss.
 */
public final class Reflected {

    /** What a lookup that failed is remembered as, since a map cannot hold a null value. */
    private static final Object MISSING = new Object();

    private static final Map<String, Object> CLASSES = new ConcurrentHashMap<>();

    private static final Map<String, Object> METHODS = new ConcurrentHashMap<>();

    private Reflected() {}

    /**
     * Whether a plugin is installed and enabled.
     *
     * <p>Enabled and not merely installed: a plugin that failed its own startup answers a query by throwing
     * rather than by saying no.
     */
    public static boolean enabled(Server server, String plugin) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(plugin, "plugin");
        return server.getPluginManager().isPluginEnabled(plugin);
    }

    /** One class by name, or nothing when this server does not have it. */
    public static Optional<Class<?>> type(String name) {
        Objects.requireNonNull(name, "name");
        Object held = CLASSES.computeIfAbsent(name, wanted -> {
            try {
                return Class.forName(wanted);
            } catch (ClassNotFoundException | LinkageError absent) {
                return MISSING;
            }
        });
        return held == MISSING ? Optional.empty() : Optional.of((Class<?>) held);
    }

    /** One method by name and parameter types, or nothing when this vendor's API does not carry it. */
    public static Optional<Method> method(String type, String name, String... parameters) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        String key = type + "#" + name + "(" + String.join(",", parameters) + ")";
        Object held = METHODS.computeIfAbsent(key, wanted -> lookUp(type, name, parameters));
        return held == MISSING ? Optional.empty() : Optional.of((Method) held);
    }

    private static Object lookUp(String type, String name, String... parameters) {
        Optional<Class<?>> owner = type(type);
        if (owner.isEmpty()) {
            return MISSING;
        }
        Class<?>[] taken = new Class<?>[parameters.length];
        for (int at = 0; at < parameters.length; at++) {
            Optional<Class<?>> parameter = type(parameters[at]);
            if (parameter.isEmpty()) {
                return MISSING;
            }
            taken[at] = parameter.get();
        }
        try {
            Method found = owner.get().getMethod(name, taken);
            found.setAccessible(true);
            return found;
        } catch (NoSuchMethodException | SecurityException | LinkageError absent) {
            return MISSING;
        }
    }

    /**
     * Call {@code method} and hand back what it answered, or nothing.
     *
     * <p>Nothing on any failure, deliberately and broadly. What is on the other side of this call is
     * somebody else's code and a version of it nobody here compiled against: the one thing that must not
     * happen is a vendor's incompatible update taking a block break down with it.
     */
    public static Optional<Object> call(Method method, @Nullable Object on, Object... arguments) {
        Objects.requireNonNull(method, "method");
        try {
            return Optional.ofNullable(method.invoke(on, arguments));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return Optional.empty();
        }
    }

    /** The same, as a string. */
    public static Optional<String> callForString(Method method, @Nullable Object on, Object... arguments) {
        return call(method, on, arguments).filter(String.class::isInstance).map(String.class::cast);
    }

    /** Forget every lookup, which a test does between two servers. */
    public static void forgetEverything() {
        CLASSES.clear();
        METHODS.clear();
    }
}
