package com.uxplima.uxmlib.schematic.nbt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A tag tree is read the way the format defines it, and a hostile one is refused before it costs memory.
 *
 * <p>The bytes here are written by hand from the format, field by field, not by this module's writer: a
 * reader proved only against its own writer proves only that the two agree.
 */
class ATagTreeIsReadAsWrittenTest {

    @Test
    @DisplayName("Every kind of tag is read from bytes written by hand from the format")
    void everyKindIsRead() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10);
        out.writeUTF("root");
        out.writeByte(1);
        out.writeUTF("b");
        out.writeByte(-3);
        out.writeByte(2);
        out.writeUTF("s");
        out.writeShort(31000);
        out.writeByte(3);
        out.writeUTF("i");
        out.writeInt(-123456);
        out.writeByte(4);
        out.writeUTF("l");
        out.writeLong(1L << 40);
        out.writeByte(5);
        out.writeUTF("f");
        out.writeFloat(1.5f);
        out.writeByte(6);
        out.writeUTF("d");
        out.writeDouble(-2.25);
        out.writeByte(7);
        out.writeUTF("ba");
        out.writeInt(3);
        out.write(new byte[] {1, 2, 3});
        out.writeByte(8);
        out.writeUTF("str");
        out.writeUTF("minecraft:oak_log[axis=y]");
        out.writeByte(9);
        out.writeUTF("list");
        out.writeByte(3);
        out.writeInt(2);
        out.writeInt(7);
        out.writeInt(8);
        out.writeByte(10);
        out.writeUTF("inner");
        out.writeByte(8);
        out.writeUTF("Id");
        out.writeUTF("minecraft:chest");
        out.writeByte(0);
        out.writeByte(11);
        out.writeUTF("ia");
        out.writeInt(3);
        out.writeInt(1);
        out.writeInt(-1);
        out.writeInt(2);
        out.writeByte(12);
        out.writeUTF("la");
        out.writeInt(1);
        out.writeLong(-9L);
        out.writeByte(9);
        out.writeUTF("empty");
        out.writeByte(0);
        out.writeInt(0);
        out.writeByte(0);

        NamedTag read = NbtReader.withDefaults().read(new ByteArrayInputStream(bytes.toByteArray()));

        assertThat(read.name()).isEqualTo("root");
        NbtCompound root = read.tag();
        assertThat(root.get("b")).isEqualTo(new NbtTag.ByteTag((byte) -3));
        assertThat(root.intValue("s")).hasValue(31000);
        assertThat(root.intValue("i")).hasValue(-123456);
        assertThat(root.get("l")).isEqualTo(new NbtTag.LongTag(1L << 40));
        assertThat(root.doubleValue("f")).hasValue(1.5);
        assertThat(root.doubleValue("d")).hasValue(-2.25);
        assertThat(root.byteArray("ba"))
                .hasValueSatisfying(array -> assertThat(array).containsExactly(1, 2, 3));
        assertThat(root.string("str")).hasValue("minecraft:oak_log[axis=y]");
        assertThat(root.list("list").orElseThrow().values())
                .containsExactly(new NbtTag.IntTag(7), new NbtTag.IntTag(8));
        assertThat(root.compound("inner").orElseThrow().string("Id")).hasValue("minecraft:chest");
        assertThat(root.intArray("ia"))
                .hasValueSatisfying(array -> assertThat(array).containsExactly(1, -1, 2));
        assertThat(root.get("la")).isEqualTo(new NbtTag.LongArrayTag(new long[] {-9L}));
        assertThat(root.list("empty").orElseThrow().size()).isZero();
        assertThat(root.entries().keySet())
                .describedAs("the order the file wrote")
                .containsExactly("b", "s", "i", "l", "f", "d", "ba", "str", "list", "inner", "ia", "la", "empty");
    }

    @Test
    @DisplayName("What the writer writes, gzip or plain, reads back the same")
    void aWrittenTreeReadsBack() throws IOException {
        NbtCompound tree = NbtCompound.builder()
                .putInt("Version", 3)
                .putString("Name", "island ğüşiöç")
                .putIntArray("Offset", 1, -2, 3)
                .put("Data", new NbtTag.ByteArrayTag(new byte[] {0, 1, (byte) 0x80, 1}))
                .put(
                        "Entities",
                        NbtList.of(List.of(
                                NbtCompound.builder().putString("Id", "x").build())))
                .put("Nothing", NbtList.of(List.of()))
                .build();
        NamedTag root = new NamedTag("", tree);

        for (boolean gzip : new boolean[] {true, false}) {
            byte[] bytes = NbtWriter.toBytes(root, gzip);
            assertThat(bytes[0] == 0x1f && bytes[1] == (byte) 0x8b).isEqualTo(gzip);
            assertThat(NbtReader.withDefaults().read(new ByteArrayInputStream(bytes)))
                    .isEqualTo(root);
        }
    }

    @Test
    @DisplayName("A length larger than the bytes allowed is refused before an array is made for it")
    void aHugeLengthIsRefused() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10);
        out.writeUTF("");
        out.writeByte(12);
        out.writeUTF("lie");
        out.writeInt(Integer.MAX_VALUE);

        assertThatThrownBy(() -> NbtReader.withDefaults().read(new ByteArrayInputStream(bytes.toByteArray())))
                .isInstanceOf(NbtFormatException.class)
                .hasMessageContaining("bytes");
    }

    @Test
    @DisplayName("A negative length, a type that does not exist and an untyped list are refused")
    void malformedCountsAreRefused() throws IOException {
        assertRefused("is not a count", out -> {
            out.writeByte(7);
            out.writeUTF("x");
            out.writeInt(-1);
        });
        assertRefused("no tag of type", out -> {
            out.writeByte(42);
            out.writeUTF("x");
        });
        assertRefused("has no type", out -> {
            out.writeByte(9);
            out.writeUTF("x");
            out.writeByte(0);
            out.writeInt(1);
        });
    }

    @Test
    @DisplayName("Many small tags past the bytes allowed are refused as surely as one large one")
    void manySmallTagsAreCounted() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10);
        out.writeUTF("");
        for (int i = 0; i < 100; i++) {
            out.writeByte(1);
            out.writeUTF("");
            out.writeByte(i);
        }
        out.writeByte(0);
        byte[] file = bytes.toByteArray();

        assertThatThrownBy(() -> new NbtReader(new NbtLimits(300, 512)).read(new ByteArrayInputStream(file)))
                .isInstanceOf(NbtFormatException.class)
                .hasMessageContaining("allowed");
        assertThat(new NbtReader(new NbtLimits(file.length, 512))
                        .read(new ByteArrayInputStream(file))
                        .tag()
                        .entries())
                .hasSize(1);
    }

    @Test
    @DisplayName("A file that does not start with a compound is no tag file")
    void theRootIsACompound() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(3);
        out.writeUTF("");
        out.writeInt(1);

        assertThatThrownBy(() -> NbtReader.withDefaults().read(new ByteArrayInputStream(bytes.toByteArray())))
                .isInstanceOf(NbtFormatException.class);
    }

    @Test
    @DisplayName("Lists nested deeper than allowed are refused")
    void tooDeepIsRefused() throws IOException {
        int depth = 40;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10);
        out.writeUTF("");
        out.writeByte(9);
        out.writeUTF("deep");
        for (int i = 0; i < depth; i++) {
            out.writeByte(9);
            out.writeInt(1);
        }
        out.writeByte(0);
        out.writeInt(0);
        out.writeByte(0);
        byte[] file = bytes.toByteArray();

        assertThatThrownBy(() -> new NbtReader(new NbtLimits(1 << 20, 16)).read(new ByteArrayInputStream(file)))
                .isInstanceOf(NbtFormatException.class)
                .hasMessageContaining("deeper");
        assertThat(new NbtReader(new NbtLimits(1 << 20, 64))
                        .read(new ByteArrayInputStream(file))
                        .tag()
                        .has("deep"))
                .isTrue();
    }

    @Test
    @DisplayName("A small gzip that unpacks past the bytes allowed is refused while it unpacks")
    void aGzipBombIsRefused() throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw))) {
            out.writeByte(10);
            out.writeUTF("");
            for (int i = 0; i < 64; i++) {
                out.writeByte(7);
                out.writeUTF("z" + i);
                out.writeInt(1 << 20);
                out.write(new byte[1 << 20]);
            }
            out.writeByte(0);
        }
        byte[] small = raw.toByteArray();
        assertThat(small.length).isLessThan(1 << 20);

        assertThatThrownBy(() -> new NbtReader(new NbtLimits(8L << 20, 512)).read(new ByteArrayInputStream(small)))
                .isInstanceOf(NbtFormatException.class)
                .hasMessageContaining("allowed");
    }

    private interface Body {
        void write(DataOutputStream out) throws IOException;
    }

    private static void assertRefused(String why, Body body) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10);
        out.writeUTF("");
        body.write(out);
        out.writeByte(0);
        assertThatThrownBy(() -> NbtReader.withDefaults().read(new ByteArrayInputStream(bytes.toByteArray())))
                .isInstanceOf(NbtFormatException.class)
                .hasMessageContaining(why);
    }
}
