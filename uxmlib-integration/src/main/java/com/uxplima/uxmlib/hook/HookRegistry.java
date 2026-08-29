package com.uxplima.uxmlib.hook;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Holds a plugin's soft-depend hooks and binds them lazily. A hook may depend on a plugin that enables
 * after us, so {@link #defer} registers a supplier keyed by plugin name; {@link #onPluginEnabled} runs it
 * when that plugin enables (call it from a {@code PluginEnableEvent} listener), and {@link #bindPresent}
 * sweeps any already-enabled deferrals at startup. Resolved hooks are fetched by type with {@link #get}.
 * An instance, not static state, so each plugin owns its own registry.
 */
public final class HookRegistry {

    private final Map<Class<?>, Object> bound = new ConcurrentHashMap<>();
    private final Map<String, Runnable> deferred = new ConcurrentHashMap<>();

    /** Register a ready hook under {@code type}. Returns this. */
    public <T> HookRegistry register(Class<T> type, T hook) {
        bound.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(hook, "hook"));
        return this;
    }

    /**
     * Defer binding {@code type} until the plugin named {@code pluginName} is enabled. The supplier runs
     * once, when the plugin enables (or immediately via {@link #bindPresent} if it already is).
     */
    public <T> HookRegistry defer(Class<T> type, String pluginName, Supplier<? extends T> supplier) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(supplier, "supplier");
        deferred.put(Objects.requireNonNull(pluginName, "pluginName"), () -> bindFrom(type, supplier));
        return this;
    }

    /** Run any deferred binding waiting on {@code pluginName} (from a PluginEnableEvent listener). */
    public void onPluginEnabled(String pluginName) {
        Runnable binding = deferred.remove(pluginName);
        if (binding != null) {
            binding.run();
        }
    }

    /** Run every deferral whose plugin is already enabled: call once after the registry is configured. */
    public void bindPresent() {
        for (String pluginName : Map.copyOf(deferred).keySet()) {
            if (Hooks.isPresent(pluginName)) {
                onPluginEnabled(pluginName);
            }
        }
    }

    /** The bound hook of {@code type}, or empty if it has not bound (plugin absent or not yet enabled). */
    public <T> Optional<T> get(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return Optional.ofNullable(type.cast(bound.get(type)));
    }

    private <T> void bindFrom(Class<T> type, Supplier<? extends T> supplier) {
        T hook = supplier.get();
        if (hook != null) {
            bound.put(type, hook);
        }
    }
}
