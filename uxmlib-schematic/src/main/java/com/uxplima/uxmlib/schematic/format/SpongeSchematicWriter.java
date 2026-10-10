package com.uxplima.uxmlib.schematic.format;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmlib.schematic.Schematic;
import com.uxplima.uxmlib.schematic.SchematicBiomes;
import com.uxplima.uxmlib.schematic.SchematicBlockEntity;
import com.uxplima.uxmlib.schematic.SchematicEntity;
import com.uxplima.uxmlib.schematic.nbt.NamedTag;
import com.uxplima.uxmlib.schematic.nbt.NbtCompound;
import com.uxplima.uxmlib.schematic.nbt.NbtList;
import com.uxplima.uxmlib.schematic.nbt.NbtTag;
import com.uxplima.uxmlib.schematic.nbt.NbtWriter;

/** Writes a schematic as a Sponge version 3 file, the form WorldEdit 7.3 and FastAsyncWorldEdit read. */
public final class SpongeSchematicWriter {

    private SpongeSchematicWriter() {}

    /** Writes {@code schematic} gzip compressed, and leaves {@code out} open. */
    public static void write(Schematic schematic, OutputStream out) throws IOException {
        NbtWriter.write(toTag(schematic), out, true);
    }

    /** The tag tree of the file. */
    public static NamedTag toTag(Schematic schematic) {
        Objects.requireNonNull(schematic, "schematic must not be null");
        NbtCompound.Builder tag =
                NbtCompound.builder().putInt("Version", 3).putInt("DataVersion", schematic.dataVersion());
        if (!schematic.metadata().entries().isEmpty()) {
            tag.put("Metadata", schematic.metadata());
        }
        tag.putShort("Width", schematic.width())
                .putShort("Height", schematic.height())
                .putShort("Length", schematic.length())
                .putIntArray(
                        "Offset",
                        schematic.offset().x(),
                        schematic.offset().y(),
                        schematic.offset().z());

        NbtCompound.Builder palette = NbtCompound.builder();
        for (int i = 0; i < schematic.palette().size(); i++) {
            palette.putInt(schematic.palette().get(i), i);
        }
        ByteArrayOutputStream data = new ByteArrayOutputStream((int) Math.min(schematic.volume(), 1 << 24));
        for (int i = 0; i < schematic.volume(); i++) {
            VarInts.encode(schematic.paletteIndexAt(i), data);
        }
        List<NbtCompound> blockEntities = new ArrayList<>();
        for (SchematicBlockEntity entity : schematic.blockEntities()) {
            NbtCompound.Builder entry = NbtCompound.builder()
                    .putIntArray(
                            "Pos",
                            entity.pos().x(),
                            entity.pos().y(),
                            entity.pos().z())
                    .putString("Id", entity.id());
            if (!entity.data().entries().isEmpty()) {
                entry.put("Data", entity.data());
            }
            blockEntities.add(entry.build());
        }
        NbtCompound.Builder blocks = NbtCompound.builder()
                .put("Palette", palette.build())
                .put("Data", new NbtTag.ByteArrayTag(data.toByteArray()));
        if (!blockEntities.isEmpty()) {
            blocks.put("BlockEntities", NbtList.of(blockEntities));
        }
        tag.put("Blocks", blocks.build());

        schematic.biomes().ifPresent(biomes -> tag.put("Biomes", biomes(biomes, schematic)));

        if (!schematic.entities().isEmpty()) {
            List<NbtCompound> entities = new ArrayList<>();
            for (SchematicEntity entity : schematic.entities()) {
                NbtCompound.Builder entry = NbtCompound.builder()
                        .put(
                                "Pos",
                                NbtList.of(List.of(
                                        new NbtTag.DoubleTag(entity.x()),
                                        new NbtTag.DoubleTag(entity.y()),
                                        new NbtTag.DoubleTag(entity.z()))))
                        .putString("Id", entity.id());
                if (!entity.data().entries().isEmpty()) {
                    entry.put("Data", entity.data());
                }
                entities.add(entry.build());
            }
            tag.put("Entities", NbtList.of(entities));
        }
        return new NamedTag(
                "", NbtCompound.builder().put("Schematic", tag.build()).build());
    }

    /** Version 3 keeps a biome per block, so a biome given per column is written at every height. */
    private static NbtCompound biomes(SchematicBiomes biomes, Schematic schematic) {
        NbtCompound.Builder palette = NbtCompound.builder();
        for (int i = 0; i < biomes.palette().size(); i++) {
            palette.putInt(biomes.palette().get(i), i);
        }
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (int i = 0; i < schematic.volume(); i++) {
            VarInts.encode(biomes.indexAt(i, schematic.width(), schematic.length()), data);
        }
        return NbtCompound.builder()
                .put("Palette", palette.build())
                .put("Data", new NbtTag.ByteArrayTag(data.toByteArray()))
                .build();
    }
}
