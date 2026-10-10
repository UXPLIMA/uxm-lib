package com.uxplima.uxmlib.schematic.nbt;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

/** A set of named tags, in the order they were written. */
public record NbtCompound(Map<String, NbtTag> entries) implements NbtTag {

    public NbtCompound {
        entries = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(entries, "entries")));
    }

    public static NbtCompound empty() {
        return new NbtCompound(Map.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A builder that starts from this compound's entries. */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.entries.putAll(entries);
        return builder;
    }

    @Override
    public byte type() {
        return COMPOUND;
    }

    public boolean has(String key) {
        return entries.containsKey(key);
    }

    public @Nullable NbtTag get(String key) {
        return entries.get(key);
    }

    public OptionalInt intValue(String key) {
        NbtTag tag = entries.get(key);
        return tag == null ? OptionalInt.empty() : NbtTag.asInt(tag);
    }

    public OptionalDouble doubleValue(String key) {
        NbtTag tag = entries.get(key);
        return tag == null ? OptionalDouble.empty() : NbtTag.asDouble(tag);
    }

    public Optional<String> string(String key) {
        return entries.get(key) instanceof StringTag text ? Optional.of(text.value()) : Optional.empty();
    }

    public Optional<NbtCompound> compound(String key) {
        return entries.get(key) instanceof NbtCompound compound ? Optional.of(compound) : Optional.empty();
    }

    public Optional<NbtList> list(String key) {
        return entries.get(key) instanceof NbtList list ? Optional.of(list) : Optional.empty();
    }

    public Optional<int[]> intArray(String key) {
        return entries.get(key) instanceof IntArrayTag array ? Optional.of(array.value()) : Optional.empty();
    }

    public Optional<byte[]> byteArray(String key) {
        return entries.get(key) instanceof ByteArrayTag array ? Optional.of(array.value()) : Optional.empty();
    }

    /** Builds a compound, keeping the order entries were put in. */
    public static final class Builder {

        private final Map<String, NbtTag> entries = new LinkedHashMap<>();

        private Builder() {}

        public Builder put(String key, NbtTag value) {
            entries.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder putByte(String key, int value) {
            return put(key, new ByteTag((byte) value));
        }

        public Builder putShort(String key, int value) {
            return put(key, new ShortTag((short) value));
        }

        public Builder putInt(String key, int value) {
            return put(key, new IntTag(value));
        }

        public Builder putLong(String key, long value) {
            return put(key, new LongTag(value));
        }

        public Builder putFloat(String key, float value) {
            return put(key, new FloatTag(value));
        }

        public Builder putDouble(String key, double value) {
            return put(key, new DoubleTag(value));
        }

        public Builder putString(String key, String value) {
            return put(key, new StringTag(value));
        }

        public Builder putIntArray(String key, int... value) {
            return put(key, new IntArrayTag(value));
        }

        public Builder remove(String key) {
            entries.remove(key);
            return this;
        }

        public NbtCompound build() {
            return new NbtCompound(entries);
        }
    }
}
