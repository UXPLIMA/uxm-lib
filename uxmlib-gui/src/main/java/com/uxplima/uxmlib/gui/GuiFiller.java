package com.uxplima.uxmlib.gui;

import java.util.Objects;

import com.uxplima.uxmlib.gui.item.GuiItem;

/**
 * Layout helpers for a chest-style {@link Gui}: fill the border, a row, a column, a rectangle, or every
 * empty slot, without writing the slot arithmetic by hand. Obtain one with {@link Gui#filler()}. Every
 * method assumes a nine-wide grid (a chest menu); on a non-chest {@link GuiType} menu only {@link #fill}
 * and {@link #fillEmpty} are meaningful. Rows and columns are 1-indexed to match {@link Gui#set(int, int,
 * GuiItem)}.
 */
public final class GuiFiller {

    private static final int WIDTH = 9;

    private final Gui gui;

    GuiFiller(Gui gui) {
        this.gui = Objects.requireNonNull(gui, "gui");
    }

    /** Put {@code item} in every slot, overwriting what is there. */
    public GuiFiller fill(GuiItem item) {
        Objects.requireNonNull(item, "item");
        for (int slot = 0; slot < gui.size(); slot++) {
            gui.set(slot, item);
        }
        return this;
    }

    /**
     * Fill every slot by cycling through {@code items} (A, B, A, B, …) across the menu's true capacity, so a
     * two-tone glass border or a striped background is one call. Sizes off {@link Gui#size()}, so it is
     * correct on a non-chest {@link GuiType} menu (a hopper has five slots, not nine).
     */
    public GuiFiller fill(java.util.List<GuiItem> items) {
        Objects.requireNonNull(items, "items");
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        for (int slot = 0; slot < gui.size(); slot++) {
            gui.set(slot, items.get(slot % items.size()));
        }
        return this;
    }

    /** Put {@code item} in every slot that is currently empty. */
    public GuiFiller fillEmpty(GuiItem item) {
        Objects.requireNonNull(item, "item");
        for (int slot = 0; slot < gui.size(); slot++) {
            if (gui.getItem(slot) == null) {
                gui.set(slot, item);
            }
        }
        return this;
    }

    /** Put {@code item} around the outer edge of the grid (top and bottom rows, first and last columns). */
    public GuiFiller fillBorder(GuiItem item) {
        Objects.requireNonNull(item, "item");
        int rows = rows();
        if (rows < 1) {
            return this;
        }
        for (int col = 1; col <= WIDTH; col++) {
            gui.set(1, col, item);
            gui.set(rows, col, item);
        }
        for (int row = 1; row <= rows; row++) {
            gui.set(row, 1, item);
            gui.set(row, WIDTH, item);
        }
        return this;
    }

    /** Put {@code item} across every slot of 1-indexed {@code row}. */
    public GuiFiller fillRow(int row, GuiItem item) {
        Objects.requireNonNull(item, "item");
        for (int col = 1; col <= WIDTH; col++) {
            gui.set(row, col, item);
        }
        return this;
    }

    /** Put {@code item} down every slot of 1-indexed {@code col}. */
    public GuiFiller fillColumn(int col, GuiItem item) {
        Objects.requireNonNull(item, "item");
        int rows = rows();
        for (int row = 1; row <= rows; row++) {
            gui.set(row, col, item);
        }
        return this;
    }

    /**
     * Lay items out from a character mask: each string in {@code mask} is one row, each non-space char is
     * looked up in {@code legend} and placed in that slot, and a space (or a char absent from the legend)
     * leaves the slot untouched. A compact, declarative way to design a menu: the API half of a
     * config-driven layout.
     *
     * <pre>{@code
     * filler.pattern(List.of(
     *     "XXXXXXXXX",
     *     "X       X",
     *     "XXXXXXXXX"), Map.of('X', border));
     * }</pre>
     */
    public GuiFiller pattern(java.util.List<String> mask, java.util.Map<Character, GuiItem> legend) {
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(legend, "legend");
        for (int row = 0; row < mask.size(); row++) {
            String line = mask.get(row);
            for (int col = 0; col < line.length() && col < WIDTH; col++) {
                GuiItem item = legend.get(line.charAt(col));
                if (item != null) {
                    int slot = row * WIDTH + col;
                    if (slot < gui.size()) {
                        gui.set(slot, item);
                    }
                }
            }
        }
        return this;
    }

    /** Put {@code item} around the {@code offset}-th ring inward from the edge (0 = the outer border). */
    public GuiFiller fillBorder(int offset, GuiItem item) {
        Objects.requireNonNull(item, "item");
        int rows = rows();
        int top = 1 + offset;
        int bottom = rows - offset;
        int left = 1 + offset;
        int right = WIDTH - offset;
        if (top > bottom || left > right) {
            return this;
        }
        for (int col = left; col <= right; col++) {
            gui.set(top, col, item);
            gui.set(bottom, col, item);
        }
        for (int row = top; row <= bottom; row++) {
            gui.set(row, left, item);
            gui.set(row, right, item);
        }
        return this;
    }

    /** Put {@code item} in the inclusive rectangle from ({@code row1},{@code col1}) to ({@code row2},{@code col2}). */
    public GuiFiller fillRect(int row1, int col1, int row2, int col2, GuiItem item) {
        Objects.requireNonNull(item, "item");
        int topRow = Math.min(row1, row2);
        int bottomRow = Math.max(row1, row2);
        int leftCol = Math.min(col1, col2);
        int rightCol = Math.max(col1, col2);
        for (int row = topRow; row <= bottomRow; row++) {
            for (int col = leftCol; col <= rightCol; col++) {
                gui.set(row, col, item);
            }
        }
        return this;
    }

    /**
     * The slots of the outer border of a {@code width}×{@code height} grid in clockwise order from the top
     * left, rotated to start {@code offset} slots along the ring. The ordered ring a "walk a highlight around
     * the edge" animation needs; delegates to {@link SlotPattern#orderedBorderSlots}.
     */
    public static java.util.List<Integer> orderedBorderSlots(int width, int height, int offset) {
        return SlotPattern.orderedBorderSlots(width, height, offset);
    }

    private int rows() {
        return gui.size() / WIDTH;
    }
}
