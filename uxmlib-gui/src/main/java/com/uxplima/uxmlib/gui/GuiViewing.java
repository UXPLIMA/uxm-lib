package com.uxplima.uxmlib.gui;

import java.time.Duration;
import java.util.Objects;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.Nullable;

/**
 * Drives a menu's viewer lifecycle: showing it (deferring the open over a sleeping player), closing it,
 * and scheduling the next-tick retry/reopen that those paths need. Split out of {@code AbstractGui} so the
 * scheduler-backed open/close plumbing lives in one place while the menu class keeps the slot, click, and
 * inventory work.
 *
 * <p>The owning menu is handed to each call rather than kept as a field, so the holder can be built in a
 * field initializer without {@code this} escaping the menu's constructor. The deferred retry and the
 * prevent-close reopen re-enter through {@code gui.open(viewer)}, so a subclass that overrides {@code open}
 * (paginated/scrolling menus re-render there) still runs its override on the retry, as a direct call would.
 */
final class GuiViewing {

    private final GuiAnimations animations;
    private final GuiHandlers handlers;

    GuiViewing(GuiAnimations animations, GuiHandlers handlers) {
        this.animations = animations;
        this.handlers = handlers;
    }

    /** Show {@code gui} to {@code viewer}, rendering its per-viewer items first; defers over a sleeper. */
    void open(AbstractGui gui, HumanEntity viewer) {
        Objects.requireNonNull(viewer, "viewer");
        if (deferWhileSleeping(gui, viewer)) {
            return; // opening over the bed UI glitches the client; retry once the player is up
        }
        Inventory inv = gui.getInventory();
        // Resolve dynamic/stateful/animated items for this specific viewer before showing the menu.
        if (viewer instanceof org.bukkit.entity.Player player) {
            GuiRender.renderAll(inv, gui, gui.items(), player);
        }
        animations.advance(inv, gui.items());
        viewer.openInventory(inv);
    }

    /**
     * If {@code viewer} is in bed, an inventory cannot open cleanly over the sleep screen, so defer the open
     * to the next tick (when they have usually woken) through the installed
     * {@link com.uxplima.uxmlib.scheduler.Scheduler}; with no scheduler the open is skipped rather than
     * glitching. Returns whether the open was deferred or skipped.
     */
    private boolean deferWhileSleeping(AbstractGui gui, HumanEntity viewer) {
        if (!viewer.isSleeping()) {
            return false;
        }
        nextTick(viewer, () -> {
            if (!viewer.isSleeping()) {
                gui.open(viewer);
            }
        });
        return true;
    }

    /** Close {@code gui} for {@code viewer}, marking the close deliberate so a prevent-close menu still closes. */
    void close(AbstractGui gui, HumanEntity viewer) {
        Objects.requireNonNull(viewer, "viewer");
        handlers.runProgrammaticClose(() -> GuiRender.close(gui.liveInventory(), viewer));
    }

    /** Close {@code gui} for every viewer, marking the close deliberate. */
    void closeAll(AbstractGui gui) {
        handlers.runProgrammaticClose(() -> GuiRender.closeAll(gui.liveInventory()));
    }

    /**
     * Route a close event: swallow the internal half of a title-change reopen, reopen a prevent-close menu
     * the viewer tried to dismiss, otherwise stop ticking on the last viewer and run the close handler.
     */
    void handleClose(AbstractGui gui, InventoryCloseEvent event) {
        if (handlers.reopening()) {
            return; // the close half of an internal title-change reopen
        }
        if (handlers.preventsClose()
                && !handlers.closingProgrammatically()
                && reopenAfterClose(gui, event.getPlayer())) {
            return; // a forced-input menu the viewer tried to dismiss: it is being reopened, not closed
        }
        Inventory inv = gui.liveInventory();
        // Stop ticking once the last viewer leaves (getViewers still includes the closing player here,
        // so one-or-fewer means this close empties the menu).
        if (inv != null && inv.getViewers().size() <= 1) {
            GuiRegistry.onClose(gui);
        }
        handlers.fireClose(event);
    }

    /**
     * Schedule a reopen of {@code gui} for {@code viewer} on the next tick. Returns whether the reopen was
     * scheduled: it needs the Scheduler-aware install, so with no scheduler the close proceeds normally
     * rather than silently swallowing it.
     */
    private boolean reopenAfterClose(AbstractGui gui, HumanEntity viewer) {
        return nextTick(viewer, () -> gui.open(viewer));
    }

    /** Run {@code task} on {@code viewer}'s region one tick later through the installed scheduler, if any. */
    private static boolean nextTick(HumanEntity viewer, Runnable task) {
        @Nullable Scheduler scheduler = GuiRegistry.installedScheduler();
        if (scheduler == null) {
            return false;
        }
        scheduler.entityLater(viewer, Duration.ofMillis(50L), task);
        return true;
    }
}
