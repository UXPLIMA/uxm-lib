package com.uxplima.uxmlib.schematic.paper;

import java.util.Objects;

import com.uxplima.uxmlib.schematic.nbt.NbtCompound;

/**
 * How a part of a world is saved.
 *
 * @param entities whether the entities standing in it are saved with it; players never are
 * @param biomes whether its biomes are saved with it
 * @param concurrency how many chunks are read at once
 * @param maxVolume how many block positions a capture may take, so a mistyped corner does not exhaust memory
 * @param metadata what the schematic says about itself, such as its name and author
 */
public record CaptureOptions(boolean entities, boolean biomes, int concurrency, long maxVolume, NbtCompound metadata) {

    /** Entities saved, biomes not, four chunks at once, the volume the reader takes, no metadata. */
    public static final CaptureOptions DEFAULT =
            new CaptureOptions(true, false, 4, 32L * 1024 * 1024, NbtCompound.empty());

    public CaptureOptions {
        Objects.requireNonNull(metadata, "metadata must not be null");
        if (concurrency < 1) {
            throw new IllegalArgumentException("concurrency is at least 1, not " + concurrency);
        }
        if (maxVolume < 1 || maxVolume > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("maxVolume must be between 1 and " + (Integer.MAX_VALUE - 8));
        }
    }

    public CaptureOptions withEntities(boolean entities) {
        return new CaptureOptions(entities, biomes, concurrency, maxVolume, metadata);
    }

    public CaptureOptions withBiomes(boolean biomes) {
        return new CaptureOptions(entities, biomes, concurrency, maxVolume, metadata);
    }

    public CaptureOptions withConcurrency(int concurrency) {
        return new CaptureOptions(entities, biomes, concurrency, maxVolume, metadata);
    }

    public CaptureOptions withMaxVolume(long maxVolume) {
        return new CaptureOptions(entities, biomes, concurrency, maxVolume, metadata);
    }

    public CaptureOptions withMetadata(NbtCompound metadata) {
        return new CaptureOptions(entities, biomes, concurrency, maxVolume, metadata);
    }
}
