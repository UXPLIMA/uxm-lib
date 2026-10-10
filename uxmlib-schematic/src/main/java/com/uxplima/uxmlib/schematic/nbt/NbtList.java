package com.uxplima.uxmlib.schematic.nbt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A list of tags of one kind. An empty list still names the kind it was written with, since a reader of
 * the file may ask.
 */
public record NbtList(byte elementType, List<NbtTag> values) implements NbtTag {

    public NbtList {
        values = List.copyOf(Objects.requireNonNull(values, "values must not be null"));
        for (NbtTag value : values) {
            if (value.type() != elementType) {
                throw new IllegalArgumentException(
                        "a list of type " + elementType + " cannot hold a tag of type " + value.type());
            }
        }
    }

    /** A list of {@code values}, all of the kind of the first, or an empty list of compounds. */
    public static NbtList of(List<? extends NbtTag> values) {
        byte kind = values.isEmpty() ? COMPOUND : values.getFirst().type();
        return new NbtList(kind, new ArrayList<>(values));
    }

    @Override
    public byte type() {
        return LIST;
    }

    public int size() {
        return values.size();
    }

    /** The compounds of this list, or nothing when it holds another kind. */
    public List<NbtCompound> compounds() {
        List<NbtCompound> found = new ArrayList<>(values.size());
        for (NbtTag value : values) {
            if (value instanceof NbtCompound compound) {
                found.add(compound);
            }
        }
        return found;
    }
}
