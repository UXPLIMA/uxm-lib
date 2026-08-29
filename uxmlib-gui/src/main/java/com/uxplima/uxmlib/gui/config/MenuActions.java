package com.uxplima.uxmlib.gui.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.event.inventory.InventoryClickEvent;

import com.uxplima.uxmlib.gui.GuiNavigator;
import org.jspecify.annotations.Nullable;

/**
 * A registry of named click actions that a config-defined menu can wire its items to. Code owns the
 * behaviour (each action is registered under a key), and an operator references those keys from the
 * config file, so a server owner can re-slot and re-skin a menu without touching code, while the actual
 * effects (open another menu, give an item, run a command) stay code-controlled.
 *
 * <p>A {@code close} action is registered out of the box; add {@code back} by passing a navigator to
 * {@link #withBack}, and register your own with {@link #register}.
 */
public final class MenuActions {

    private final Map<String, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    /** A registry pre-populated with {@code close}; pass a navigator to also get {@code back}. */
    public MenuActions() {
        register("close", event -> event.getWhoClicked().closeInventory());
    }

    /** Register {@code action} under {@code name} (replacing any existing one). Returns this. */
    public MenuActions register(String name, Consumer<InventoryClickEvent> action) {
        actions.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(action, "action"));
        return this;
    }

    /** Register a {@code back} action that pops {@code navigator}'s stack for the clicking player. */
    public MenuActions withBack(GuiNavigator navigator) {
        Objects.requireNonNull(navigator, "navigator");
        return register("back", event -> {
            if (event.getWhoClicked() instanceof org.bukkit.entity.Player player) {
                navigator.back(player);
            }
        });
    }

    /** The action registered under {@code name}, or {@code null} if none. */
    public @Nullable Consumer<InventoryClickEvent> get(String name) {
        return actions.get(name);
    }
}
