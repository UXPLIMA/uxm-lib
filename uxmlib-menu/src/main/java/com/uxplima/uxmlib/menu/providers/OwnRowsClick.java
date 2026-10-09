package com.uxplima.uxmlib.menu.providers;

import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.menu.spec.ClickKind;
import org.jspecify.annotations.NullMarked;

/**
 * A click on a stack in the viewer's own inventory while a window that holds items is open, put to the window's
 * {@link ContentProvider}. A trade offers that stack and a cargo hold takes it in, each on its own record, so the
 * feature needs where the stack sits in the player's inventory and which gesture reached it, not the Bukkit event.
 *
 * @param inventorySlot the slot of the viewer's own inventory the click landed on, as {@code PlayerInventory} numbers it
 * @param stack a copy of the stack in that slot, never air
 * @param gesture the gesture the viewer used
 */
@NullMarked
public record OwnRowsClick(int inventorySlot, ItemStack stack, ClickKind gesture) {

    public OwnRowsClick {
        if (inventorySlot < 0) {
            throw new IllegalArgumentException("inventorySlot must be >= 0: " + inventorySlot);
        }
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(gesture, "gesture");
    }
}
