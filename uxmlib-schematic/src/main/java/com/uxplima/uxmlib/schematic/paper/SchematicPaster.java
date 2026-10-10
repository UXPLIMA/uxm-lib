package com.uxplima.uxmlib.schematic.paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.Ticks;
import com.uxplima.uxmlib.schematic.Schematic;
import com.uxplima.uxmlib.schematic.SchematicBiomes;
import com.uxplima.uxmlib.schematic.SchematicBlockEntity;
import com.uxplima.uxmlib.schematic.SchematicEntity;
import com.uxplima.uxmlib.schematic.Vec3i;
import com.uxplima.uxmlib.schematic.nbt.NbtCompound;
import com.uxplima.uxmlib.schematic.nbt.NbtList;
import com.uxplima.uxmlib.schematic.nbt.NbtTag;
import org.jspecify.annotations.Nullable;

/**
 * Puts a schematic into a world, a chunk at a time, on the thread that owns each chunk.
 *
 * <p>Each chunk the paste covers is loaded without waiting on a server thread, then worked on its own region
 * a slice a tick, so a large paste spreads over ticks and over regions rather than stopping either. Blocks
 * are set without physics, so a torch does not drop for want of the wall that the next slice builds. A
 * chunk's block entities are filled once its blocks stand, and the entities are made once every block
 * stands, so an item frame finds its wall.
 *
 * <p>The schematic's air leaves what is there standing unless {@link PasteOptions#pasteAir()} says
 * otherwise. A structure void is never pasted: it marks where a structure leaves the world alone. A block
 * entity already standing where a block is pasted is cleared first, so a chest pasted over a chest holds
 * what the schematic holds and nothing of what was there.
 *
 * <p>Cancelling the returned future stops the paste after the slice in hand.
 */
public final class SchematicPaster {

    private static final String STRUCTURE_VOID = "minecraft:structure_void";

    private final Scheduler scheduler;
    private final BlockStates states;
    private final ServerAccess server;

    public SchematicPaster(Scheduler scheduler) {
        this(scheduler, new BlockStates(), ServerAccess.SERVER);
    }

