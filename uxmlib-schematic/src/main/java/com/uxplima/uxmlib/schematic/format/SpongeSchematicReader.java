package com.uxplima.uxmlib.schematic.format;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmlib.schematic.PaletteIndices;
import com.uxplima.uxmlib.schematic.Schematic;
import com.uxplima.uxmlib.schematic.SchematicBiomes;
import com.uxplima.uxmlib.schematic.SchematicBlockEntity;
import com.uxplima.uxmlib.schematic.SchematicEntity;
import com.uxplima.uxmlib.schematic.Vec3i;
import com.uxplima.uxmlib.schematic.nbt.NamedTag;
import com.uxplima.uxmlib.schematic.nbt.NbtCompound;
import com.uxplima.uxmlib.schematic.nbt.NbtFormatException;
import com.uxplima.uxmlib.schematic.nbt.NbtList;
import com.uxplima.uxmlib.schematic.nbt.NbtReader;
import com.uxplima.uxmlib.schematic.nbt.NbtTag;
import org.jspecify.annotations.Nullable;

/**
 * Reads a Sponge schematic, versions 1, 2 and 3.
 *
 * <p>Version 3 keeps the schematic under a {@code Schematic} compound in an unnamed root, and groups the
 * blocks, the biomes and their block entities; versions 1 and 2 are the root itself, flat. WorldEdit writes
 * the point a version 2 file was copied around into its metadata as {@code WEOffsetX}, {@code Y} and
 * {@code Z} and the box's absolute corner into {@code Offset}, so those are read first. A file from before
 * the flattening of 1.13 is not a Sponge file, and is refused by name.
 */
public final class SpongeSchematicReader {

    /** The game version a version 1 file was written for, which did not record one: 1.13.2. */
    static final int VERSION_ONE_DATA = 1631;

    private final SchematicLimits limits;

    public SpongeSchematicReader(SchematicLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits must not be null");
    }

    public static SpongeSchematicReader withDefaults() {
        return new SpongeSchematicReader(SchematicLimits.DEFAULT);
    }

    public Schematic read(InputStream in) throws IOException {
        NamedTag root;
        try {
            root = new NbtReader(limits.nbt()).read(in);
        } catch (NbtFormatException e) {
            throw new SchematicFormatException("The file is not a readable schematic: " + e.getMessage(), e);
        }
        NbtCompound schematic = root.tag().compound("Schematic").orElse(root.tag());
        if (schematic.has("Materials") || schematic.get("Blocks") instanceof NbtTag.ByteArrayTag) {
            throw new SchematicFormatException("This is an MCEdit .schematic from before Minecraft 1.13, which"
                    + " names blocks by number. Save it again as .schem with WorldEdit on a current server.");
        }
        int version = schematic
                .intValue("Version")
                .orElseThrow(() -> new SchematicFormatException("The file names no schematic version"));
        return switch (version) {
            case 1, 2 -> flat(schematic, version);
            case 3 -> grouped(schematic);
            default ->
                throw new SchematicFormatException(
                        "The file is a version " + version + " schematic, and versions 1, 2 and 3 are read");
        };
    }

    private Schematic flat(NbtCompound tag, int version) throws SchematicFormatException {
        Size size = size(tag);
        int dataVersion = version == 1 ? VERSION_ONE_DATA : dataVersion(tag);
        NbtCompound metadata = tag.compound("Metadata").orElse(NbtCompound.empty());
        Vec3i offset = weOffset(metadata).orElseGet(() -> vec(tag.intArray("Offset")));
        List<String> palette = palette(tag.compound("Palette").orElse(null), "block palette");
        PaletteIndices blocks = indices(tag, "BlockData", size.volume(), palette.size(), "block data");
        List<SchematicBlockEntity> blockEntities = blockEntities(
                tag.list(version == 1 ? "TileEntities" : "BlockEntities").orElse(null), size, false);
        List<SchematicEntity> entities = entities(tag.list("Entities").orElse(null), false);
        SchematicBiomes biomes = null;
        Optional<NbtCompound> biomePalette = tag.compound("BiomePalette");
        if (biomePalette.isPresent() && tag.byteArray("BiomeData").isPresent()) {
            List<String> names = palette(biomePalette.get(), "biome palette");
            biomes = SchematicBiomes.perColumn(
                    names, indices(tag, "BiomeData", size.width * size.length, names.size(), "biome data"));
        }
        return build(size, offset, dataVersion, palette, blocks, blockEntities, entities, biomes, metadata);
    }

