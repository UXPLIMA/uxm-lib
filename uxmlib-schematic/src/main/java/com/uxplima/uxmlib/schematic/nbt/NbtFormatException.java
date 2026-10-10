package com.uxplima.uxmlib.schematic.nbt;

import java.io.IOException;

/** Bytes that are not a tag tree, or one larger or deeper than the reader allows. */
public final class NbtFormatException extends IOException {

    private static final long serialVersionUID = 1L;

    public NbtFormatException(String message) {
        super(message);
    }
}
