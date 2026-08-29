package com.uxplima.uxmlib.hologram.widget;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The pure, per-player paging state machine behind {@link PagedHologram}. Each player has a current page in
 * {@code [0, pageCount)}; {@link #next} and {@link #prev} wrap around the ends, {@link #set} clamps into
 * range, and every player's page is held in its own slot so one viewer paging never moves another. A player
 * with no recorded page is on page zero, so {@link #forget} (called on quit / world-change) simply drops the
 * slot.
 *
 * <p>No Bukkit types here: this is the testable core; {@link PagedHologram} layers the show/hide of the
 * overlapping page holograms on top of it.
 */
final class PageState {

    private final int pageCount;
    private final Map<UUID, Integer> pages = new ConcurrentHashMap<>();

    PageState(int pageCount) {
        if (pageCount < 1) {
            throw new IllegalArgumentException("pageCount must be >= 1");
        }
        this.pageCount = pageCount;
    }

    /** How many pages this state machine spans. */
    int pageCount() {
        return pageCount;
    }

    /** The page {@code player} is currently on (zero when it has none recorded). */
    int current(UUID player) {
        Objects.requireNonNull(player, "player");
        return pages.getOrDefault(player, 0);
    }

    /** Advance {@code player} one page, wrapping past the last page to the first; returns the new page. */
    int next(UUID player) {
        return move(player, 1);
    }

    /** Retreat {@code player} one page, wrapping before the first page to the last; returns the new page. */
    int prev(UUID player) {
        return move(player, -1);
    }

    /** Jump {@code player} to {@code page}, clamped into {@code [0, pageCount)}; returns the new page. */
    int set(UUID player, int page) {
        Objects.requireNonNull(player, "player");
        int clamped = Math.max(0, Math.min(page, pageCount - 1));
        pages.put(player, clamped);
        return clamped;
    }

    /** Drop {@code player}'s recorded page so it falls back to page zero. */
    void forget(UUID player) {
        Objects.requireNonNull(player, "player");
        pages.remove(player);
    }

    private int move(UUID player, int delta) {
        Objects.requireNonNull(player, "player");
        // Floor-mod so a -1 step from page 0 lands on the last page rather than going negative.
        int moved = Math.floorMod(current(player) + delta, pageCount);
        pages.put(player, moved);
        return moved;
    }
}