    private Schematic grouped(NbtCompound tag) throws SchematicFormatException {
        Size size = size(tag);
        int dataVersion = dataVersion(tag);
        NbtCompound metadata = tag.compound("Metadata").orElse(NbtCompound.empty());
        Vec3i offset = vec(tag.intArray("Offset"));
        List<String> palette = List.of(Schematic.AIR);
        PaletteIndices blocks;
        List<SchematicBlockEntity> blockEntities = List.of();
        Optional<NbtCompound> blockGroup = tag.compound("Blocks");
        if (blockGroup.isPresent()) {
            palette = palette(blockGroup.get().compound("Palette").orElse(null), "block palette");
            blocks = indices(blockGroup.get(), "Data", size.volume(), palette.size(), "block data");
            blockEntities = blockEntities(blockGroup.get().list("BlockEntities").orElse(null), size, true);
        } else {
            // The format lets a file leave its blocks out, and then the box is air.
            blocks = PaletteIndices.of(size.volume());
        }
        SchematicBiomes biomes = null;
        Optional<NbtCompound> biomeGroup = tag.compound("Biomes");
        if (biomeGroup.isPresent()) {
            List<String> names = palette(biomeGroup.get().compound("Palette").orElse(null), "biome palette");
            biomes = SchematicBiomes.perBlock(
                    names, indices(biomeGroup.get(), "Data", size.volume(), names.size(), "biome data"));
        }
        List<SchematicEntity> entities = entities(tag.list("Entities").orElse(null), true);
        return build(size, offset, dataVersion, palette, blocks, blockEntities, entities, biomes, metadata);
    }

    private static Schematic build(
            Size size,
            Vec3i offset,
            int dataVersion,
            List<String> palette,
            PaletteIndices blocks,
            List<SchematicBlockEntity> blockEntities,
            List<SchematicEntity> entities,
            @Nullable SchematicBiomes biomes,
            NbtCompound metadata)
            throws SchematicFormatException {
        try {
            return Schematic.of(
                    size.width,
                    size.height,
                    size.length,
                    offset,
                    dataVersion,
                    palette,
                    blocks,
                    blockEntities,
                    entities,
                    biomes,
                    metadata);
        } catch (IllegalArgumentException e) {
            throw new SchematicFormatException("The schematic does not hold together: " + e.getMessage(), e);
        }
    }

    private record Size(int width, int height, int length) {
        int volume() {
            return width * height * length;
        }
    }

    private Size size(NbtCompound tag) throws SchematicFormatException {
        int width = dimension(tag, "Width");
        int height = dimension(tag, "Height");
        int length = dimension(tag, "Length");
        long volume = (long) width * height * length;
        if (volume > limits.maxVolume()) {
            throw new SchematicFormatException("The schematic holds " + volume + " blocks, more than the "
                    + limits.maxVolume() + " this reader takes");
        }
        return new Size(width, height, length);
    }

    private static int dimension(NbtCompound tag, String name) throws SchematicFormatException {
        int value = tag.intValue(name)
                .orElseThrow(() ->
                        new SchematicFormatException("The file names no " + name.toLowerCase(java.util.Locale.ROOT)));
        int unsigned = value & 0xFFFF;
        if (unsigned == 0) {
            throw new SchematicFormatException(
                    "A schematic is at least one block in " + name.toLowerCase(java.util.Locale.ROOT));
        }
        return unsigned;
    }

    private static int dataVersion(NbtCompound tag) throws SchematicFormatException {
        return tag.intValue("DataVersion")
                .orElseThrow(() -> new SchematicFormatException("The file names no game version, DataVersion"));
    }

    /** The palette as a list by index. Two states under one index are refused; an unused index is air. */
    private static List<String> palette(@Nullable NbtCompound tag, String what) throws SchematicFormatException {
        if (tag == null || tag.entries().isEmpty()) {
            throw new SchematicFormatException("The " + what + " is missing or empty");
        }
        String[] byIndex = new String[tag.entries().size()];
        int highest = -1;
        for (Map.Entry<String, NbtTag> entry : tag.entries().entrySet()) {
            int index = NbtTag.asInt(entry.getValue())
                    .orElseThrow(() ->
                            new SchematicFormatException("The " + what + " gives " + entry.getKey() + " no number"));
            // A palette index far past the palette's size would make the list it is read into as large as
            // the number, so one is refused long before that.
            if (index < 0 || index >= tag.entries().size() * 4 + 1024) {
                throw new SchematicFormatException("The " + what + " gives " + entry.getKey() + " the index " + index);
            }
            if (index >= byIndex.length) {
                byIndex = Arrays.copyOf(byIndex, index + 1);
            }
            if (byIndex[index] != null) {
                throw new SchematicFormatException("The " + what + " gives the index " + index + " to two states, "
                        + byIndex[index] + " and " + entry.getKey());
            }
            byIndex[index] = entry.getKey();
            highest = Math.max(highest, index);
        }
        List<String> palette = new ArrayList<>(highest + 1);
        for (int i = 0; i <= highest; i++) {
            palette.add(byIndex[i] == null ? Schematic.AIR : byIndex[i]);
        }
        return palette;
    }

