package com.uxplima.uxmlib.schematic.format;

import java.io.ByteArrayOutputStream;

import com.uxplima.uxmlib.schematic.PaletteIndices;

/** The variable length integers a schematic packs its block and biome indices in, seven bits a byte. */
final class VarInts {

    private VarInts() {}

    /**
     * Reads exactly {@code count} values from {@code bytes}, refusing too few, too many and a broken one.
     * Every value takes at least a byte, so fewer bytes than values are refused before anything is made.
     */
    static PaletteIndices decode(byte[] bytes, int count, String what) throws SchematicFormatException {
        if (bytes.length < count) {
            throw new SchematicFormatException(
                    "The " + what + " holds " + bytes.length + " bytes, too few for its " + count + " entries");
        }
        PaletteIndices values = PaletteIndices.of(count);
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
            values.set(i, value);
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
