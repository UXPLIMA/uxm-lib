package com.uxplima.uxmlib.hologram;

import java.util.Objects;

import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;

/**
 * A {@link ModelHologram} backed by a live {@link BlockDisplay} showing a floating block model. The block can
 * be swapped in place with {@link #setBlock(BlockData)} (a refresh, not a re-spawn); every other operation:
 * move, transform, mount, per-viewer visibility, removal, comes from {@link AbstractModelHologram} and behaves
 * exactly as it does for a text {@link Hologram}.
 */
public final class BlockHologram extends AbstractModelHologram<BlockDisplay> {

    BlockHologram(BlockDisplay display) {
        super(display);
    }

    /** Replace the displayed block. */
    public void setBlock(BlockData block) {
        display.setBlock(Objects.requireNonNull(block, "block"));
    }
}
