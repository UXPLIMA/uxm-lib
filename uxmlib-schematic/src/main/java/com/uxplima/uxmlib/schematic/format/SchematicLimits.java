package com.uxplima.uxmlib.schematic.format;

import java.util.Objects;

import com.uxplima.uxmlib.schematic.nbt.NbtLimits;

/**
 * How large a schematic the reader takes.
 *
 * @param nbt the limits on the tag tree itself
 * @param maxVolume how many block positions a schematic may hold; each costs four bytes in memory
 */
public record SchematicLimits(NbtLimits nbt, long maxVolume) {

    /** Sixty four million positions, a quarter of a gigabyte held: far past any island or spawn. */
    public static final SchematicLimits DEFAULT = new SchematicLimits(NbtLimits.DEFAULT, 64L * 1024 * 1024);

    public SchematicLimits {
        Objects.requireNonNull(nbt, "nbt must not be null");
        if (maxVolume <= 0 || maxVolume > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("maxVolume must be between 1 and " + (Integer.MAX_VALUE - 8));
        }
    }
}
