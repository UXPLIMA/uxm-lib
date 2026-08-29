package com.uxplima.uxmlib.gui;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Owns a menu's set of allowed {@link InteractionModifier interaction classes} and the click/drag policy
 * that consults it. Split out of {@code AbstractGui} so the allow-list and the cancel decision it drives
 * live in one place; the actual cancelling and routing stay in {@link GuiClick}, which this holder feeds
 * the current allow-list.
 *
 * <p>By default the set is empty, so every interaction class is denied: an unconfigured menu cannot leak
 * items, until a caller {@link #allow(InteractionModifier) allows} the classes it wants.
 */
final class GuiInteractions {

    private final Set<InteractionModifier> allowed = EnumSet.noneOf(InteractionModifier.class);

    /** Permit {@code modifier}'s interaction class, so a matching click or drag is no longer cancelled. */
    void allow(InteractionModifier modifier) {
        allowed.add(Objects.requireNonNull(modifier, "modifier"));
    }

    /** Re-deny {@code modifier}'s interaction class. */
    void disallow(InteractionModifier modifier) {
        allowed.remove(Objects.requireNonNull(modifier, "modifier"));
    }

    /** Whether {@code modifier}'s interaction class is currently allowed. */
    boolean allows(InteractionModifier modifier) {
        return allowed.contains(Objects.requireNonNull(modifier, "modifier"));
    }

    /** Cancel the event (with {@code DENY}) unless its interaction class is allowed. Must run in-event. */
    void applyPolicy(InventoryClickEvent event) {
        GuiClick.applyPolicy(allowed, event);
    }

    /** Cancel a drag that lands in the menu unless placing into the menu is allowed. */
    void routeDrag(InventoryDragEvent event) {
        GuiClick.routeDrag(allowed, event);
    }
}
