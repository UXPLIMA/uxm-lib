package com.uxplima.uxmlib.schematic;

import java.util.List;
import java.util.Objects;

/**
 * The biome at every block of a schematic: a palette, and an index per block in the order the blocks are
 * in. A file written before biomes were three dimensional gives one per column, kept as it is and read as
 * that column's biome at every height.
 */
public final class SchematicBiomes {

    private final List<String> palette;
    private final PaletteIndices indices;
    private final boolean columns;

    private SchematicBiomes(List<String> palette, PaletteIndices indices, boolean columns) {
        this.palette = List.copyOf(Objects.requireNonNull(palette, "palette must not be null"));
        if (indices.highest() >= this.palette.size()) {
            throw new IllegalArgumentException("biome index " + indices.highest() + " is outside the palette");
        }
        this.indices = indices.handOver();
        this.columns = columns;
    }

    /** A biome for every block. */
    public static SchematicBiomes perBlock(List<String> palette, PaletteIndices indices) {
        return new SchematicBiomes(palette, indices, false);
    }

    /** A biome for every column, {@code x} fastest then {@code z}, the same at every height. */
    public static SchematicBiomes perColumn(List<String> palette, PaletteIndices indices) {
        return new SchematicBiomes(palette, indices, true);
    }

    public List<String> palette() {
        return palette;
    }

    /** Whether one biome stands for a whole column. */
    public boolean perColumn() {
        return columns;
    }

    /** The palette index of the biome at the block index {@code blockIndex} of a schematic this fits. */
    public int indexAt(int blockIndex, int width, int length) {
        return columns ? indices.get(blockIndex % (width * length)) : indices.get(blockIndex);
    }

    /** How many indices are held: one a block, or one a column. */
    public int size() {
        return indices.size();
    }

    boolean fits(int width, int height, int length) {
        return columns ? indices.size() == (long) width * length : indices.size() == (long) width * height * length;
    }
}
