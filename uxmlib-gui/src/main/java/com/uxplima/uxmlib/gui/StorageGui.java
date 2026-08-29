package com.uxplima.uxmlib.gui;

import java.util.Map;
import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import org.jspecify.annotations.Nullable;

/**
 * A menu that holds real items rather than buttons. Unlike a {@link SimpleGui}, it allows the player to
 * take, place, and swap items by default, and it does <em>not</em> clear what they leave behind: the
 * contents persist across opens, so it can back a vault, a player stash, or any "drop items here" UI.
 * Read what the player left with {@link #contents()} (typically from an {@link #onClose} handler) and
 * seed it with {@link #setContents}.
 *
 * <p>Created through {@link Guis#storage()}.
 */
public final class StorageGui extends AbstractGui {

    StorageGui(Component title, int rows) {
        super(title, rows);
        allow(InteractionModifier.ITEM_TAKE);
        allow(InteractionModifier.ITEM_PLACE);
        allow(InteractionModifier.ITEM_SWAP);
    }

    /** A snapshot of the slots' current contents; an entry is {@code null} where the slot is empty. */
    public @Nullable ItemStack[] contents() {
        Inventory inv = getInventory();
        @Nullable ItemStack[] snapshot = new ItemStack[size()];
        for (int slot = 0; slot < snapshot.length; slot++) {
            ItemStack stack = inv.getItem(slot);
            snapshot[slot] = stack == null ? null : stack.clone();
        }
        return snapshot;
    }

    /** Seed the storage slots with {@code items} (index = slot); a {@code null} entry leaves a slot empty. */
    public void setContents(@Nullable ItemStack[] items) {
        Objects.requireNonNull(items, "items");
        Inventory inv = getInventory();
        for (int slot = 0; slot < size(); slot++) {
            ItemStack stack = slot < items.length ? items[slot] : null;
            inv.setItem(slot, stack == null ? null : stack.clone());
        }
    }

    /**
     * Stow {@code items} into the storage slots, stacking onto existing stacks first and then filling empty
     * slots, exactly as Bukkit's {@link Inventory#addItem} does. Returns the leftover that did not fit, keyed
     * by the index of the argument it came from: an empty map means everything was stored. Overflow is
     * handed back to the caller rather than dropped, so a vault deposit can bounce the remainder to the
     * player instead of vanishing it.
     */
    public Map<Integer, ItemStack> addItem(ItemStack... items) {
        Objects.requireNonNull(items, "items");
        for (ItemStack item : items) {
            Objects.requireNonNull(item, "item");
        }
        return getInventory().addItem(items);
    }
}
