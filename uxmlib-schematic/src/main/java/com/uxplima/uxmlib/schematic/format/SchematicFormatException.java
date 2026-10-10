package com.uxplima.uxmlib.schematic.format;

import java.io.IOException;

/** A file that is not a schematic this reader understands, with the reason in words. */
public final class SchematicFormatException extends IOException {

    private static final long serialVersionUID = 1L;

    public SchematicFormatException(String message) {
        super(message);
    }

    public SchematicFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
