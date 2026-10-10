package com.uxplima.uxmlib.schematic.paper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.schematic.PaletteIndices;
import com.uxplima.uxmlib.schematic.Schematic;
import com.uxplima.uxmlib.schematic.SchematicBiomes;
import com.uxplima.uxmlib.schematic.SchematicBlockEntity;
import com.uxplima.uxmlib.schematic.SchematicEntity;
import com.uxplima.uxmlib.schematic.Vec3i;
import org.jspecify.annotations.Nullable;

/**
 * Saves a box of a world as a schematic, a chunk at a time.
 *
 * <p>Each chunk is read on the thread that owns it only for as long as it takes to copy it: its blocks as a
 * snapshot, its block entities and its entities as tags. The snapshot is read into the schematic off the
 * server's threads, so a large box costs the server a copy per chunk and no more.
 */
public final class SchematicCapture {

    private final Scheduler scheduler;
    private final ServerAccess server;

    public SchematicCapture(Scheduler scheduler) {
        this(scheduler, ServerAccess.SERVER);
    }

    SchematicCapture(Scheduler scheduler, ServerAccess server) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.server = Objects.requireNonNull(server, "server must not be null");
    }

    /**
     * Saves the box between two corners, both inside it, around {@code origin}: pasting the schematic at a
     * point puts {@code origin} there.
     */
    public CompletableFuture<Schematic> capture(
            World world, Vec3i corner, Vec3i otherCorner, Vec3i origin, CaptureOptions options) {
        Objects.requireNonNull(world, "world must not be null");
        Objects.requireNonNull(origin, "origin must not be null");
        Objects.requireNonNull(options, "options must not be null");
        Vec3i min = new Vec3i(
                Math.min(corner.x(), otherCorner.x()),
                Math.max(Math.min(corner.y(), otherCorner.y()), world.getMinHeight()),
                Math.min(corner.z(), otherCorner.z()));
        Vec3i max = new Vec3i(
                Math.max(corner.x(), otherCorner.x()),
                Math.min(Math.max(corner.y(), otherCorner.y()), world.getMaxHeight() - 1),
                Math.max(corner.z(), otherCorner.z()));
        if (max.y() < min.y()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("the box lies wholly above or below the world"));
        }
        long volume = (long) (max.x() - min.x() + 1) * (max.y() - min.y() + 1) * (max.z() - min.z() + 1);
        if (volume > options.maxVolume()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "the box holds " + volume + " blocks, more than the " + options.maxVolume() + " allowed"));
        }
        Capture capture = new Capture(world, min, max, origin, options);
        capture.start();
        return capture.result;
    }

    /** One capture in progress. */
    private final class Capture {

        final CompletableFuture<Schematic> result = new CompletableFuture<>();
        private final World world;
        private final Vec3i min;
        private final Vec3i max;
        private final Vec3i origin;
        private final CaptureOptions options;
        private final int width;
        private final int height;
        private final int length;
        private final PaletteIndices blocks;
        private final @Nullable PaletteIndices biomeIndices;
        private final List<String> palette = new ArrayList<>(List.of(Schematic.AIR));
        private final Map<String, Integer> paletteIndex = new HashMap<>(Map.of(Schematic.AIR, 0));
        private final List<String> biomePalette = new ArrayList<>();
        private final Map<String, Integer> biomeIndex = new HashMap<>();
        private final ConcurrentLinkedQueue<SchematicBlockEntity> blockEntities = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<SchematicEntity> entities = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<Long> chunks = new ConcurrentLinkedQueue<>();
        private final AtomicInteger chunksLeft = new AtomicInteger();

        Capture(World world, Vec3i min, Vec3i max, Vec3i origin, CaptureOptions options) {
            this.world = world;
            this.min = min;
            this.max = max;
            this.origin = origin;
            this.options = options;
            this.width = max.x() - min.x() + 1;
            this.height = max.y() - min.y() + 1;
            this.length = max.z() - min.z() + 1;
            this.blocks = PaletteIndices.of(width * height * length);
            this.biomeIndices = options.biomes() ? PaletteIndices.of(width * height * length) : null;
        }

        void start() {
            for (int cx = min.x() >> 4; cx <= max.x() >> 4; cx++) {
                for (int cz = min.z() >> 4; cz <= max.z() >> 4; cz++) {
                    chunks.add(((long) cx << 32) | (cz & 0xFFFFFFFFL));
                }
            }
            chunksLeft.set(chunks.size());
            for (int i = 0; i < options.concurrency(); i++) {
                next();
            }
        }

        private void next() {
            Long chunk = chunks.poll();
            if (chunk == null || result.isDone()) {
                return;
            }
            int cx = (int) (chunk >> 32);
            int cz = (int) (long) chunk;
            Location anchor = new Location(world, cx << 4, 0, cz << 4);
            var unused = server.load(world, cx, cz).whenComplete((loaded, failure) -> {
                if (failure != null) {
                    result.completeExceptionally(failure);
                    return;
                }
                try {
                    scheduler.region(anchor, () -> copy(cx, cz));
                } catch (RuntimeException refused) {
                    result.completeExceptionally(refused);
                }
            });
        }

        /** On the chunk's own thread: what a snapshot does not hold, then the snapshot itself. */
        private void copy(int cx, int cz) {
            if (result.isDone()) {
                return;
            }
            try {
                var chunk = world.getChunkAt(cx, cz);
                for (BlockState state : server.blockEntities(chunk)) {
                    if (inside(state.getX(), state.getY(), state.getZ())) {
                        BlockEntities.capture(state.getBlock())
                                .ifPresent(captured -> blockEntities.add(new SchematicBlockEntity(
                                        new Vec3i(state.getX(), state.getY(), state.getZ()).minus(min),
                                        captured.id(),
                                        captured.data())));
                    }
                }
                if (options.entities()) {
                    for (Entity entity : chunk.getEntities()) {
                        copyEntity(entity);
                    }
                }
                ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, options.biomes(), false);
                scheduler.async(() -> read(snapshot, cx, cz));
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        }

        private void copyEntity(Entity entity) {
            Location at = entity.getLocation();
            if (entity instanceof Player
                    || !inside(at.getBlockX(), at.getBlockY(), at.getBlockZ())
                    || entity.getType() == EntityType.UNKNOWN) {
                return;
            }
            entities.add(new SchematicEntity(
                    at.getX() - min.x(),
                    at.getY() - min.y(),
                    at.getZ() - min.z(),
                    entity.getType().getKey().asString(),
                    server.save(entity)));
        }

        /** Off the server's threads: the snapshot's blocks into the schematic. */
        private void read(ChunkSnapshot snapshot, int cx, int cz) {
            if (result.isDone()) {
                return;
            }
            try {
                int x0 = Math.max(min.x(), cx << 4);
                int x1 = Math.min(max.x(), (cx << 4) + 15);
                int z0 = Math.max(min.z(), cz << 4);
                int z1 = Math.min(max.z(), (cz << 4) + 15);
                Map<BlockData, Integer> seen = new HashMap<>();
                Map<Biome, Integer> seenBiomes = new HashMap<>();
                for (int y = min.y(); y <= max.y(); y++) {
                    for (int z = z0; z <= z1; z++) {
                        for (int x = x0; x <= x1; x++) {
                            int index = (x - min.x()) + (z - min.z()) * width + (y - min.y()) * width * length;
                            BlockData data = snapshot.getBlockData(x & 15, y, z & 15);
                            Integer known = seen.get(data);
                            if (known == null) {
                                known = paletteIndex(data.getAsString());
                                seen.put(data, known);
                            }
                            blocks.set(index, known);
                            if (biomeIndices != null) {
                                Biome biome = snapshot.getBiome(x & 15, y, z & 15);
                                Integer biomeKnown = seenBiomes.get(biome);
                                if (biomeKnown == null) {
                                    biomeKnown = biomeIndex(biome);
                                    seenBiomes.put(biome, biomeKnown);
                                }
                                biomeIndices.set(index, biomeKnown);
                            }
                        }
                    }
                }
                if (chunksLeft.decrementAndGet() == 0) {
                    finish();
                } else {
                    next();
                }
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        }

        private int paletteIndex(String state) {
            synchronized (paletteIndex) {
                return paletteIndex.computeIfAbsent(state, added -> {
                    palette.add(added);
                    return palette.size() - 1;
                });
            }
        }

        private int biomeIndex(Biome biome) {
            NamespacedKey key = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.BIOME)
                    .getKey(biome);
            String name = key == null ? "minecraft:plains" : key.asString();
            synchronized (biomeIndex) {
                return biomeIndex.computeIfAbsent(name, added -> {
                    biomePalette.add(added);
                    return biomePalette.size() - 1;
                });
            }
        }

        private void finish() {
            try {
                List<SchematicBlockEntity> sortedBlockEntities = new ArrayList<>(blockEntities);
                sortedBlockEntities.sort(
                        Comparator.comparingInt(entity -> entity.pos().x()
                                + entity.pos().z() * width
                                + entity.pos().y() * width * length));
                List<SchematicEntity> sortedEntities = new ArrayList<>(entities);
                sortedEntities.sort(Comparator.comparingDouble(SchematicEntity::y)
                        .thenComparingDouble(SchematicEntity::z)
                        .thenComparingDouble(SchematicEntity::x));
                SchematicBiomes biomes;
                synchronized (biomeIndex) {
                    biomes = biomeIndices == null ? null : SchematicBiomes.perBlock(biomePalette, biomeIndices);
                }
                List<String> blockPalette;
                synchronized (paletteIndex) {
                    blockPalette = List.copyOf(palette);
                }
                result.complete(Schematic.of(
                        width,
                        height,
                        length,
                        min.minus(origin),
                        dataVersion(),
                        blockPalette,
                        blocks,
                        sortedBlockEntities,
                        sortedEntities,
                        biomes,
                        options.metadata()));
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        }

        private boolean inside(int x, int y, int z) {
            return x >= min.x() && x <= max.x() && y >= min.y() && y <= max.y() && z >= min.z() && z <= max.z();
        }
    }

    @SuppressWarnings("deprecation") // UnsafeValues is where the server says which version its names are of
    private static int dataVersion() {
        return Bukkit.getUnsafe().getDataVersion();
    }
}
