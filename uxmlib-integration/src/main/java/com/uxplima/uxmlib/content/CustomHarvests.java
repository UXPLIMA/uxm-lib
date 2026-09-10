package com.uxplima.uxmlib.content;

import java.util.Optional;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * What a custom farming or fishing plugin calls one crop or one catch.
 *
 * <p>Two vendors' worth of the same shape. A server running CustomCrops has a tomato that is a block of
 * something else with data on it, and a server running CustomFishing has a catch that is an item nothing
 * else knows the name of. A jobs plugin that priced the underlying block or item would pay the same for a
 * tomato as for the wheat it is drawn as.
 */
public interface CustomHarvests {

    /** A seam that answers for nothing, which is a server running neither kind of plugin. */
    CustomHarvests NONE = new CustomHarvests() {

        @Override
        public boolean active() {
            return false;
        }

        @Override
        public Optional<String> cropAt(Block block) {
            return Optional.empty();
        }

        @Override
        public Optional<String> catchOf(ItemStack stack) {
            return Optional.empty();
        }
    };

    /** Whether any custom crop or fishing plugin answered on this server. */
    boolean active();

    /** What is growing on this block, or nothing when it is an ordinary crop. */
    Optional<String> cropAt(Block block);

    /** What this catch is called, or nothing when it is an ordinary fish. */
    Optional<String> catchOf(ItemStack stack);
}
