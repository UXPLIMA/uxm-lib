package com.uxplima.uxmlib.schematic.nbt;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

/** Writes a tag tree, the way the game and every structure tool read one. */
public final class NbtWriter {

    private NbtWriter() {}

    /** Writes {@code root}, gzip compressed when asked, and leaves {@code out} open. */
    public static void write(NamedTag root, OutputStream out, boolean gzip) throws IOException {
        Objects.requireNonNull(root, "root must not be null");
        Objects.requireNonNull(out, "out must not be null");
        if (gzip) {
            GZIPOutputStream zipped = new GZIPOutputStream(new NonClosing(out));
            DataOutputStream data = new DataOutputStream(zipped);
            writeRoot(root, data);
            data.flush();
            zipped.finish();
            return;
        }
        DataOutputStream data = new DataOutputStream(new NonClosing(out));
        writeRoot(root, data);
        data.flush();
    }

    /** {@code root} as bytes. */
    public static byte[] toBytes(NamedTag root, boolean gzip) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        write(root, bytes, gzip);
        return bytes.toByteArray();
    }

    private static void writeRoot(NamedTag root, DataOutputStream out) throws IOException {
        out.writeByte(NbtTag.COMPOUND);
        out.writeUTF(root.name());
        payload(root.tag(), out);
    }

    private static void payload(NbtTag tag, DataOutputStream out) throws IOException {
        switch (tag) {
            case NbtTag.ByteTag b -> out.writeByte(b.value());
            case NbtTag.ShortTag s -> out.writeShort(s.value());
            case NbtTag.IntTag i -> out.writeInt(i.value());
            case NbtTag.LongTag l -> out.writeLong(l.value());
            case NbtTag.FloatTag f -> out.writeFloat(f.value());
            case NbtTag.DoubleTag d -> out.writeDouble(d.value());
            case NbtTag.StringTag text -> out.writeUTF(text.value());
            case NbtTag.ByteArrayTag array -> {
                byte[] values = array.value();
                out.writeInt(values.length);
                out.write(values);
            }
            case NbtTag.IntArrayTag array -> {
                int[] values = array.value();
                out.writeInt(values.length);
                for (int value : values) {
                    out.writeInt(value);
                }
            }
            case NbtTag.LongArrayTag array -> {
                long[] values = array.value();
                out.writeInt(values.length);
                for (long value : values) {
                    out.writeLong(value);
                }
            }
            case NbtList list -> {
                out.writeByte(list.values().isEmpty() ? NbtTag.END : list.elementType());
                out.writeInt(list.size());
                for (NbtTag value : list.values()) {
                    payload(value, out);
                }
            }
            case NbtCompound compound -> {
                for (Map.Entry<String, NbtTag> entry : compound.entries().entrySet()) {
                    out.writeByte(entry.getValue().type());
                    out.writeUTF(entry.getKey());
                    payload(entry.getValue(), out);
                }
                out.writeByte(NbtTag.END);
            }
        }
    }

    /** Lets the gzip stream finish without closing the stream the caller owns. */
    private static final class NonClosing extends java.io.FilterOutputStream {

        NonClosing(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            out.write(buffer, offset, length);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
