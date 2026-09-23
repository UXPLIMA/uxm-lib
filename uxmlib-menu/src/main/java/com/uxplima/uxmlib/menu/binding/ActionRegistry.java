package com.uxplima.uxmlib.menu.binding;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.uxplima.uxmlib.menu.runtime.MenuActionContext;

/**
 * Holds the action handlers a spec can fire by id (on open, on close, or on a click). A duplicate id is a wiring
 * mistake (two features both claiming {@code "close"}) so registration fails loudly rather than letting the second
 * silently win.
 */
public final class ActionRegistry {

    private final ConcurrentHashMap<String, Consumer<MenuActionContext>> handlers = new ConcurrentHashMap<>();

    /** The names whose handler is an offer a plugin may replace. */
    private final Set<String> defaults = ConcurrentHashMap.newKeySet();

    public void register(String id, Consumer<MenuActionContext> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(id, handler) == null) {
            return;
        }
        if (!defaults.remove(id)) {
            throw new IllegalStateException("action already registered: " + id);
        }
        handlers.put(id, handler);
    }

    /**
     * Offer {@code handler} under a name every plugin shares, such as the ones {@code MenuBasics} answers. It is kept
     * only while nothing else claims the name: a plugin that registers its own afterwards replaces it, and one that
     * registered first keeps its own. Only the offer yields, so two plugin registrations still fail.
     */
    public void registerDefault(String id, Consumer<MenuActionContext> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(id, handler) == null) {
            defaults.add(id);
        }
    }

    public Optional<Consumer<MenuActionContext>> get(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(handlers.get(id));
    }

    public boolean has(String id) {
        Objects.requireNonNull(id, "id");
        return handlers.containsKey(id);
    }

    /** Every registered action id, sorted: the catalog the in-game action picker offers a spec author. */
    public List<String> ids() {
        return handlers.keySet().stream().sorted().toList();
    }
}
