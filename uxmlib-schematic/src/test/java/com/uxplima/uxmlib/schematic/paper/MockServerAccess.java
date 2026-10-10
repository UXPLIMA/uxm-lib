package com.uxplima.uxmlib.schematic.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;

import com.uxplima.uxmlib.schematic.nbt.NbtCompound;

/**
 * The server parts MockBukkit does not have, stood in for in the plainest way that still lets a test see
 * what was asked: a chunk is loaded at once, a new block type is set before its data, a facing is turned by its own property, the block entities
 * of a chunk are found by looking at every block of it, and an entity is an armor stand that remembers the
 * tags and the place it was made from.
 */
final class MockServerAccess implements ServerAccess {

    record Made(String id, NbtCompound data, Location at) {}

    final List<Made> made = new ArrayList<>();
    final List<Location> loaded = new ArrayList<>();
    int turned;

    @Override
    public CompletableFuture<?> load(World world, int chunkX, int chunkZ) {
        loaded.add(new Location(world, chunkX, 0, chunkZ));
        return CompletableFuture.completedFuture(null);
    }

    /**
     * MockBukkit makes a block's state for its type only when the type is set, so a new type is set first.
     * The same type keeps the state it has, as a server keeps a chest's block entity when a chest is set on it.
     */
    @Override
    public void set(Block block, BlockData data) {
        if (block.getType() != data.getMaterial()) {
            block.setType(data.getMaterial(), false);
        }
        block.setBlockData(data, false);
    }

    @Override
    public void turn(BlockData data, StructureRotation rotation) {
        turned++;
        if (data instanceof Directional directional) {
            Rotation turn =
                    switch (rotation) {
                        case NONE -> Rotation.NONE;
                        case CLOCKWISE_90 -> Rotation.CLOCKWISE_90;
                        case CLOCKWISE_180 -> Rotation.CLOCKWISE_180;
                        case COUNTERCLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
                    };
            directional.setFacing(turn.face(directional.getFacing()));
        }
    }

    @Override
    public BlockState[] blockEntities(Chunk chunk) {
        List<BlockState> found = new ArrayList<>();
        World world = chunk.getWorld();
        for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    var block = chunk.getBlock(x, y, z);
                    if (!block.getType().isAir() && block.getState() instanceof TileState state) {
                        found.add(state);
                    }
                }
            }
        }
        return found.toArray(BlockState[]::new);
    }

    @Override
    public Optional<Entity> make(String id, NbtCompound data, Location at, int dataVersion) {
        made.add(new Made(id, data, at.clone()));
        return Optional.of(at.getWorld().spawn(at, ArmorStand.class));
    }

    @Override
    public boolean spawn(Entity made, Location at) {
        return made.isValid();
    }

    @Override
    public NbtCompound save(Entity entity) {
        return NbtCompound.builder()
                .putString("CustomName", "\"" + entity.getName() + "\"")
                .build();
    }
}
