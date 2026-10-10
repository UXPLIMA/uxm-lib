package com.uxplima.uxmlib.schematic.nbt;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/**
 * Reads a tag tree, gzip compressed or plain, under {@link NbtLimits}.
 *
 * <p>Every count a file gives is checked against the bytes still allowed before anything is made for it,
 * so a hostile length fails with a {@link NbtFormatException} instead of an {@link OutOfMemoryError}.
 */
public final class NbtReader {

    private final NbtLimits limits;

    public NbtReader(NbtLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits must not be null");
    }

    public static NbtReader withDefaults() {
        return new NbtReader(NbtLimits.DEFAULT);
    }

    /** Reads the root compound of a file, and the name it was written under. */
    public NamedTag read(InputStream in) throws IOException {
        Objects.requireNonNull(in, "in must not be null");
        BufferedInputStream buffered = new BufferedInputStream(in);
        buffered.mark(2);
        int first = buffered.read();
        int second = buffered.read();
        buffered.reset();
        InputStream source = first == 0x1f && second == 0x8b ? new GZIPInputStream(buffered) : buffered;
        Budget budget = new Budget(limits.maxBytes());
        DataInputStream data = new DataInputStream(new Metered(source, budget));
        byte type = data.readByte();
        if (type != NbtTag.COMPOUND) {
            throw new NbtFormatException("A tag file starts with a compound, and this one starts with type " + type);
        }
        String name = data.readUTF();
        NbtCompound root = (NbtCompound) payload(data, NbtTag.COMPOUND, 1, budget);
        return new NamedTag(name, root);
    }

    private NbtTag payload(DataInputStream in, byte type, int depth, Budget budget) throws IOException {
        return switch (type) {
            case NbtTag.BYTE -> new NbtTag.ByteTag(in.readByte());
            case NbtTag.SHORT -> new NbtTag.ShortTag(in.readShort());
            case NbtTag.INT -> new NbtTag.IntTag(in.readInt());
            case NbtTag.LONG -> new NbtTag.LongTag(in.readLong());
            case NbtTag.FLOAT -> new NbtTag.FloatTag(in.readFloat());
            case NbtTag.DOUBLE -> new NbtTag.DoubleTag(in.readDouble());
            case NbtTag.STRING -> new NbtTag.StringTag(in.readUTF());
            case NbtTag.BYTE_ARRAY -> {
                byte[] values = new byte[count(in, 1, budget)];
                in.readFully(values);
                yield new NbtTag.ByteArrayTag(values);
            }
            case NbtTag.INT_ARRAY -> {
                int[] values = new int[count(in, 4, budget)];
                for (int i = 0; i < values.length; i++) {
                    values[i] = in.readInt();
                }
                yield new NbtTag.IntArrayTag(values);
            }
            case NbtTag.LONG_ARRAY -> {
                long[] values = new long[count(in, 8, budget)];
                for (int i = 0; i < values.length; i++) {
                    values[i] = in.readLong();
                }
                yield new NbtTag.LongArrayTag(values);
            }
            case NbtTag.LIST -> list(in, depth, budget);
            case NbtTag.COMPOUND -> compound(in, depth, budget);
            default -> throw new NbtFormatException("There is no tag of type " + type);
        };
    }

    private NbtList list(DataInputStream in, int depth, Budget budget) throws IOException {
        deeper(depth);
        byte elementType = in.readByte();
        int size = count(in, 1, budget);
        if (elementType == NbtTag.END && size > 0) {
            throw new NbtFormatException("A list of " + size + " has no type for its elements");
        }
        List<NbtTag> values = new ArrayList<>(Math.min(size, 1024));
        for (int i = 0; i < size; i++) {
            values.add(payload(in, elementType, depth + 1, budget));
        }
        return new NbtList(elementType == NbtTag.END ? NbtTag.COMPOUND : elementType, values);
    }

    private NbtCompound compound(DataInputStream in, int depth, Budget budget) throws IOException {
        deeper(depth);
        Map<String, NbtTag> entries = new LinkedHashMap<>();
        while (true) {
            byte type = in.readByte();
            if (type == NbtTag.END) {
                return new NbtCompound(entries);
            }
            String name = in.readUTF();
            entries.put(name, payload(in, type, depth + 1, budget));
        }
    }

    private void deeper(int depth) throws NbtFormatException {
        if (depth > limits.maxDepth()) {
            throw new NbtFormatException("The tags nest deeper than " + limits.maxDepth());
        }
    }

    /** Reads a count and checks the bytes it needs are still allowed before anything is made for it. */
    private static int count(DataInputStream in, int bytesEach, Budget budget) throws IOException {
        int count = in.readInt();
        if (count < 0) {
            throw new NbtFormatException("A count of " + count + " is not a count");
        }
        budget.require((long) count * bytesEach);
        return count;
    }

    /** The bytes still allowed. */
    private static final class Budget {

        private long left;
        private final long total;

        Budget(long total) {
            this.left = total;
            this.total = total;
        }

        void spend(long bytes) throws NbtFormatException {
            left -= bytes;
            if (left < 0) {
                throw new NbtFormatException("The tags are larger than the " + total + " bytes allowed");
            }
        }

        void require(long bytes) throws NbtFormatException {
            if (bytes > left) {
                throw new NbtFormatException(
                        "A length asks for " + bytes + " bytes, more than the " + left + " still allowed");
            }
        }
    }

    /** Spends the budget on every byte read. */
    private static final class Metered extends FilterInputStream {

        private final Budget budget;

        Metered(InputStream in, Budget budget) {
            super(in);
            this.budget = budget;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                budget.spend(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                budget.spend(read);
            }
            return read;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = super.skip(n);
            budget.spend(skipped);
            return skipped;
        }
    }
}
