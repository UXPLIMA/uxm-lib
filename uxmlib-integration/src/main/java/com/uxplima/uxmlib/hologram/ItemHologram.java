package com.uxplima.uxmlib.hologram;

import java.util.Objects;

import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

/**
 * A {@link ModelHologram} backed by a live {@link ItemDisplay} showing a floating {@code ItemStack}. The item
 * can be swapped in place with {@link #setItem(ItemStack)} (a refresh, not a re-spawn); every other operation:
 * move, transform, mount, per-viewer visibility, removal, comes from {@link AbstractModelHologram} and behaves
 * exactly as it does for a text {@link Hologram}.
 */
public final class ItemHologram extends AbstractModelHologram<ItemDisplay> {

    ItemHologram(ItemDisplay display) {
        super(display);
    }

    /** Replace the displayed item. */
    public void setItem(ItemStack item) {
        display.setItemStack(Objects.requireNonNull(item, "item"));
    }
}
