package com.uxplima.uxmlib.schematic.paper;

import java.util.Objects;

/**
 * How a schematic is pasted.
 *
 * @param rotation the turn about the paste point
 * @param pasteAir whether the schematic's air clears what is there, or leaves it standing
 * @param entities whether the entities saved with it are made
 * @param biomes whether the biomes saved with it are set
 * @param blocksPerTick how many positions one chunk works through in a tick before it waits for the next
 * @param concurrency how many chunks are worked at once
 */
public record PasteOptions(
        Rotation rotation, boolean pasteAir, boolean entities, boolean biomes, int blocksPerTick, int concurrency) {

    /** No turn, air left standing, entities and biomes carried, 2048 positions a chunk a tick, four chunks. */
    public static final PasteOptions DEFAULT = new PasteOptions(Rotation.NONE, false, true, true, 2048, 4);

    public PasteOptions {
        Objects.requireNonNull(rotation, "rotation must not be null");
        if (blocksPerTick < 1) {
            throw new IllegalArgumentException("blocksPerTick is at least 1, not " + blocksPerTick);
        }
        if (concurrency < 1) {
            throw new IllegalArgumentException("concurrency is at least 1, not " + concurrency);
        }
    }

    public PasteOptions withRotation(Rotation rotation) {
        return new PasteOptions(rotation, pasteAir, entities, biomes, blocksPerTick, concurrency);
    }

    public PasteOptions withPasteAir(boolean pasteAir) {
        return new PasteOptions(rotation, pasteAir, entities, biomes, blocksPerTick, concurrency);
    }

    public PasteOptions withEntities(boolean entities) {
        return new PasteOptions(rotation, pasteAir, entities, biomes, blocksPerTick, concurrency);
    }

    public PasteOptions withBiomes(boolean biomes) {
        return new PasteOptions(rotation, pasteAir, entities, biomes, blocksPerTick, concurrency);
    }

    public PasteOptions withBlocksPerTick(int blocksPerTick) {
        return new PasteOptions(rotation, pasteAir, entities, biomes, blocksPerTick, concurrency);
    }

    public PasteOptions withConcurrency(int concurrency) {
        return new PasteOptions(rotation, pasteAir, entities, biomes, blocksPerTick, concurrency);
    }
}
