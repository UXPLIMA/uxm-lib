package com.uxplima.uxmlib.hologram.widget;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmlib.hologram.HologramLifecycle;

/**
 * A per-player paged hologram: N pages of content live as N overlapping holograms at one location and each
 * player sees exactly one. {@link #open} shows a viewer page zero; {@link #next} / {@link #prev} hide the page
 * the viewer is on and show the one it moves to (for that viewer only), so paging never disturbs anyone else
 * (the HologramLib {@code PagedLeaderboard} technique, mapped onto Paper's native per-viewer {@code show} /
 * {@code hide}).
 *
 * <p>The page math is the pure {@link PageState}; the show/hide goes through a {@link PagePresenter} so the
 * widget is unit-testable with no live entity. It implements {@link HologramLifecycle} so, registered with the
 * {@code HologramManager}, a player's page is reset (and its current page hidden) on quit / world-change with
 * no per-consumer Bukkit listener.
 */
public final class PagedHologram implements HologramLifecycle {

    private final PageState pages;
    private final PagePresenter presenter;

    public PagedHologram(int pageCount, PagePresenter presenter) {
        this.pages = new PageState(pageCount);
        this.presenter = Objects.requireNonNull(presenter, "presenter");
    }

    /** How many pages this widget spans. */
    public int pageCount() {
        return pages.pageCount();
    }

    /** Start showing {@code viewer} the first page. Call when the viewer should begin seeing the widget. */
    public void open(UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        presenter.show(pages.current(viewer), viewer);
    }

    /** Advance {@code viewer} one page, wrapping at the end; re-renders only that viewer. */
    public void next(UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        turn(viewer, pages.current(viewer), pages.next(viewer));
    }

    /** Retreat {@code viewer} one page, wrapping at the start; re-renders only that viewer. */
    public void prev(UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        turn(viewer, pages.current(viewer), pages.prev(viewer));
    }

    private void turn(UUID viewer, int from, int to) {
        // current() is read before the move, so {@code from} is the page the viewer was on. A single-page
        // widget (or any move that lands on the same page) leaves from == to, so skip the no-op flicker.
        if (from == to) {
            return;
        }
        presenter.hide(from, viewer);
        presenter.show(to, viewer);
    }

    /** Reset {@code player} to page zero, hiding the page it was on. Wired by the lifecycle SPI. */
    @Override
    public void onQuit(UUID player) {
        reset(player);
    }

    @Override
    public void onWorldChange(UUID player) {
        reset(player);
    }

    @Override
    public void onRespawn(UUID player) {
        reset(player);
    }

    private void reset(UUID player) {
        Objects.requireNonNull(player, "player");
        presenter.hide(pages.current(player), player);
        pages.forget(player);
    }
}
