package com.uxplima.uxmlib.schematic.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import com.uxplima.uxmlib.schematic.Schematic;
import com.uxplima.uxmlib.schematic.SchematicBiomes;
import com.uxplima.uxmlib.schematic.SchematicBlockEntity;
import com.uxplima.uxmlib.schematic.SchematicEntity;
import com.uxplima.uxmlib.schematic.Vec3i;
import com.uxplima.uxmlib.schematic.nbt.NamedTag;
import com.uxplima.uxmlib.schematic.nbt.NbtCompound;
import com.uxplima.uxmlib.schematic.nbt.NbtLimits;
import com.uxplima.uxmlib.schematic.nbt.NbtList;
import com.uxplima.uxmlib.schematic.nbt.NbtTag;
import com.uxplima.uxmlib.schematic.nbt.NbtWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A Sponge file is read the way WorldEdit and the format's own text lay it out, versions 2 and 3.
 *
 * <p>The files are built here from the specification field by field, with the varints packed by hand, so
 * the reader is held to the format and not to this module's writer. The real files of the corpus test
 * the same reader against files nobody here made.
 */
class ASpongeFileIsReadAsToolsWriteItTest {

    /** A 2 x 3 x 2 box whose block at (x, y, z) is the palette entry (x + 2z + 4y) mod the palette. */
    private static final int W = 2;

    private static final int H = 3;
    private static final int L = 2;

    @Test
    @DisplayName("A version 3 file: grouped blocks, three dimensional biomes, Data compounds and the offset")
    void versionThree() throws IOException {
        List<String> palette = palette(200);
        NbtCompound schematic = NbtCompound.builder()
                .putInt("Version", 3)
                .putInt("DataVersion", 3953)
                .putShort("Width", W)
                .putShort("Height", H)
                .putShort("Length", L)
                .putIntArray("Offset", -1, -2, -3)
                .put(
                        "Blocks",
                        NbtCompound.builder()
                                .put("Palette", paletteTag(palette))
                                .put("Data", new NbtTag.ByteArrayTag(packed(i -> 150 + i)))
                                .put(
                                        "BlockEntities",
                                        NbtList.of(List.of(NbtCompound.builder()
                                                .putIntArray("Pos", 1, 2, 0)
                                                .putString("Id", "minecraft:chest")
                                                .put(
                                                        "Data",
                                                        NbtCompound.builder()
                                                                .putString("id", "minecraft:chest")
                                                                .putInt("x", 1)
                                                                .putInt("y", 2)
                                                                .putInt("z", 0)
                                                                .putString("CustomName", "\"Loot\"")
                                                                .build())
                                                .build())))
                                .build())
                .put(
                        "Biomes",
                        NbtCompound.builder()
                                .put("Palette", paletteTag(List.of("minecraft:plains", "minecraft:desert")))
                                .put("Data", new NbtTag.ByteArrayTag(packed(i -> i % 2)))
                                .build())
                .put("Entities", NbtList.of(List.of(entity("minecraft:armor_stand", 0.5, 1.0, 1.5, true))))
                .build();

        Schematic read = read(new NamedTag(
                "", NbtCompound.builder().put("Schematic", schematic).build()));

        assertThat(read.dataVersion()).isEqualTo(3953);
        assertThat(read.offset()).isEqualTo(new Vec3i(-1, -2, -3));
        for (int y = 0; y < H; y++) {
            for (int z = 0; z < L; z++) {
                for (int x = 0; x < W; x++) {
                    int index = x + z * W + y * W * L;
                    assertThat(read.blockAt(x, y, z))
                            .describedAs("(%d, %d, %d), an index past 127 packed in two bytes", x, y, z)
                            .isEqualTo(palette.get(150 + index));
                }
            }
        }
        assertThat(read.blockEntities())
                .singleElement()
                .isEqualTo(new SchematicBlockEntity(
                        new Vec3i(1, 2, 0),
                        "minecraft:chest",
                        NbtCompound.builder()
                                .putString("CustomName", "\"Loot\"")
                                .build()));
        SchematicBiomes biomes = read.biomes().orElseThrow();
        assertThat(biomes.palette().get(biomes.indexAt(read.indexOf(1, 0, 0), W, L)))
                .isEqualTo("minecraft:desert");
        assertThat(read.entities()).singleElement().satisfies(entity -> {
            assertThat(entity.id()).isEqualTo("minecraft:armor_stand");
            assertThat(entity.z()).isEqualTo(1.5);
            assertThat(entity.data().has("Invisible")).isTrue();
        });
    }

