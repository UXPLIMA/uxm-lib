package com.uxplima.uxmlib.menu.api.event;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/**
 * Fired when a viewer leaves a menu for good: they closed its chest, other than to open another window or by
 * disconnecting, or dismissed the Bedrock form that stood in for it. A view that tracks whether its menu is open
 * hears both from here, since a form raises no inventory event at all.
 *
 * <p>The event fires on the viewer's own region thread, after the menu's close actions ran.
 */
@NullMarked
public final class MenuCloseEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player viewer;
    private final String menuId;
    private final boolean form;

    public MenuCloseEvent(Player viewer, String menuId, boolean form) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.menuId = Objects.requireNonNull(menuId, "menuId");
        this.form = form;
    }

    /** The player who left the menu. */
    public Player getViewer() {
        return viewer;
    }

    /** The registered spec id of the menu they left. */
    public String getMenuId() {
        return menuId;
    }

    /** Whether they dismissed a Bedrock form rather than closed a chest. */
    public boolean isForm() {
        return form;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
