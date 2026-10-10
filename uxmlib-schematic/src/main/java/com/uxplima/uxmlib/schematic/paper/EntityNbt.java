package com.uxplima.uxmlib.schematic.paper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import com.uxplima.uxmlib.schematic.nbt.NamedTag;
import com.uxplima.uxmlib.schematic.nbt.NbtCompound;
import com.uxplima.uxmlib.schematic.nbt.NbtList;
import com.uxplima.uxmlib.schematic.nbt.NbtReader;
import com.uxplima.uxmlib.schematic.nbt.NbtTag;
import com.uxplima.uxmlib.schematic.nbt.NbtWriter;

/** An entity as a file keeps it, through Paper's own entity serialisation. */
final class EntityNbt {

    private EntityNbt() {}

    /** The entity's tags, without its identity, so a paste of it is a new entity. */
    @SuppressWarnings("deprecation") // UnsafeValues is the only entity serialisation Paper offers
    static NbtCompound write(Entity entity) {
        try {
            NamedTag written = NbtReader.withDefaults()
                    .read(new ByteArrayInputStream(Bukkit.getUnsafe().serializeEntity(entity)));
            return written.tag().toBuilder()
                    .remove("DataVersion")
                    .remove("UUID")
                    .remove("id")
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException("Paper wrote an entity this reader cannot read", e);
        }
    }

    /**
     * Makes the entity {@code id} from {@code data} where {@code at} is, not yet in the world, or nothing
     * for tags no entity can be made from.
     */
    @SuppressWarnings("deprecation") // UnsafeValues is the only entity serialisation Paper offers
    static Optional<Entity> read(String id, NbtCompound data, Location at, int dataVersion) {
        try {
            NbtCompound placed = data.toBuilder()
                    .putString("id", id)
                    .put(
                            "Pos",
                            NbtList.of(List.of(
                                    new NbtTag.DoubleTag(at.getX()),
                                    new NbtTag.DoubleTag(at.getY()),
                                    new NbtTag.DoubleTag(at.getZ()))))
                    .remove("UUID")
                    .putInt("DataVersion", dataVersion)
                    .build();
            byte[] bytes = NbtWriter.toBytes(new NamedTag("", placed), true);
            return Optional.ofNullable(Bukkit.getUnsafe().deserializeEntity(bytes, at.getWorld(), false));
        } catch (IOException | RuntimeException notAnEntity) {
            return Optional.empty();
        }
    }
}
