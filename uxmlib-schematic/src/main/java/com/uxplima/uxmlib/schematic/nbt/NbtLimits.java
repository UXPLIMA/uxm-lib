package com.uxplima.uxmlib.schematic.nbt;

/**
 * How much a tag tree may claim before the reader refuses it.
 *
 * <p>A length in a file is a promise the reader cannot check until it has read that far. Without a limit
 * a file of a few bytes could ask for an array of two billion longs, and a few kilobytes of gzip could
 * unpack to gigabytes. Every array is checked against the bytes still allowed before it is made.
 *
 * @param maxBytes how many bytes, after decompression, a tree may be read from
 * @param maxDepth how deeply lists and compounds may nest
 */
public record NbtLimits(long maxBytes, int maxDepth) {

    /** Enough for any real structure: half a gigabyte unpacked, nesting 512 deep as the game allows. */
    public static final NbtLimits DEFAULT = new NbtLimits(512L * 1024 * 1024, 512);

    public NbtLimits {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive: " + maxBytes);
        }
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("maxDepth must be positive: " + maxDepth);
        }
    }
}