    SchematicPaster(Scheduler scheduler, BlockStates states, ServerAccess server) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.states = Objects.requireNonNull(states, "states must not be null");
        this.server = Objects.requireNonNull(server, "server must not be null");
    }

    /**
     * Pastes {@code schematic} so the point it was saved around lands on the block at {@code at}.
     *
     * @return the report, once every block, block entity and entity is placed
     */
    public CompletableFuture<PasteReport> paste(Schematic schematic, Location at, PasteOptions options) {
        Objects.requireNonNull(schematic, "schematic must not be null");
        Objects.requireNonNull(options, "options must not be null");
        World world = Objects.requireNonNull(at.getWorld(), "the paste point must be in a world");
        Paste paste = new Paste(schematic, world, at.getBlockX(), at.getBlockY(), at.getBlockZ(), options);
        paste.start();
        return paste.result;
    }

    /** One paste in progress: what it places, where, and how far it has come. */
    private final class Paste {

        final CompletableFuture<PasteReport> result = new CompletableFuture<>();
        private final Schematic schematic;
        private final World world;
        private final int pivotX;
        private final int pivotY;
        private final int pivotZ;
        private final PasteOptions options;
        private final Rotation rotation;
        private final Rotation inverse;
        private final Vec3i offset;
        private final int minHeight;
        private final int maxHeight;
        private final @Nullable BlockData[] data;
        private final boolean[] skip;
        private final BlockStates.Resolved resolved;
        private final @Nullable Biome @Nullable [] biomes;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private final int lowY;
        private final int highY;
        private final Map<Long, List<SchematicBlockEntity>> blockEntities = new HashMap<>();
        private final Map<Long, List<SchematicEntity>> entities = new LinkedHashMap<>();
        private final ConcurrentLinkedQueue<Long> chunks = new ConcurrentLinkedQueue<>();
        private final AtomicInteger chunksLeft = new AtomicInteger();
        private final AtomicLong placed = new AtomicLong();
        private final AtomicInteger blockEntitiesApplied = new AtomicInteger();
        private final Set<String> notCarried = ConcurrentHashMap.newKeySet();
        private final AtomicInteger spawned = new AtomicInteger();
        private final Set<String> refused = ConcurrentHashMap.newKeySet();
        private long outside;

        Paste(Schematic schematic, World world, int pivotX, int pivotY, int pivotZ, PasteOptions options) {
            this.schematic = schematic;
            this.world = world;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = pivotZ;
            this.options = options;
            this.rotation = options.rotation();
            this.inverse = rotation.inverse();
            this.offset = schematic.offset();
            this.minHeight = world.getMinHeight();
            this.maxHeight = world.getMaxHeight();
            this.resolved = states.resolve(schematic.palette(), schematic.dataVersion());
            this.data = new BlockData[resolved.size()];
            this.skip = new boolean[resolved.size()];
            for (int i = 0; i < resolved.size(); i++) {
                BlockData found = resolved.at(i);
                if (found != null && rotation != Rotation.NONE) {
                    server.turn(found, rotation.structure());
                }
                data[i] = found;
                skip[i] = found == null
                        || schematic.palette().get(i).equals(STRUCTURE_VOID)
                        || (!options.pasteAir() && found.getMaterial().isAir());
            }
            this.biomes = options.biomes() ? biomes(schematic.biomes().orElse(null)) : null;
            int ax = pivotX + rotation.x(offset.x(), offset.z());
            int az = pivotZ + rotation.z(offset.x(), offset.z());
            int bx = pivotX + rotation.x(offset.x() + schematic.width() - 1, offset.z() + schematic.length() - 1);
            int bz = pivotZ + rotation.z(offset.x() + schematic.width() - 1, offset.z() + schematic.length() - 1);
            this.minX = Math.min(ax, bx);
            this.maxX = Math.max(ax, bx);
            this.minZ = Math.min(az, bz);
            this.maxZ = Math.max(az, bz);
            int bottom = pivotY + offset.y();
            this.lowY = Math.max(bottom, minHeight);
            this.highY = Math.min(bottom + schematic.height() - 1, maxHeight - 1);
        }

        void start() {
            try {
                outside = countOutside();
                for (SchematicBlockEntity entity : schematic.blockEntities()) {
                    Vec3i pos = place(entity.pos());
                    blockEntities
                            .computeIfAbsent(key(pos.x() >> 4, pos.z() >> 4), unused -> new ArrayList<>())
                            .add(entity);
                }
                if (options.entities()) {
                    for (SchematicEntity entity : schematic.entities()) {
                        Location to = target(entity);
                        entities.computeIfAbsent(
                                        key(to.getBlockX() >> 4, to.getBlockZ() >> 4), unused -> new ArrayList<>())
                                .add(entity);
                    }
                }
                if (lowY <= highY) {
                    for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                        for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                            chunks.add(key(cx, cz));
                        }
                    }
                }
                chunksLeft.set(chunks.size());
                if (chunks.isEmpty()) {
                    placeEntities();
                    return;
                }
                for (int i = 0; i < options.concurrency(); i++) {
                    next();
                }
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        }

        /** Takes the next chunk, if there is one and the paste still runs. */
        private void next() {
            Long chunk = chunks.poll();
            if (chunk == null || result.isDone()) {
                return;
            }
            int cx = chunkX(chunk);
            int cz = chunkZ(chunk);
            Slices slices = new Slices(cx, cz);
            onRegion(cx, cz, () -> work(slices));
        }

        /** Loads the chunk, then runs {@code task} on the region that owns it. */
        private void onRegion(int cx, int cz, Runnable task) {
            Location anchor = new Location(world, cx << 4, 0, cz << 4);
            var unused = server.load(world, cx, cz).whenComplete((loaded, failure) -> {
                if (failure != null) {
                    result.completeExceptionally(failure);
                    return;
                }
                try {
                    scheduler.region(anchor, task);
                } catch (RuntimeException refused) {
                    result.completeExceptionally(refused);
                }
            });
        }

        private void work(Slices slices) {
            if (result.isDone()) {
                return;
            }
            try {
                if (slices.work()) {
                    fillBlockEntities(slices.cx, slices.cz);
                    if (chunksLeft.decrementAndGet() == 0) {
                        placeEntities();
                    } else {
                        next();
                    }
                } else {
                    scheduler.regionLater(slices.anchor, Ticks.ONE_TICK, () -> work(slices));
                }
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        }

        private void fillBlockEntities(int cx, int cz) {
            for (SchematicBlockEntity entity : blockEntities.getOrDefault(key(cx, cz), List.of())) {
                Vec3i pos = place(entity.pos());
                int index = schematic.paletteIndexAt(schematic.indexOf(
                        entity.pos().x(), entity.pos().y(), entity.pos().z()));
                if (pos.y() < minHeight
                        || pos.y() >= maxHeight
                        || data[index] == null
                        || entity.data().entries().isEmpty()) {
                    continue;
                }
                Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
                if (BlockEntities.apply(block, entity.data(), schematic.dataVersion())) {
                    blockEntitiesApplied.incrementAndGet();
                } else {
                    notCarried.add(entity.id());
                }
            }
        }

        private void placeEntities() {
            if (entities.isEmpty()) {
                finish();
                return;
            }
            AtomicInteger left = new AtomicInteger(entities.size());
            for (Map.Entry<Long, List<SchematicEntity>> chunk : entities.entrySet()) {
                int cx = chunkX(chunk.getKey());
                int cz = chunkZ(chunk.getKey());
                onRegion(cx, cz, () -> {
                    if (result.isDone()) {
                        return;
                    }
                    try {
                        chunk.getValue().forEach(this::spawn);
                        if (left.decrementAndGet() == 0) {
                            finish();
                        }
                    } catch (RuntimeException e) {
                        result.completeExceptionally(e);
                    }
                });
            }
        }

        private void spawn(SchematicEntity saved) {
            Location to = target(saved);
            if (to.getY() < minHeight || to.getY() >= maxHeight) {
                refused.add(saved.id());
                return;
            }
            NbtCompound fields = saved.data();
            Vec3i anchor = anchor(saved, locate(saved));
            if (anchor != null) {
                fields = reanchor(fields, anchor);
            }
            Entity made =
                    server.make(saved.id(), fields, to, schematic.dataVersion()).orElse(null);
            if (made == null) {
                refused.add(saved.id());
                return;
            }
            if (!server.spawn(made, to)) {
                refused.add(saved.id());
                return;
            }
            if (made instanceof Hanging hanging && rotation != Rotation.NONE) {
                hanging.setFacingDirection(rotation.face(hanging.getFacing()), true);
            }
            spawned.incrementAndGet();
        }

        private void finish() {
            result.complete(new PasteReport(
                    placed.get(),
                    outside,
                    blockEntitiesApplied.get(),
                    notCarried,
                    spawned.get(),
                    refused,
                    resolved.changed(),
                    resolved.unknown()));
        }

        /** Where a block of the schematic lands in the world. */
        private Vec3i place(Vec3i local) {
            int x = offset.x() + local.x();
            int z = offset.z() + local.z();
            return new Vec3i(pivotX + rotation.x(x, z), pivotY + offset.y() + local.y(), pivotZ + rotation.z(x, z));
        }

        /**
         * Where an entity is made: where it lands, or for a hanging entity the corner of the block it hangs in,
         * since the server takes a hanging entity's block from the position it is made at.
         */
        private Location target(SchematicEntity saved) {
            Location to = locate(saved);
            Vec3i anchor = anchor(saved, to);
            return anchor == null
                    ? to
                    : new Location(world, anchor.x(), anchor.y(), anchor.z(), to.getYaw(), to.getPitch());
        }

        /** Where an entity of the schematic lands, facing where it faced, turned with the paste. */
        private Location locate(SchematicEntity entity) {
            double x = offset.x() + entity.x() - 0.5;
            double z = offset.z() + entity.z() - 0.5;
            float yaw = 0f;
            float pitch = 0f;
            Object rotationTag = entity.data().get("Rotation");
            if (rotationTag instanceof NbtList angles && angles.size() == 2) {
                yaw = (float) NbtTag.asDouble(angles.values().get(0)).orElse(0);
                pitch = (float) NbtTag.asDouble(angles.values().get(1)).orElse(0);
            }
            return new Location(
                    world,
                    pivotX + 0.5 + rotation.x(x, z),
                    pivotY + offset.y() + entity.y(),
                    pivotZ + 0.5 + rotation.z(x, z),
                    rotation.yaw(yaw),
                    pitch);
        }

        /**
         * The block a hanging entity hangs in, carried along with it.
         *
         * <p>A file names that block in the frame of wherever it was saved, which need not be the frame its
         * entities' positions are given in. The two differ by whole blocks, so the step from the entity to the
         * middle of its block is the part of the difference short of a whole block: never as much as half a
         * block, but for a painting of an even size, which stands on the line between two blocks. Such a
         * painting stands half a block up from its block and half a block to the left of where it faces.
         */
        private @Nullable Vec3i anchor(SchematicEntity saved, Location to) {
            int[] tile = tile(saved.data());
            if (tile == null) {
                return null;
            }
            double dx = withinHalf(tile[0] + 0.5 - saved.x());
            double dy = withinHalf(tile[1] + 0.5 - saved.y());
            double dz = withinHalf(tile[2] + 0.5 - saved.z());
            BlockFace left = paintingLeft(saved);
            if (left != null && Math.abs(dx) == 0.5) {
                dx = -0.5 * left.getModX();
            }
            if (left != null && Math.abs(dz) == 0.5) {
                dz = -0.5 * left.getModZ();
            }
            return new Vec3i((int) Math.floor(to.getX() + rotation.x(dx, dz)), (int) Math.floor(to.getY() + dy), (int)
                    Math.floor(to.getZ() + rotation.z(dx, dz)));
        }

        /** {@code step} less the whole blocks in it, from half a block back to just short of half a block on. */
        private static double withinHalf(double step) {
            return step - Math.floor(step + 0.5);
        }

        /** For a painting, the side to its left as it faces, which an even width shifts it towards. */
        private static @Nullable BlockFace paintingLeft(SchematicEntity saved) {
            if (!saved.id().equals("minecraft:painting")) {
                return null;
            }
            var facing = saved.data().intValue("facing");
            if (facing.isEmpty()) {
                facing = saved.data().intValue("Facing");
            }
            if (facing.isEmpty()) {
                return null;
            }
            return switch (facing.getAsInt()) {
                case 0 -> BlockFace.EAST;
                case 1 -> BlockFace.SOUTH;
                case 2 -> BlockFace.WEST;
                case 3 -> BlockFace.NORTH;
                default -> null;
            };
        }

        private int @Nullable [] tile(NbtCompound fields) {
            if (fields.intArray("block_pos").filter(pos -> pos.length == 3).isPresent()) {
                return fields.intArray("block_pos").get();
            }
            var x = fields.intValue("TileX");
            var y = fields.intValue("TileY");
            var z = fields.intValue("TileZ");
            if (x.isPresent() && y.isPresent() && z.isPresent()) {
                return new int[] {x.getAsInt(), y.getAsInt(), z.getAsInt()};
            }
            return null;
        }

        private NbtCompound reanchor(NbtCompound fields, Vec3i anchor) {
            NbtCompound.Builder moved = fields.toBuilder();
            if (fields.has("block_pos")) {
                moved.putIntArray("block_pos", anchor.x(), anchor.y(), anchor.z());
            }
            if (fields.has("TileX")) {
                moved.putInt("TileX", anchor.x()).putInt("TileY", anchor.y()).putInt("TileZ", anchor.z());
            }
            return moved.build();
        }

        /** The blocks that would land above or below the world, counted and left out. */
        private long countOutside() {
            long count = 0;
            int bottom = pivotY + offset.y();
            for (int y = 0; y < schematic.height(); y++) {
                if (bottom + y >= minHeight && bottom + y < maxHeight) {
                    continue;
                }
                for (int z = 0; z < schematic.length(); z++) {
                    for (int x = 0; x < schematic.width(); x++) {
                        if (!skip[schematic.paletteIndexAt(schematic.indexOf(x, y, z))]) {
                            count++;
                        }
                    }
                }
            }
            return count;
        }

        private @Nullable Biome @Nullable [] biomes(@Nullable SchematicBiomes saved) {
            if (saved == null) {
                return null;
            }
            var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
            @Nullable Biome[] found = new Biome[saved.palette().size()];
            for (int i = 0; i < found.length; i++) {
                NamespacedKey key = NamespacedKey.fromString(saved.palette().get(i));
                found[i] = key == null ? null : registry.get(key);
            }
            return found;
        }

        /** The part of the paste one chunk holds, worked through a slice at a time. */
        private final class Slices {

            final int cx;
            final int cz;
            final Location anchor;
            private final int x0;
            private final int z0;
            private final int width;
            private final int length;
            private final long total;
            private long cursor;
            private @Nullable Set<Integer> occupied;
            private final Set<Integer> biomeCells = new HashSet<>();

            Slices(int cx, int cz) {
                this.cx = cx;
                this.cz = cz;
                this.anchor = new Location(world, cx << 4, 0, cz << 4);
                this.x0 = Math.max(minX, cx << 4);
                this.z0 = Math.max(minZ, cz << 4);
                this.width = Math.min(maxX, (cx << 4) + 15) - x0 + 1;
                this.length = Math.min(maxZ, (cz << 4) + 15) - z0 + 1;
                this.total = (long) width * length * (highY - lowY + 1);
            }

            /** Works one slice. Answers whether the chunk is done. */
            boolean work() {
                if (occupied == null) {
                    occupied = occupied();
                }
                long end = Math.min(total, cursor + options.blocksPerTick());
                SchematicBiomes savedBiomes = schematic.biomes().orElse(null);
                for (; cursor < end; cursor++) {
                    int column = (int) (cursor % ((long) width * length));
                    int wy = lowY + (int) (cursor / ((long) width * length));
                    int wx = x0 + column % width;
                    int wz = z0 + column / width;
                    int rx = wx - pivotX;
                    int rz = wz - pivotZ;
                    int sx = inverse.x(rx, rz) - offset.x();
                    int sz = inverse.z(rx, rz) - offset.z();
                    int sy = wy - pivotY - offset.y();
                    int index = schematic.indexOf(sx, sy, sz);
                    int palette = schematic.paletteIndexAt(index);
                    BlockData state = data[palette];
                    if (!skip[palette] && state != null) {
                        Block block = world.getBlockAt(wx, wy, wz);
                        if (occupied.contains(packed(wx, wy, wz))) {
                            block.setType(Material.AIR, false);
                        }
                        server.set(block, state);
                        placed.incrementAndGet();
                    }
                    if (biomes != null && savedBiomes != null && biomeCells.add(cell(wx, wy, wz))) {
                        Biome biome = biomes[savedBiomes.indexAt(index, schematic.width(), schematic.length())];
                        if (biome != null) {
                            world.setBiome(wx, wy, wz, biome);
                        }
                    }
                }
                return cursor >= total;
            }

            /** The positions in this chunk's part of the paste that hold a block entity now. */
            private Set<Integer> occupied() {
                Set<Integer> found = new HashSet<>();
                for (BlockState state : server.blockEntities(world.getChunkAt(cx, cz))) {
                    int x = state.getX();
                    int y = state.getY();
                    int z = state.getZ();
                    if (x >= x0 && x < x0 + width && z >= z0 && z < z0 + length && y >= lowY && y <= highY) {
                        found.add(packed(x, y, z));
                    }
                }
                return found;
            }

            private int packed(int x, int y, int z) {
                return (x & 15) | (z & 15) << 4 | (y - minHeight) << 8;
            }

            private int cell(int x, int y, int z) {
                return (x & 15) >> 2 | ((z & 15) >> 2) << 2 | ((y - minHeight) >> 2) << 4;
            }
        }
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static int chunkX(long key) {
        return (int) (key >> 32);
    }

    private static int chunkZ(long key) {
        return (int) key;
    }
}
