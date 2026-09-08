package com.uxplima.uxmlib.menu.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Slices a flat list of list-source entries into the slots a single rendered page can show. The content slots are
 * the cells a menu reserves for its scrollable list (everything else is fixed decoration), so the page size is
 * simply how many of those slots exist. Entries are laid into those slots in order, and a request for a page past
 * the end is clamped back to the last real page rather than rendering an empty screen. With no entries, or no
 * content slots at all: there is still exactly one page, just an empty one, which keeps the renderer's paging
 * controls consistent.
 *
 * <p>Two markers change where an entry lands. {@link PinnedEntry} fixes one to a slot on every page; {@link
 * PageBreak} ends the page it meets instead of taking a slot on it, which is how a list of runs that belong
 * together is drawn one run to a page.
 */
public final class Pagination {

    private Pagination() {}

    /**
     * A single rendered page: the entry placed in each content slot, the clamped page index, and how many pages
     * the full entry list spans. A short final page leaves its trailing content slots unmapped.
     */
    public record Page<T>(List<Map.Entry<Integer, T>> placements, int page, int pageCount) {}

    /**
     * Computes the placements for one page of {@code entries} across {@code contentSlots}. An entry that implements
     * {@link PinnedEntry} and names a content slot is fixed to that slot on every page, outside the scrolling flow
     * (first entry wins a contested slot; an out-of-range slot flows like a normal entry). Everything else pages
     * across the remaining content slots: the page size is how many slots are left, and {@code page} is clamped into
     * {@code [0, pageCount - 1]}. An empty slot list yields a single empty page so callers never divide by zero.
     *
     * <p>An entry that implements {@link PageBreak} ends the page it meets rather than taking a slot on it, so a
     * host whose entries fall into runs that belong together (one group of rewards to a page) writes the breaks
     * between the runs instead of padding each run out with cells it invented. A break that meets a page with
     * nothing on it yet ends nothing.
     */
    public static <T> Page<T> paginate(List<T> entries, List<Integer> contentSlots, int page) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(contentSlots, "contentSlots");
        if (contentSlots.isEmpty()) {
            return new Page<>(List.of(), 0, 1);
        }
        Map<Integer, T> pinned = pin(entries, contentSlots);
        List<T> flowing = flowing(entries, contentSlots, pinned);
        List<Integer> flowSlots = remaining(contentSlots, pinned.keySet());
        List<List<T>> pages = pages(flowing, flowSlots.size());
        int pageCount = Math.max(1, pages.size());
        int clamped = Math.max(0, Math.min(page, pageCount - 1));
        List<Map.Entry<Integer, T>> placements = new ArrayList<>(pinned.entrySet());
        if (clamped < pages.size()) {
            placements.addAll(place(pages.get(clamped), flowSlots));
        }
        return new Page<>(placements, clamped, pageCount);
    }

    /**
     * The flow cut into pages: a page ends when it has filled every flow slot, and it ends early when the next
     * entry is a {@link PageBreak}.
     *
     * <p>A break is a marker rather than a cell, so it takes no slot and is never handed back in a placement. A
     * break that meets a page with nothing on it yet ends nothing, which is what keeps a leading break, a trailing
     * break and two in a row from costing a blank page a viewer would have to click through.
     *
     * <p>With no break in the list this is the plain division that came before it: entries in order, {@code size}
     * to a page.
     */
    private static <T> List<List<T>> pages(List<T> entries, int size) {
        if (size == 0) {
            return List.of();
        }
        List<List<T>> pages = new ArrayList<>();
        List<T> current = new ArrayList<>(size);
        for (T entry : entries) {
            if (entry instanceof PageBreak) {
                if (!current.isEmpty()) {
                    pages.add(current);
                    current = new ArrayList<>(size);
                }
                continue;
            }
            current.add(entry);
            if (current.size() == size) {
                pages.add(current);
                current = new ArrayList<>(size);
            }
        }
        if (!current.isEmpty()) {
            pages.add(current);
        }
        return pages;
    }

    /** Maps each pinned slot to the first entry that claims it; a contested or out-of-range slot is skipped. */
    private static <T> Map<Integer, T> pin(List<T> entries, List<Integer> contentSlots) {
        Map<Integer, T> pinned = new LinkedHashMap<>();
        for (T entry : entries) {
            if (claims(entry, contentSlots, pinned)) {
                pinned.put(((PinnedEntry) entry).pinnedSlot(), entry);
            }
        }
        return pinned;
    }

    /** The entries that page normally: everything that didn't win a pinned slot, order preserved. */
    private static <T> List<T> flowing(List<T> entries, List<Integer> contentSlots, Map<Integer, T> pinned) {
        Map<Integer, T> claimed = new LinkedHashMap<>();
        List<T> flowing = new ArrayList<>(entries.size() - pinned.size());
        for (T entry : entries) {
            if (claims(entry, contentSlots, claimed)) {
                claimed.put(((PinnedEntry) entry).pinnedSlot(), entry);
            } else {
                flowing.add(entry);
            }
        }
        return flowing;
    }

    /** Whether {@code entry} would win a free, in-range content slot given what is already {@code claimed}. */
    private static <T> boolean claims(T entry, List<Integer> contentSlots, Map<Integer, T> claimed) {
        return entry instanceof PinnedEntry pe
                && contentSlots.contains(pe.pinnedSlot())
                && !claimed.containsKey(pe.pinnedSlot());
    }

    /** The content slots left for the flow: {@code contentSlots} minus the claimed pinned slots, order preserved. */
    private static List<Integer> remaining(List<Integer> contentSlots, Set<Integer> claimed) {
        List<Integer> remaining = new ArrayList<>(contentSlots.size() - claimed.size());
        for (int slot : contentSlots) {
            if (!claimed.contains(slot)) {
                remaining.add(slot);
            }
        }
        return remaining;
    }

    /** One page's entries laid into the flow slots, in order. A short page leaves its trailing slots unmapped. */
    private static <T> List<Map.Entry<Integer, T>> place(List<T> entries, List<Integer> contentSlots) {
        List<Map.Entry<Integer, T>> placements = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            placements.add(Map.entry(contentSlots.get(i), entries.get(i)));
        }
        return placements;
    }
}
