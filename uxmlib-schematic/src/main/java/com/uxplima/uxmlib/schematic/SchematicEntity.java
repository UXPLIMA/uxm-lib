package com.uxplima.uxmlib.schematic;

import java.util.Objects;

import com.uxplima.uxmlib.schematic.nbt.NbtCompound;

/**
 * An entity saved with a structure: an armor stand, an item frame, a villager.
 *
 * @param x where it stands, in blocks from the schematic's corner
 * @param id the entity's kind, such as {@code minecraft:armor_stand}
 * @param data its fields in the game's own form, without the position and the kind
 */
public record SchematicEntity(double x, double y, double z, String id, NbtCompound data) {

    public SchematicEntity {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(data, "data must not be null");
    }
}