    @Test
    @DisplayName("A version 2 file from WorldEdit: a flat root, the copy point in its metadata, biomes per column")
    void versionTwo() throws IOException {
        List<String> palette = palette(3);
        NbtCompound root = NbtCompound.builder()
                .putInt("Version", 2)
                .putInt("DataVersion", 3463)
                .putShort("Width", W)
                .putShort("Height", H)
                .putShort("Length", L)
                .putIntArray("Offset", 1200, 64, -300)
                .put(
                        "Metadata",
                        NbtCompound.builder()
                                .putInt("WEOffsetX", -1)
                                .putInt("WEOffsetY", 0)
                                .putInt("WEOffsetZ", -1)
                                .build())
                .putInt("PaletteMax", palette.size())
                .put("Palette", paletteTag(palette))
                .put("BlockData", new NbtTag.ByteArrayTag(packed(i -> i % 3)))
                .put(
                        "BlockEntities",
                        NbtList.of(List.of(
                                NbtCompound.builder()
                                        .putIntArray("Pos", 0, 0, 0)
                                        .putString("Id", "minecraft:sign")
                                        .putString("Text1", "inline, as WorldEdit 7.2 wrote it")
                                        .build(),
                                NbtCompound.builder()
                                        .putIntArray("Pos", 1, 0, 1)
                                        .putString("Id", "minecraft:barrel")
                                        .put(
                                                "Data",
                                                NbtCompound.builder()
                                                        .putString("Lock", "under Data, as 7.3 writes it")
                                                        .build())
                                        .build())))
                .put("Entities", NbtList.of(List.of(entity("minecraft:item_frame", 1.0, 2.0, 0.0, false))))
                .put("BiomePalette", paletteTag(List.of("minecraft:plains", "minecraft:swamp")))
                .put("BiomeData", new NbtTag.ByteArrayTag(columns(i -> i == 3 ? 1 : 0)))
                .build();

        Schematic read = read(new NamedTag("Schematic", root));

        assertThat(read.offset())
                .describedAs("WorldEdit's copy point, not the corner it also wrote")
                .isEqualTo(new Vec3i(-1, 0, -1));
        assertThat(read.blockAt(1, 2, 1)).isEqualTo(palette.get((1 + 2 + 8) % 3));
        assertThat(read.blockEntities())
                .extracting(SchematicBlockEntity::data)
                .containsExactly(
                        NbtCompound.builder()
                                .putString("Text1", "inline, as WorldEdit 7.2 wrote it")
                                .build(),
                        NbtCompound.builder()
                                .putString("Lock", "under Data, as 7.3 writes it")
                                .build());
        SchematicBiomes biomes = read.biomes().orElseThrow();
        for (int y = 0; y < H; y++) {
            assertThat(biomes.palette().get(biomes.indexAt(read.indexOf(1, y, 1), W, L)))
                    .describedAs("a column's biome at every height")
                    .isEqualTo("minecraft:swamp");
            assertThat(biomes.palette().get(biomes.indexAt(read.indexOf(0, y, 1), W, L)))
                    .isEqualTo("minecraft:plains");
        }
        assertThat(read.entities())
                .singleElement()
                .satisfies(entity -> assertThat(entity.data().has("Facing")).isTrue());
    }

