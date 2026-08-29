package com.uxplima.uxmlib.hud.scoreboard;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import net.kyori.adventure.text.Component;

/**
 * The flicker-free diff. Given the lines a sidebar currently shows and the lines it should show next, this
 * computes the minimal set of changes: which existing line indices need their text re-sent, which trailing
 * indices must be added, and which trailing indices must be removed. Because the visible text for each line
 * rides in a team prefix keyed by a fixed invisible entry, only the changed indices are touched: there is
 * no clear-and-rebuild, so the client never flickers.
 *
 * <p>Kept as a pure function (no Bukkit types) so the algorithm is unit-tested directly.
 */
final class SidebarDiff {

    private SidebarDiff() {}

    /** The result of diffing two line lists: indices whose text changed, plus the index ranges to add/remove. */
    record Plan(Set<Integer> changed, List<Integer> added, List<Integer> removed) {
        Plan {
            changed = Set.copyOf(changed);
            added = List.copyOf(added);
            removed = List.copyOf(removed);
        }

        boolean isEmpty() {
            return changed.isEmpty() && added.isEmpty() && removed.isEmpty();
        }
    }

    /**
     * Diff {@code previous} against {@code next}. Indices present in both lists whose components differ go in
     * {@code changed}; indices only in {@code next} go in {@code added}; indices only in {@code previous} go
     * in {@code removed}. Indices are line numbers from the top of the sidebar (0-based).
     */
    static Plan diff(List<Component> previous, List<Component> next) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(next, "next");
        Set<Integer> changed = new TreeSet<>();
        List<Integer> added = new java.util.ArrayList<>();
        List<Integer> removed = new java.util.ArrayList<>();
        int shared = Math.min(previous.size(), next.size());
        for (int i = 0; i < shared; i++) {
            if (!previous.get(i).equals(next.get(i))) {
                changed.add(i);
            }
        }
        for (int i = shared; i < next.size(); i++) {
            added.add(i);
        }
        for (int i = shared; i < previous.size(); i++) {
            removed.add(i);
        }
        return new Plan(changed, added, removed);
    }
}
