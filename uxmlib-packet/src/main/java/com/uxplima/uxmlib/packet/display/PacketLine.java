package com.uxplima.uxmlib.packet.display;

import java.util.Objects;

import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

/**
 * One line of a {@link PacketHologram}: text, a floating item, a floating block, or a mob standing still.
 *
 * <p>Each kind is a different entity on the client, so a line that changes kind is taken down and sent again
 * rather than repainted. Two lines are the same when they are equal, which is how a frame decides that a
 * viewer's client already holds a line and needs no packet for it.
 */
public sealed interface PacketLine {

    /** A line of text, drawn by a text display. */
    record Text(Component text) implements PacketLine {
        public Text {
            Objects.requireNonNull(text, "text");
        }

        @Override
        public String entityTypeKey() {
            return "text_display";
        }
    }

    /** A floating item, drawn by an item display. A player head with a skin is an item. */
    record Item(ItemStack item) implements PacketLine {
        public Item {
            item = Objects.requireNonNull(item, "item").clone();
        }

        @Override
        public ItemStack item() {
            return item.clone();
        }

        @Override
        public String entityTypeKey() {
            return "item_display";
        }
    }

    /** A floating block, drawn by a block display. */
    record Block(BlockData block) implements PacketLine {
        public Block {
            block = Objects.requireNonNull(block, "block").clone();
        }

        @Override
        public BlockData block() {
            return block.clone();
        }

        @Override
        public String entityTypeKey() {
            return "block_display";
        }
    }

    /**
     * A mob of the named type, standing at the line's place. The client draws it and nothing on the server moves
     * it, so it stands still without any AI to turn off.
     */
    record Mob(String entityTypeKey) implements PacketLine {
        public Mob {
            Objects.requireNonNull(entityTypeKey, "entityTypeKey");
        }
    }

    /** The registry key of the entity type the client is sent for this line. */
    String entityTypeKey();

    static PacketLine text(Component text) {
        return new Text(text);
    }

    static PacketLine item(ItemStack item) {
        return new Item(item);
    }

    static PacketLine block(BlockData block) {
        return new Block(block);
    }

    static PacketLine mob(String entityTypeKey) {
        return new Mob(entityTypeKey);
    }
}
