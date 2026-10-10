package com.uxplima.uxmlib.schematic.format;

import java.io.ByteArrayOutputStream;

/** The variable length integers a schematic packs its block and biome indices in, seven bits a byte. */
final class VarInts {

    private VarInts() {}

    /** Reads exactly {@code count} values from {@code bytes}, refusing too few, too many and a broken one. */
    static int[] decode(byte[] bytes, int count, String what) throws SchematicFormatException {
        int[] values = new int[count];
        int at = 0;
        for (int i = 0; i < count; i++) {
            int value = 0;
            int shift = 0;
            while (true) {
                if (at >= bytes.length) {
                    throw new SchematicFormatException(
                            "The " + what + " ends after " + i + " of its " + count + " entries");
                }
                byte next = bytes[at++];
                value |= (next & 0x7F) << shift;
                if ((next & 0x80) == 0) {
                    break;
                }
                shift += 7;
                if (shift > 28) {
                    throw new SchematicFormatException("The " + what + " holds a number longer than five bytes");
                }
            }
            if (value < 0) {
                throw new SchematicFormatException("The " + what + " holds a negative index");
            }
            values[i] = value;
        }
        if (at != bytes.length) {
            throw new SchematicFormatException(
                    "The " + what + " holds " + (bytes.length - at) + " bytes past its " + count + " entries");
        }
        return values;
    }

    static void encode(int value, ByteArrayOutputStream out) {
        int left = value;
        while ((left & ~0x7F) != 0) {
            out.write((left & 0x7F) | 0x80);
            left >>>= 7;
        }
        out.write(left);
    }
}
