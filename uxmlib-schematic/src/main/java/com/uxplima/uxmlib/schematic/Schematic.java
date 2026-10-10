package com.uxplima.uxmlib.schematic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmlib.schematic.nbt.NbtCompound;
import org.jspecify.annotations.Nullable;

/**
 * A box of block states, with the block entities and entities inside it.
 *
 * <p>Blocks are kept as palette indices in the order the Sponge format writes them, {@code x} fastest, then
 * {@code z}, then {@code y}. {@link #offset()} is where the box's corner sits from the point it was saved
 * around, so pasting at a place puts that point there, as WorldEdit does.
 */
public final class Schematic {

    /** The block every position holds until something else is put there. */
    public static final String AIR = "minecraft:air";

    private final int width;
    private final int height;
    private final int length;
    private final Vec3i offset;
    private final int dataVersion;
    private final List<String> palette;
    private final PaletteIndices blocks;
    private final List<SchematicBlockEntity> blockEntities;
    private final List<SchematicEntity> entities;
    private final @Nullable SchematicBiomes biomes;
    private final NbtCompound metadata;

    Schematic(
            int width,
            int height,
            int length,
            Vec3i offset,
            int dataVersion,
            List<String> palette,
            PaletteIndices blocks,
            List<SchematicBlockEntity> blockEntities,
            List<SchematicEntity> entities,
            @Nullable SchematicBiomes biomes,
            NbtCompound metadata) {
        if (width < 1 || height < 1 || length < 1 || width > 0xFFFF || height > 0xFFFF || length > 0xFFFF) {
            throw new IllegalArgumentException(
                    "a schematic is 1 to 65535 blocks each way, not " + width + "x" + height + "x" + length);
        }
        this.width = width;
        this.height = height;
        this.length = length;
        this.offset = Objects.requireNonNull(offset, "offset must not be null");
        this.dataVersion = dataVersion;
        this.palette = List.copyOf(palette);
        if (this.palette.isEmpty()) {
            throw new IllegalArgumentException("a palette names at least one block");
        }
        if (blocks.size() != (long) width * height * length) {
            throw new IllegalArgumentException("a " + width + "x" + height + "x" + length + " schematic holds "
                    + (long) width * height * length + " blocks, not " + blocks.size());
        }
        if (blocks.highest() >= this.palette.size()) {
            throw new IllegalArgumentException("block index " + blocks.highest() + " is outside the palette");
        }
        this.blocks = blocks.handOver();
        for (SchematicBlockEntity entity : blockEntities) {
            if (!contains(entity.pos())) {
                throw new IllegalArgumentException("a block entity at " + entity.pos() + " is outside the schematic");
            }
        }
        this.blockEntities = List.copyOf(blockEntities);
        this.entities = List.copyOf(entities);
        if (biomes != null && !biomes.fits(width, height, length)) {
            throw new IllegalArgumentException(
                    "the biomes are not of a " + width + "x" + height + "x" + length + " box");
        }
        this.biomes = biomes;
        this.metadata = Objects.requireNonNull(metadata, "metadata must not be null");
    }