    private static PaletteIndices indices(NbtCompound tag, String key, int count, int paletteSize, String what)
            throws SchematicFormatException {
        byte[] packed =
                tag.byteArray(key).orElseThrow(() -> new SchematicFormatException("The " + what + " is missing"));
        PaletteIndices values = VarInts.decode(packed, count, what);
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) >= paletteSize) {
                throw new SchematicFormatException(
                        "The " + what + " names index " + values.get(i) + " and the palette has " + paletteSize);
            }
        }
        return values;
    }

    private static List<SchematicBlockEntity> blockEntities(@Nullable NbtList list, Size size, boolean grouped)
            throws SchematicFormatException {
        if (list == null) {
            return List.of();
        }
        List<SchematicBlockEntity> found = new ArrayList<>(list.size());
        for (NbtCompound entry : list.compounds()) {
            int[] pos = entry.intArray("Pos")
                    .filter(array -> array.length == 3)
                    .orElseThrow(() -> new SchematicFormatException("A block entity has no position"));
            Vec3i at = new Vec3i(pos[0], pos[1], pos[2]);
            if (at.x() < 0
                    || at.y() < 0
                    || at.z() < 0
                    || at.x() >= size.width
                    || at.y() >= size.height
                    || at.z() >= size.length) {
                throw new SchematicFormatException("A block entity at " + at + " is outside the schematic");
            }
            String id = entry.string("Id")
                    .or(() -> entry.string("id"))
                    .orElseThrow(() -> new SchematicFormatException("The block entity at " + at + " names no kind"));
            found.add(new SchematicBlockEntity(at, id, fields(entry, grouped, "x", "y", "z", "id")));
        }
        return found;
    }

    private static List<SchematicEntity> entities(@Nullable NbtList list, boolean grouped)
            throws SchematicFormatException {
        if (list == null) {
            return List.of();
        }
        List<SchematicEntity> found = new ArrayList<>(list.size());
        for (NbtCompound entry : list.compounds()) {
            NbtList pos = entry.list("Pos")
                    .filter(values -> values.size() == 3)
                    .orElseThrow(() -> new SchematicFormatException("An entity has no position"));
            double[] at = new double[3];
            for (int i = 0; i < 3; i++) {
                at[i] = NbtTag.asDouble(pos.values().get(i))
                        .orElseThrow(() -> new SchematicFormatException("An entity's position is not a number"));
            }
            String id = entry.string("Id")
                    .or(() -> entry.string("id"))
                    .orElseThrow(() -> new SchematicFormatException("An entity names no kind"));
            found.add(new SchematicEntity(at[0], at[1], at[2], id, fields(entry, grouped, "Pos", "id", "UUID")));
        }
        return found;
    }

    /**
     * The fields of a block entity or an entity, without where it stands and what it is, which the schematic
     * keeps beside them and a paste writes anew. Version 3 keeps the fields in {@code Data}. Version 2 kept
     * them beside {@code Pos} and {@code Id}, and the WorldEdit of 1.20 already put them in {@code Data} in
     * a version 2 file, so both are read.
     */
    private static NbtCompound fields(NbtCompound entry, boolean grouped, String... positional) {
        NbtCompound.Builder fields = grouped
                ? NbtCompound.builder()
                : entry.toBuilder().remove("Pos").remove("Id").remove("id").remove("Data");
        entry.compound("Data").ifPresent(data -> data.entries().forEach(fields::put));
        for (String key : positional) {
            fields.remove(key);
        }
        return fields.build();
    }

    private static Optional<Vec3i> weOffset(NbtCompound metadata) {
        if (metadata.has("WEOffsetX") && metadata.has("WEOffsetY") && metadata.has("WEOffsetZ")) {
            return Optional.of(new Vec3i(
                    metadata.intValue("WEOffsetX").orElse(0),
                    metadata.intValue("WEOffsetY").orElse(0),
                    metadata.intValue("WEOffsetZ").orElse(0)));
        }
        return Optional.empty();
    }

    private static Vec3i vec(Optional<int[]> array) {
        return array.filter(values -> values.length == 3)
                .map(values -> new Vec3i(values[0], values[1], values[2]))
                .orElse(Vec3i.ZERO);
    }
}