    @Test
    @DisplayName("What the writer writes reads back as the same schematic")
    void aWrittenSchematicReadsBack() throws IOException {
        Schematic.Builder builder = Schematic.builder(3, 2, 4)
                .offset(new Vec3i(-1, 0, -2))
                .dataVersion(4440)
                .metadata(NbtCompound.builder().putString("Name", "starter").build());
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 4; z++) {
                builder.block(x, 0, z, "minecraft:grass_block[snowy=false]");
            }
        }
        for (int i = 0; i < 4; i++) {
            builder.block(2, 1, i, "minecraft:stone_" + i);
        }
        // Past 127 states an index takes two bytes, which only a large palette reaches.
        for (int i = 0; i < 140; i++) {
            builder.block(0, 1, 0, "minecraft:filler_" + i);
        }
        builder.block(1, 1, 1, "minecraft:chest[facing=north,type=single,waterlogged=false]")
                .blockEntity(new SchematicBlockEntity(
                        new Vec3i(1, 1, 1),
                        "minecraft:chest",
                        NbtCompound.builder().putString("Lock", "").build()))
                .entity(new SchematicEntity(
                        1.5,
                        1.0,
                        0.5,
                        "minecraft:armor_stand",
                        NbtCompound.builder().putByte("Small", 1).build()));
        // Given per column, as a version 2 file gives them: the column at (1, 1) is forest at every height.
        com.uxplima.uxmlib.schematic.PaletteIndices columns = com.uxplima.uxmlib.schematic.PaletteIndices.of(3 * 4);
        columns.set(1 + 3, 1);
        builder.biomes(SchematicBiomes.perColumn(List.of("minecraft:plains", "minecraft:forest"), columns));
        Schematic written = builder.build();

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        SpongeSchematicWriter.write(written, file);
        Schematic read = SpongeSchematicReader.withDefaults().read(new ByteArrayInputStream(file.toByteArray()));

        assertThat(read.width()).isEqualTo(3);
        assertThat(read.height()).isEqualTo(2);
        assertThat(read.length()).isEqualTo(4);
        assertThat(read.offset()).isEqualTo(written.offset());
        assertThat(read.dataVersion()).isEqualTo(4440);
        assertThat(read.metadata()).isEqualTo(written.metadata());
        for (int i = 0; i < written.volume(); i++) {
            assertThat(read.palette().get(read.paletteIndexAt(i)))
                    .isEqualTo(written.palette().get(written.paletteIndexAt(i)));
        }
        assertThat(written.palette()).hasSizeGreaterThan(128);
        assertThat(read.blockAt(1, 1, 1)).isEqualTo("minecraft:chest[facing=north,type=single,waterlogged=false]");
        assertThat(read.blockEntities()).isEqualTo(written.blockEntities());
        assertThat(read.entities()).isEqualTo(written.entities());
        SchematicBiomes biomes = read.biomes().orElseThrow();
        assertThat(biomes.perColumn())
                .describedAs("version 3 keeps a biome per block")
                .isFalse();
        for (int y = 0; y < 2; y++) {
            assertThat(biomes.palette().get(biomes.indexAt(read.indexOf(1, y, 1), 3, 4)))
                    .isEqualTo("minecraft:forest");
            assertThat(biomes.palette().get(biomes.indexAt(read.indexOf(2, y, 1), 3, 4)))
                    .isEqualTo("minecraft:plains");
        }
    }

    @Test
    @DisplayName("A file that does not hold together is refused with the reason")
    void brokenFilesAreRefused() {
        assertRefused("version 9", tag -> tag.putInt("Version", 9));
        assertRefused("before Minecraft 1.13", tag -> tag.put("Materials", new NbtTag.StringTag("Alpha")));
        assertRefused("too few", tag -> tag.put("Data", new NbtTag.ByteArrayTag(new byte[W * H * L - 1])));
        assertRefused("ends after", tag -> tag.put("Data", new NbtTag.ByteArrayTag(endsMidValue())));
        assertRefused("bytes past", tag -> tag.put("Data", new NbtTag.ByteArrayTag(packedExtra())));
        assertRefused("palette has", tag -> tag.put("Data", new NbtTag.ByteArrayTag(packed(i -> 7))));
        assertRefused(
                "two states",
                tag -> tag.put(
                        "Palette",
                        NbtCompound.builder()
                                .putInt("minecraft:air", 0)
                                .putInt("minecraft:stone", 0)
                                .build()));
        assertRefused(
                "outside the schematic",
                tag -> tag.put(
                        "BlockEntities",
                        NbtList.of(List.of(NbtCompound.builder()
                                .putIntArray("Pos", 0, 9, 0)
                                .putString("Id", "minecraft:chest")
                                .build()))));
        assertRefused("DataVersion", tag -> tag.remove("DataVersion"));
    }

    @Test
    @DisplayName("A schematic larger than the reader takes is refused before its blocks are read")
    void tooLargeIsRefused() throws IOException {
        byte[] file = NbtWriter.toBytes(new NamedTag("Schematic", flatTwo(UnaryOperator.identity())), true);
        SpongeSchematicReader small = new SpongeSchematicReader(new SchematicLimits(NbtLimits.DEFAULT, 11));

        assertThatThrownBy(() -> small.read(new ByteArrayInputStream(file)))
                .isInstanceOf(SchematicFormatException.class)
                .hasMessageContaining("more than the 11");
    }

    private static Schematic read(NamedTag root) throws IOException {
        return SpongeSchematicReader.withDefaults().read(new ByteArrayInputStream(NbtWriter.toBytes(root, true)));
    }

    private static void assertRefused(String why, UnaryOperator<NbtCompound.Builder> change) {
        assertThatThrownBy(() -> read(new NamedTag("Schematic", flatTwo(change))))
                .isInstanceOf(SchematicFormatException.class)
                .hasMessageContaining(why);
    }

    /** A valid version 2 file with {@code change} made to it; the block data key is {@code Data} here. */
    private static NbtCompound flatTwo(UnaryOperator<NbtCompound.Builder> change) {
        NbtCompound.Builder tag = NbtCompound.builder()
                .putInt("Version", 2)
                .putInt("DataVersion", 3463)
                .putShort("Width", W)
                .putShort("Height", H)
                .putShort("Length", L)
                .put("Palette", paletteTag(palette(2)))
                .put("Data", new NbtTag.ByteArrayTag(packed(i -> i % 2)));
        NbtCompound changed = change.apply(tag).build();
        NbtCompound.Builder renamed = changed.toBuilder().remove("Data");
        changed.byteArray("Data").ifPresent(data -> renamed.put("BlockData", new NbtTag.ByteArrayTag(data)));
        return renamed.build();
    }

    private static List<String> palette(int size) {
        List<String> states = new ArrayList<>();
        states.add("minecraft:air");
        for (int i = 1; i < size; i++) {
            states.add("minecraft:stone_" + i);
        }
        return states;
    }

    private static NbtCompound paletteTag(List<String> palette) {
        NbtCompound.Builder tag = NbtCompound.builder();
        for (int i = palette.size() - 1; i >= 0; i--) {
            tag.putInt(palette.get(i), i);
        }
        return tag.build();
    }

    /** The format's varints, written out by hand: seven bits a byte, low bits first, high bit to go on. */
    private static byte[] packed(java.util.function.IntUnaryOperator valueAt) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < W * H * L; i++) {
            int value = valueAt.applyAsInt(i);
            if (value < 128) {
                out.write(value);
            } else {
                out.write((value & 0x7F) | 0x80);
                out.write(value >>> 7);
            }
        }
        return out.toByteArray();
    }

    private static byte[] columns(java.util.function.IntUnaryOperator valueAt) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < W * L; i++) {
            out.write(valueAt.applyAsInt(i));
        }
        return out.toByteArray();
    }

    /** As many bytes as values, but the last one says another byte follows. */
    private static byte[] endsMidValue() {
        byte[] bytes = packed(i -> 0);
        bytes[bytes.length - 1] = (byte) 0x81;
        return bytes;
    }

    private static byte[] packedExtra() {
        byte[] exact = packed(i -> 0);
        byte[] longer = java.util.Arrays.copyOf(exact, exact.length + 1);
        return longer;
    }

    private static NbtCompound entity(String id, double x, double y, double z, boolean grouped) {
        NbtList pos = NbtList.of(List.of(new NbtTag.DoubleTag(x), new NbtTag.DoubleTag(y), new NbtTag.DoubleTag(z)));
        NbtCompound.Builder entry = NbtCompound.builder().put("Pos", pos).putString("Id", id);
        if (grouped) {
            entry.put(
                    "Data",
                    NbtCompound.builder()
                            .put("Pos", pos)
                            .putString("id", id)
                            .putByte("Invisible", 1)
                            .build());
        } else {
            entry.putByte("Facing", 2);
        }
        return entry.build();
    }
}
