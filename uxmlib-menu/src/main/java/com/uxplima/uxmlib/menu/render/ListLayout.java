package com.uxplima.uxmlib.menu.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmlib.menu.eval.PinnedEntry;
import com.uxplima.uxmlib.menu.spec.ListAlign;
import com.uxplima.uxmlib.menu.spec.MenuItemSpec;
import org.jspecify.annotations.NullMarked;

/**
 * Where the entries of one list page sit, and which place in the whole list each cell stands for. The pagination
 * decides which entries a page shows; this decides the slot each of them takes when the file asked for a centred list,
 * and numbers the cells so a {@code %position%} in the template or in the empty item reads the place a viewer counts.
 */
@NullMarked
final class ListLayout {

    /** The token a list's template and empty item write for the place of their cell, counted from one. */
    static final String POSITION = "%position%";

    private static final int SLOTS_PER_ROW = 9;

    private ListLayout() {}

    /**
     * The placements of a page moved for {@code align}. A start-aligned list keeps them as the pagination laid them.
     * A centred list fills its full rows the same way and moves the short last row to the middle of its row. A page
     * that holds a pinned entry is left as it is, because a pinned entry names its own slot.
     */
    static List<Map.Entry<Integer, Object>> arrange(
            ListAlign align, List<Map.Entry<Integer, Object>> placements, List<Integer> contentSlots) {
        if (align != ListAlign.CENTER || placements.stream().anyMatch(p -> p.getValue() instanceof PinnedEntry)) {
            return placements;
        }
        List<Integer> slots = centred(contentSlots, placements.size());
        List<Map.Entry<Integer, Object>> moved = new ArrayList<>(placements.size());
        for (int index = 0; index < placements.size(); index++) {
            moved.add(Map.entry(slots.get(index), placements.get(index).getValue()));
        }
        return moved;
    }

    /**
     * The slots {@code count} entries take in a centred list: the content slots grouped into rows in the order the
     * file wrote them, each full row taken whole, and the row the entries run out in taken from its middle.
     */
    static List<Integer> centred(List<Integer> contentSlots, int count) {
        List<List<Integer>> rows = rows(contentSlots);
        List<Integer> taken = new ArrayList<>(count);
        int left = count;
        for (List<Integer> row : rows) {
            if (left <= 0) {
                break;
            }
            if (left >= row.size()) {
                taken.addAll(row);
                left -= row.size();
            } else {
                int from = (row.size() - left) / 2;
                taken.addAll(row.subList(from, from + left));
                left = 0;
            }
        }
        return taken;
    }

    /** The content slots cut into runs that share a window row, in the order the file wrote them. */
    private static List<List<Integer>> rows(List<Integer> contentSlots) {
        List<List<Integer>> rows = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        int row = -1;
        for (int slot : contentSlots) {
            if (slot / SLOTS_PER_ROW != row && !current.isEmpty()) {
                rows.add(current);
                current = new ArrayList<>();
            }
            row = slot / SLOTS_PER_ROW;
            current.add(slot);
        }
        if (!current.isEmpty()) {
            rows.add(current);
        }
        return rows;
    }

    /** {@code item} with every {@code %position%} in its material, name and lore written as {@code position}. */
    static MenuItemSpec atPosition(MenuItemSpec item, int position) {
        String place = String.valueOf(position);
        List<String> lore = new ArrayList<>(item.lore().size());
        boolean changed = false;
        for (String line : item.lore()) {
            String written = line.replace(POSITION, place);
            changed |= !written.equals(line);
            lore.add(written);
        }
        String name = item.name().replace(POSITION, place);
        String material = item.material().replace(POSITION, place);
        if (!changed && name.equals(item.name()) && material.equals(item.material())) {
            return item;
        }
        return item.withName(name).withLore(lore).withMaterial(material);
    }
}
