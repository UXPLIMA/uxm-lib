package com.uxplima.uxmlib.schematic.paper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.schematic.nbt.NamedTag;
import com.uxplima.uxmlib.schematic.nbt.NbtCompound;
import com.uxplima.uxmlib.schematic.nbt.NbtReader;
import com.uxplima.uxmlib.schematic.nbt.NbtWriter;

/**
 * An item as a file keeps it, through Paper's own item serialisation.
 *
 * <p>Paper writes an item as the game's own tags and reads one back through the game's data fixers, so an
 * item saved by an older game is upgraded the way the server upgrades its own worlds.
 */
final class ItemNbt {

    private ItemNbt() {}

    static NbtCompound write(ItemStack item) {
        try {
            NamedTag written = NbtReader.withDefaults().read(new ByteArrayInputStream(item.serializeAsBytes()));
            return written.tag().toBuilder().remove("DataVersion").build();
        } catch (IOException e) {
            throw new UncheckedIOException("Paper wrote an item this reader cannot read", e);
        }
    }

    /** The item, or nothing for tags no item can be read from. */
    static Optional<ItemStack> read(NbtCompound item, int dataVersion) {
        try {
            NbtCompound versioned = item.toBuilder()
                    .remove("Slot")
                    .putInt("DataVersion", dataVersion)
                    .build();
            byte[] bytes = NbtWriter.toBytes(new NamedTag("", versioned), true);
            ItemStack read = ItemStack.deserializeBytes(bytes);
            return read.isEmpty() ? Optional.empty() : Optional.of(read);
        } catch (IOException | RuntimeException notAnItem) {
            return Optional.empty();
        }
    }
}
