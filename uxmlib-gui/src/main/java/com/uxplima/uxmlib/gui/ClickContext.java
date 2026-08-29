package com.uxplima.uxmlib.gui;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * An immutable snapshot of one click, captured the moment the event fires: who clicked, which slot, the
 * click type, and clones of the clicked icon and the item on the cursor. It is the input to a declarative
 * (and possibly off-thread) click handler: see {@link com.uxplima.uxmlib.gui.item.GuiAction.Responding}.
 *
 * <p>The items are cloned in {@link #of(InventoryClickEvent)} so a snapshot handed to a handler running on
 * another thread can never see the live inventory change underneath it; the value is honestly immutable
 * from every construction path. A pure snapshot also makes a handler unit-testable without a live menu.
 *
 * <p>Pattern from AnvilGUI's {@code StateSnapshot} (MIT): a defensively-cloned value object handed to the
 * handler instead of the live event, so the same handler is safe whether it runs inline or off-thread.
 */
public record ClickContext(Player viewer, int slot, ClickType clickType, ItemStack clickedItem, ItemStack cursor) {

    public ClickContext {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(clickType, "clickType");
        Objects.requireNonNull(clickedItem, "clickedItem");
        Objects.requireNonNull(cursor, "cursor");
        // Clone on construction (not only in the factory) so the value is immutable however it is built.
        clickedItem = clickedItem.clone();
        cursor = cursor.clone();
    }

    /**
     * Snapshot {@code event}. The clicked-slot item may be absent (an empty slot); it snapshots as a
     * one-count AIR stack so callers never see {@code null}. The viewer must be a {@link Player}.
     */
    public static ClickContext of(InventoryClickEvent event) {
        Objects.requireNonNull(event, "event");
        if (!(event.getWhoClicked() instanceof Player player)) {
            throw new IllegalArgumentException("click context requires a player viewer");
        }
        return new ClickContext(
                player, event.getSlot(), event.getClick(), orAir(event.getCurrentItem()), event.getCursor());
    }

    private static ItemStack orAir(@Nullable ItemStack item) {
        return item == null ? new ItemStack(Material.AIR) : item;
    }
}
