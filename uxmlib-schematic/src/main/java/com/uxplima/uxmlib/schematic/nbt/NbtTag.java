package com.uxplima.uxmlib.schematic.nbt;

/**
 * One value of a tag tree. The twelve kinds a file may hold, each a record of its own.
 *
 * <p>The number arrays compare by content, so two trees read from the same bytes are equal.
 */
public sealed interface NbtTag
        permits NbtTag.ByteTag,
                NbtTag.ShortTag,
                NbtTag.IntTag,
                NbtTag.LongTag,
                NbtTag.FloatTag,
                NbtTag.DoubleTag,
                NbtTag.ByteArrayTag,
                NbtTag.StringTag,
                NbtList,
                NbtCompound,
                NbtTag.IntArrayTag,
                NbtTag.LongArrayTag {

    byte END = 0;
    byte BYTE = 1;
    byte SHORT = 2;
    byte INT = 3;
    byte LONG = 4;
    byte FLOAT = 5;
    byte DOUBLE = 6;
    byte BYTE_ARRAY = 7;
    byte STRING = 8;
    byte LIST = 9;
    byte COMPOUND = 10;
    byte INT_ARRAY = 11;
    byte LONG_ARRAY = 12;

    /** The type id this tag is written under. */
    byte type();

    record ByteTag(byte value) implements NbtTag {
        @Override
        public byte type() {
            return BYTE;
        }
    }

    record ShortTag(short value) implements NbtTag {
        @Override
        public byte type() {
            return SHORT;
        }
    }

    record IntTag(int value) implements NbtTag {
        @Override
        public byte type() {
            return INT;
        }
    }

    record LongTag(long value) implements NbtTag {
        @Override
        public byte type() {
            return LONG;
        }
    }

    record FloatTag(float value) implements NbtTag {
        @Override
        public byte type() {
            return FLOAT;
        }
    }

    record DoubleTag(double value) implements NbtTag {
        @Override
        public byte type() {
            return DOUBLE;
        }
    }

    record StringTag(String value) implements NbtTag {
        public StringTag {
            java.util.Objects.requireNonNull(value, "value must not be null");
        }

        @Override
        public byte type() {
            return STRING;
        }
    }

    /** An array of byte values, compared by content. */
    final class ByteArrayTag implements NbtTag {

        private final byte[] value;

        public ByteArrayTag(byte[] value) {
            this.value = value.clone();
        }

        public byte[] value() {
            return value.clone();
        }

        public int length() {
            return value.length;
        }

        @Override
        public byte type() {
            return BYTE_ARRAY;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ByteArrayTag that && java.util.Arrays.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return java.util.Arrays.hashCode(value);
        }

        @Override
        public String toString() {
            return "ByteArrayTag[" + value.length + "]";
        }
    }

    /** An array of int values, compared by content. */
    final class IntArrayTag implements NbtTag {

        private final int[] value;

        public IntArrayTag(int[] value) {
            this.value = value.clone();
        }

        public int[] value() {
            return value.clone();
        }

        public int length() {
            return value.length;
        }

        @Override
        public byte type() {
            return INT_ARRAY;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof IntArrayTag that && java.util.Arrays.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return java.util.Arrays.hashCode(value);
        }

        @Override
        public String toString() {
            return "IntArrayTag" + java.util.Arrays.toString(value);
        }
    }

    /** An array of long values, compared by content. */
    final class LongArrayTag implements NbtTag {

        private final long[] value;

        public LongArrayTag(long[] value) {
            this.value = value.clone();
        }

        public long[] value() {
            return value.clone();
        }

        public int length() {
            return value.length;
        }

        @Override
        public byte type() {
            return LONG_ARRAY;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof LongArrayTag that && java.util.Arrays.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return java.util.Arrays.hashCode(value);
        }

        @Override
        public String toString() {
            return "LongArrayTag[" + value.length + "]";
        }
    }

    /** A number tag's value as an int, or nothing for a tag that is not a whole number. */
    static java.util.OptionalInt asInt(NbtTag tag) {
        return switch (tag) {
            case ByteTag b -> java.util.OptionalInt.of(b.value());
            case ShortTag s -> java.util.OptionalInt.of(s.value());
            case IntTag i -> java.util.OptionalInt.of(i.value());
            case LongTag l
            when l.value() >= Integer.MIN_VALUE && l.value() <= Integer.MAX_VALUE ->
                java.util.OptionalInt.of((int) l.value());
            default -> java.util.OptionalInt.empty();
        };
    }

    /** A number tag's value as a double, or nothing for a tag that is not a number. */
    static java.util.OptionalDouble asDouble(NbtTag tag) {
        return switch (tag) {
            case ByteTag b -> java.util.OptionalDouble.of(b.value());
            case ShortTag s -> java.util.OptionalDouble.of(s.value());
            case IntTag i -> java.util.OptionalDouble.of(i.value());
            case LongTag l -> java.util.OptionalDouble.of((double) l.value());
            case FloatTag f -> java.util.OptionalDouble.of(f.value());
            case DoubleTag d -> java.util.OptionalDouble.of(d.value());
            default -> java.util.OptionalDouble.empty();
        };
    }
}
