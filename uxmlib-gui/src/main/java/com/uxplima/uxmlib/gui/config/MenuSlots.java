package com.uxplima.uxmlib.gui.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.spongepowered.configurate.ConfigurationNode;

/**
 * Where an item of a config-defined menu is drawn.
 *
 * <p>A menu file names a single {@code slot = 13} or a list of ranges, {@code slots = ["0-44", "51-53"]}.
 * A range is inclusive at both ends and a bare number is a range of one, so a paginated grid and a single
 * button are written the same way and an operator moves either without touching code.
 *
 * <p>Every slot is checked against the size of the window as it is read. A slot outside it is a layout the
 * server would silently drop, and a button nobody can click is worse than a file that refuses to load.
 */
public final class MenuSlots {

    /** How many slots one row of a chest menu holds. */
    public static final int PER_ROW = 9;

    private MenuSlots() {}

    /**
     * Read the {@code slot} and {@code slots} of one item, in the order the file gives them.
     *
     * @param capacity how many slots the window holds, which is nine per row
     * @throws IllegalArgumentException when the item names no slot, names both forms, or names a slot
     *     that the window does not hold
     */
    public static List<Integer> read(ConfigurationNode node, String id, int capacity) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(id, "id");

        ConfigurationNode single = node.node("slot");
        ConfigurationNode many = node.node("slots");
        if (!single.virtual() && !many.virtual()) {
            throw new IllegalArgumentException(id + " names both a slot and a slots list. Keep one of them.");
        }
        if (!single.virtual()) {
            return List.of(checked(single.getInt(), id, capacity));
        }
        if (many.virtual()) {
            throw new IllegalArgumentException(id + " names no slot. Write slot = 13 or slots = [\"0-8\"].");
        }

        Set<Integer> read = new LinkedHashSet<>();
        for (ConfigurationNode child : many.childrenList()) {
            read.addAll(range(String.valueOf(child.getString("")), id, capacity));
        }
        if (read.isEmpty()) {
            throw new IllegalArgumentException(id + " names an empty slots list.");
        }
        return List.copyOf(read);
    }

    /** One entry of a {@code slots} list: {@code "13"} or {@code "0-44"}, both ends included. */
    private static List<Integer> range(String written, String id, int capacity) {
        String text = written.trim();
        int dash = text.indexOf('-');
        if (dash < 0) {
            return List.of(checked(number(text, id), id, capacity));
        }
        int from = checked(number(text.substring(0, dash), id), id, capacity);
        int to = checked(number(text.substring(dash + 1), id), id, capacity);
        if (to < from) {
            throw new IllegalArgumentException(id + " has the range " + text + " the wrong way round.");
        }
        List<Integer> slots = new ArrayList<>();
        for (int slot = from; slot <= to; slot++) {
            slots.add(slot);
        }
        return slots;
    }

    private static int number(String text, String id) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(id + " has the slot '" + text + "', which is not a number.");
        }
    }

    private static int checked(int slot, String id, int capacity) {
        if (slot < 0 || slot >= capacity) {
            throw new IllegalArgumentException(
                    id + " sits in slot " + slot + ", and this menu holds " + capacity + " slots.");
        }
        return slot;
    }
}
