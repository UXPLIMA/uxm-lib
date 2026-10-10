package com.uxplima.uxmlib.schematic;

/**
 * The palette index of every position of a schematic, filled once and then handed to it.
 *
 * <p>A schematic holds one of these for its blocks and one for its biomes. A reader fills it in place and
 * hands it over, so a large file is held once rather than once filled and again copied. Once handed over
 * it can no longer be changed.
 */
public final class PaletteIndices {

    private final int[] values;
    private boolean handedOver;

    private PaletteIndices(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative: " + size);
        }
        this.values = new int[size];
    }

    /** Indices for {@code size} positions, every one zero. */
    public static PaletteIndices of(int size) {
        return new PaletteIndices(size);
    }

    public int size() {
        return values.length;
    }

    public int get(int position) {
        return values[position];
    }

    public void set(int position, int index) {
        if (handedOver) {
            throw new IllegalStateException("these indices belong to a schematic now");
        }
        if (index < 0) {
            throw new IllegalArgumentException("a palette index is not negative: " + index);
        }
        values[position] = index;
    }

    /** The highest index held, or -1 when there are no positions. */
    int highest() {
        int highest = -1;
        for (int value : values) {
            highest = Math.max(highest, value);
        }
        return highest;
    }

    PaletteIndices handOver() {
        if (handedOver) {
            throw new IllegalStateException("these indices were handed to a schematic already");
        }
        handedOver = true;
        return this;
    }
}
