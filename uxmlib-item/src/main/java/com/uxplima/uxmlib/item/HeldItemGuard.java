package com.uxplima.uxmlib.item;

import java.util.Objects;
import java.util.function.Predicate;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * Keeps a held item where {@link HeldItems} put it: every way it can leave the player is refused here, in one
 * place.
 *
 * <p>A click on it, a click with it on the cursor, a number key or the off-hand key pressed over an inventory slot,
 * a drag, a drop, the off-hand swap and placing it as a block are cancelled, and it is taken out of the drops on
 * death. A lock that checks only the clicked item and the cursor is the usual one, and it misses a number key
 * pressed over a chest slot, which moves the hotbar item while neither of the two is it.
 *
 * <p>The predicate names the items to keep. A plugin whose operator turned the lock off passes a predicate that
 * answers false. Each handler only cancels its own event, so the guard is safe on any Folia region.
 */
public final class HeldItemGuard implements Listener {

    private final Predicate<? super ItemStack> kept;

    /** @param kept whether an item must stay where it is */
    public HeldItemGuard(Predicate<? super ItemStack> kept) {
        this.kept = Objects.requireNonNull(kept, "kept");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (isKept(event.getCurrentItem()) || isKept(event.getCursor()) || swapsAKeptItem(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (isKept(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isKept(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isKept(event.getMainHandItem()) || isKept(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (isKept(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isKept);
    }

    /** A number key or the off-hand key moves the hotbar or off-hand item into the clicked slot. */
    private boolean swapsAKeptItem(InventoryClickEvent event) {
        HumanEntity who = event.getWhoClicked();
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            return isKept(who.getInventory().getItem(event.getHotbarButton()));
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            return isKept(who.getInventory().getItemInOffHand());
        }
        return false;
    }

    private boolean isKept(@Nullable ItemStack item) {
        return item != null && !item.getType().isAir() && kept.test(item);
    }
}
