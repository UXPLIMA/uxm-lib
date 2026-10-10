package com.uxplima.uxmlib.schematic;

import java.util.Objects;

import com.uxplima.uxmlib.schematic.nbt.NbtCompound;

/**
 * What a block holds besides its state: a chest's items, a sign's text, a spawner's mob.
 *
 * @param pos where the block is, from the schematic's corner
 * @param id the block entity's kind, such as {@code minecraft:chest}
 * @param data its fields in the game's own form, without the position and the kind
 */
public record SchematicBlockEntity(Vec3i pos, String id, NbtCompound data) {

    public SchematicBlockEntity {
        Objects.requireNonNull(pos, "pos must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(data, "data must not be null");
    }
}
