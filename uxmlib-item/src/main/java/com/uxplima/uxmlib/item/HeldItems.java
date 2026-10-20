package com.uxplima.uxmlib.item;

import java.util.Objects;
import java.util.function.Predicate;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import org.jspecify.annotations.Nullable;

/**
 * Where a plugin puts an item it hands a player and keeps: a hotbar tool, a menu opener, a toggle. The hotbar is
 * shared by every plugin on the server and by the player, so the item never takes anybody else's place.
 *
 * <p>The item takes its wanted slot when that slot is empty or already holds it. Otherwise it stays in the slot the
 * player moved it to, or takes the first free storage slot. An item that is not the plugin's own is never written
 * over and never moved: writing over it deletes it, and moving it aside makes the plugin that owns it hand it out
 * again, one more copy each time both plugins place their items. Every other copy is taken back, so the player
 * holds one. With no room at all the player gets nothing and loses nothing.
 *
 * <p>The predicate names the plugin's own item, by the mark in its persistent data. Call these on the player's own
 * thread: the inventory belongs to the player, and under Folia only that thread may write it.
 */
public final class HeldItems {

    private HeldItems() {}

    /**
     * The slot the item goes to, or -1 when the inventory has no room.
     *
     * @param wanted the slot the plugin's configuration names, 0 to 8 for the hotbar
     * @param ours whether an item is this plugin's own item
     */
    public static int slotFor(PlayerInventory inventory, int wanted, Predicate<? super ItemStack> ours) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(ours, "ours");
        @Nullable ItemStack there = inventory.getItem(wanted);
        if (isEmpty(there) || ours.test(there)) {
            return wanted;
        }
        ItemStack[] contents = inventory.getStorageContents();
        int firstFree = -1;
        for (int slot = 0; slot < contents.length; slot++) {
            @Nullable ItemStack item = contents[slot];
            if (isEmpty(item)) {
                firstFree = firstFree < 0 ? slot : firstFree;
            } else if (ours.test(item)) {
                return slot;
            }
        }
        // Counted here rather than asked of firstEmpty(), so an armour or off-hand slot is never offered.
        return firstFree;
    }

    /**
     * Put {@code item} where {@link #slotFor} says, after taking back every other copy. Placing it again, as every
     * join does, rebuilds it in place, so a rename or a translation reaches a player who never logs out.
     *
     * @return the slot the item went to, or -1 when the inventory had no room and nothing changed
     */
    public static int place(PlayerInventory inventory, int wanted, ItemStack item, Predicate<? super ItemStack> ours) {
        Objects.requireNonNull(item, "item");
        int slot = slotFor(inventory, wanted, ours);
        if (slot < 0) {
            return -1;
        }
        takeBack(inventory, ours);
        inventory.setItem(slot, item);
        return slot;
    }

    /**
     * Take back every copy of the plugin's own item, wherever the player keeps it.
     *
     * @return how many slots were emptied
     */
    public static int takeBack(PlayerInventory inventory, Predicate<? super ItemStack> ours) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(ours, "ours");
        ItemStack[] contents = inventory.getContents();
        int taken = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            @Nullable ItemStack item = contents[slot];
            if (!isEmpty(item) && ours.test(item)) {
                inventory.setItem(slot, null);
                taken++;
            }
        }
        return taken;
    }

    private static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
