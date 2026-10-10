package com.uxplima.uxmlib.schematic;

import java.util.List;
import java.util.Objects;

/**
 * The biome at every block of a schematic, a palette and an index per block in the order the blocks are
 * in. A file written before biomes were three dimensional gives one per column; it is read as that column's
 * biome at every height.
 */
public final class SchematicBiomes {

    private final List<String> palette;
    private final int[] indices;

    public SchematicBiomes(List<String> palette, int[] indices) {
        this.palette = List.copyOf(Objects.requireNonNull(palette, "palette must not be null"));
        this.indices = indices.clone();
        for (int index : this.indices) {
            if (index < 0 || index >= this.palette.size()) {
                throw new IllegalArgumentException("biome index " + index + " is outside the palette");
            }
        }
    }

    public List<String> palette() {
        return palette;
    }

    /** The palette index of the biome at block index {@code index}. */
    public int indexAt(int index) {
        return indices[index];
    }

    public int size() {
        return indices.length;
    }
}
