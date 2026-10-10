package com.uxplima.uxmlib.schematic.paper;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Entity;

import com.uxplima.uxmlib.schematic.nbt.NbtCompound;

/**
 * What a paste and a capture ask of the server beyond setting and reading blocks, in one place, so a test
 * can stand in for the parts a mock server does not have.
 */
interface ServerAccess {

    /** The server itself. */
    ServerAccess SERVER = new ServerAccess() {};

    /** Loads a chunk ahead of the work on it, without holding a server thread. */
    default CompletableFuture<?> load(World world, int chunkX, int chunkZ) {
        return world.getChunkAtAsync(chunkX, chunkZ);
    }

    /** Sets a block without physics, so nothing breaks for want of what the next slice builds. */
    default void set(Block block, BlockData data) {
        block.setBlockData(data, false);
    }

    /** Turns block data in place. */
    default void turn(BlockData data, StructureRotation rotation) {
        data.rotate(rotation);
    }

    /** The block entities a chunk holds, read without a copy. */
    default BlockState[] blockEntities(Chunk chunk) {
        return chunk.getTileEntities(false);
    }

    /** Makes the entity {@code id} from its tags at {@code at}, not yet in the world. */
    default Optional<Entity> make(String id, NbtCompound data, Location at, int dataVersion) {
        return EntityNbt.read(id, data, at, dataVersion);
    }

    /** Puts a made entity into the world at {@code at}. Answers whether the world took it. */
    default boolean spawn(Entity made, Location at) {
        return made.spawnAt(at);
    }

    /** An entity's tags, without its identity. */
    default NbtCompound save(Entity entity) {
        return EntityNbt.write(entity);
    }
}
