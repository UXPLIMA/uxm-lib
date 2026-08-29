package com.uxplima.uxmlib.item;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Enforces {@link ItemBlockerType} flags by cancelling the event for each blocked {@link ItemAction}. One
 * listener covers every action; each handler reads the involved item's persistent data and acts only when the
 * matching flag is set, so unmarked items pass through untouched. The library leaves registration to the
 * plugin shell ({@code uxmlib-all}), since wiring a listener is a plugin concern, not a library one.
 *
 * <p>Folia-safe by construction: each handler runs on the event's own region thread and only cancels that
 * event (or, for crafting, nulls the in-flight recipe result): it schedules nothing and touches no other
 * region's state.
 */
public final class ItemActionListener implements Listener {

    /**
     * Suppress the recipe result when any matrix item is flagged {@link ItemAction#CRAFT}. Runs at
     * {@link EventPriority#HIGHEST} so the result is nulled after any earlier listener that completes a recipe,
     * leaving the smallest window for another plugin to re-set a result we mean to block.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (ItemBlockerType.isBlocked(ingredient, ItemAction.CRAFT)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    /** Cancel eating/drinking an item flagged {@link ItemAction#CONSUME}. */
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (ItemBlockerType.isBlocked(event.getItem(), ItemAction.CONSUME)) {
            event.setCancelled(true);
        }
    }

    /** Cancel placing an item flagged {@link ItemAction#PLACE}. */
    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (ItemBlockerType.isBlocked(event.getItemInHand(), ItemAction.PLACE)) {
            event.setCancelled(true);
        }
    }

    /** Cancel dropping an item flagged {@link ItemAction#DROP}. */
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (ItemBlockerType.isBlocked(event.getItemDrop().getItemStack(), ItemAction.DROP)) {
            event.setCancelled(true);
        }
    }
}
