package com.uxplima.uxmlib.content;

import java.util.Optional;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * What a custom item plugin calls one item or one block.
 *
 * <p>Four analyses named this as the gap, in four different plugins, under the same words: "custom item
 * plugins". A server running Oraxen has ruby ore, and a jobs plugin that prices {@code DEEPSLATE} for it and
 * a crates plugin that cannot put one in a reward are both the same defect, so this is one seam and not
 * four.
 *
 * <p>The id is the vendor's own, namespaced by the vendor's name, so a file writes
 * {@code oraxen:ruby_ore} and means it. Two vendors that both call something {@code ruby_ore} are two
 * different things and the namespace is what says so.
 *
 * <p>Nothing here is a dependency. A server with no custom item plugin gets nothing from every method and
 * every caller reads that as "this is an ordinary item", which is what it is.
 */
public interface CustomItems {

    /** A seam that answers for nothing, which is a server running no custom item plugin. */
    CustomItems NONE = new CustomItems() {

        @Override
        public boolean active() {
            return false;
        }

        @Override
        public Optional<String> idOf(ItemStack stack) {
            return Optional.empty();
        }

        @Override
        public Optional<String> idOfBlock(Block block) {
            return Optional.empty();
        }

        @Override
        public Optional<ItemStack> itemOf(String id) {
            return Optional.empty();
        }
    };

    /** Whether any custom item plugin answered on this server. */
    boolean active();

    /** What this item is called, or nothing when it is an ordinary one. */
    Optional<String> idOf(ItemStack stack);

    /** What this block is called, or nothing when it is an ordinary one. */
    Optional<String> idOfBlock(Block block);

    /**
     * The item one id names, or nothing.
     *
     * <p>The other direction, and the one a reward table needs: a file writes an id and something has to
     * turn it into an item a player can be handed. An id whose plugin is not on this server is nothing, and
     * the caller says so rather than handing over a stone block named after it.
     */
    Optional<ItemStack> itemOf(String id);
}
