package com.uxplima.uxmlib.schematic.nbt;

import java.util.Objects;

/** The root of a file: a compound and the name it was written under, usually empty. */
public record NamedTag(String name, NbtCompound tag) {

    public NamedTag {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(tag, "tag must not be null");
    }
}
