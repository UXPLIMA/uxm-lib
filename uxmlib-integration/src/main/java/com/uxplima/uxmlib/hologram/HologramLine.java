package com.uxplima.uxmlib.hologram;

import java.util.Objects;

import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

/**
 * One line of a {@link MixedHologramSpec}: text, a floating item, or a floating block. Each variant maps
 * to a native {@code Display} entity ({@code TextDisplay} / {@code ItemDisplay} / {@code BlockDisplay}), so
 * a single hologram can stack a heading, an item icon, and a block model together without packets.
 *
 * <p>A line also carries the vertical {@code gap} (in blocks) the next line sits below it; this lets an item
 * or block line, which is physically taller than a text line, reserve more room. {@link MixedHologramSpec}
 * turns these gaps into absolute stack offsets.
 */
public sealed interface HologramLine permits HologramLine.TextLine, HologramLine.ItemLine, HologramLine.BlockLine {

    /** Default vertical drop below a text line: roughly one line of text at default scale. */
    double TEXT_GAP = 0.28;

    /** Default vertical drop below an item or block line: they occupy more vertical space than text. */
    double MODEL_GAP = 0.55;

    /** How far (in blocks) the next line sits below this one. */
    double gap();

    /** A text line backed by a {@code TextDisplay}. */
    record TextLine(Component text, double gap) implements HologramLine {
        public TextLine {
            Objects.requireNonNull(text, "text");
            requirePositiveGap(gap);
        }

        /** A text line at the default text gap. */
        public TextLine(Component text) {
            this(text, TEXT_GAP);
        }
    }

    /** An item line backed by an {@code ItemDisplay}. */
    record ItemLine(ItemStack item, double gap) implements HologramLine {
        public ItemLine {
            Objects.requireNonNull(item, "item");
            requirePositiveGap(gap);
        }

        /** An item line at the default model gap. */
        public ItemLine(ItemStack item) {
            this(item, MODEL_GAP);
        }
    }

    /** A block line backed by a {@code BlockDisplay}. */
    record BlockLine(BlockData block, double gap) implements HologramLine {
        public BlockLine {
            Objects.requireNonNull(block, "block");
            requirePositiveGap(gap);
        }

        /** A block line at the default model gap. */
        public BlockLine(BlockData block) {
            this(block, MODEL_GAP);
        }
    }

    private static void requirePositiveGap(double gap) {
        if (!(gap > 0)) {
            throw new IllegalArgumentException("gap must be > 0");
        }
    }
}