    /** Starts a schematic of this size, every position air. */
    public static Builder builder(int width, int height, int length) {
        return new Builder(width, height, length);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int length() {
        return length;
    }

    public long volume() {
        return (long) width * height * length;
    }

    /** Where the box's corner sits from the point it was saved around. */
    public Vec3i offset() {
        return offset;
    }

    /** The version of the game whose names the file uses. */
    public int dataVersion() {
        return dataVersion;
    }

    /** Every block state the schematic names, each once. */
    public List<String> palette() {
        return palette;
    }

    public boolean contains(Vec3i pos) {
        return pos.x() >= 0 && pos.y() >= 0 && pos.z() >= 0 && pos.x() < width && pos.y() < height && pos.z() < length;
    }

    /** The index of the block at {@code x, y, z} in the order the format writes blocks. */
    public int indexOf(int x, int y, int z) {
        return x + z * width + y * width * length;
    }

    /** The palette index of the block at the block index {@code index}. */
    public int paletteIndexAt(int index) {
        return blocks.get(index);
    }

    /** The block state at {@code x, y, z}. */
    public String blockAt(int x, int y, int z) {
        if (!contains(new Vec3i(x, y, z))) {
            throw new IndexOutOfBoundsException("(" + x + ", " + y + ", " + z + ") is outside the schematic");
        }
        return palette.get(blocks.get(indexOf(x, y, z)));
    }

    public List<SchematicBlockEntity> blockEntities() {
        return blockEntities;
    }

    public List<SchematicEntity> entities() {
        return entities;
    }

    public Optional<SchematicBiomes> biomes() {
        return Optional.ofNullable(biomes);
    }

    /** What the file says about itself: its name, its author, when it was made. */
    public NbtCompound metadata() {
        return metadata;
    }

    /** Builds a schematic a block at a time, giving each new state the next palette index. */
    public static final class Builder {

        private final int width;
        private final int height;
        private final int length;
        private final PaletteIndices blocks;
        private final List<String> palette = new ArrayList<>(List.of(AIR));
        private final Map<String, Integer> indices = new HashMap<>(Map.of(AIR, 0));
        private final List<SchematicBlockEntity> blockEntities = new ArrayList<>();
        private final List<SchematicEntity> entities = new ArrayList<>();
        private Vec3i offset = Vec3i.ZERO;
        private int dataVersion;
        private @Nullable SchematicBiomes biomes;
        private NbtCompound metadata = NbtCompound.empty();

        private Builder(int width, int height, int length) {
            if ((long) width * height * length > Integer.MAX_VALUE - 8 || width < 1 || height < 1 || length < 1) {
                throw new IllegalArgumentException("cannot hold " + width + "x" + height + "x" + length + " blocks");
            }
            this.width = width;
            this.height = height;
            this.length = length;
            this.blocks = PaletteIndices.of(width * height * length);
        }

        public Builder block(int x, int y, int z, String state) {
            Objects.requireNonNull(state, "state must not be null");
            if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= length) {
                throw new IndexOutOfBoundsException("(" + x + ", " + y + ", " + z + ") is outside the schematic");
            }
            int index = indices.computeIfAbsent(state, added -> {
                palette.add(added);
                return palette.size() - 1;
            });
            blocks.set(x + z * width + y * width * length, index);
            return this;
        }

        public Builder blockEntity(SchematicBlockEntity entity) {
            blockEntities.add(Objects.requireNonNull(entity, "entity must not be null"));
            return this;
        }

        public Builder entity(SchematicEntity entity) {
            entities.add(Objects.requireNonNull(entity, "entity must not be null"));
            return this;
        }

        public Builder offset(Vec3i offset) {
            this.offset = Objects.requireNonNull(offset, "offset must not be null");
            return this;
        }

        public Builder dataVersion(int dataVersion) {
            this.dataVersion = dataVersion;
            return this;
        }

        public Builder biomes(@Nullable SchematicBiomes biomes) {
            this.biomes = biomes;
            return this;
        }

        public Builder metadata(NbtCompound metadata) {
            this.metadata = Objects.requireNonNull(metadata, "metadata must not be null");
            return this;
        }

        /** The schematic. A builder builds one, and is spent after. */
        public Schematic build() {
            return new Schematic(
                    width,
                    height,
                    length,
                    offset,
                    dataVersion,
                    palette,
                    blocks,
                    blockEntities,
                    entities,
                    biomes,
                    metadata);
        }
    }

    /**
     * For a reader that already holds the palette and the indices in the format's order. The indices are
     * handed over, not copied: a large file is held once.
     */
    public static Schematic of(
            int width,
            int height,
            int length,
            Vec3i offset,
            int dataVersion,
            List<String> palette,
            PaletteIndices blocks,
            List<SchematicBlockEntity> blockEntities,
            List<SchematicEntity> entities,
            @Nullable SchematicBiomes biomes,
            NbtCompound metadata) {
        return new Schematic(
                width, height, length, offset, dataVersion, palette, blocks, blockEntities, entities, biomes, metadata);
    }
}
