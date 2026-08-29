package com.uxplima.uxmlib.hologram;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import org.jspecify.annotations.Nullable;

/**
 * The immutable description of a multi-type hologram: an ordered list of {@link HologramLine}s (text, item,
 * and block lines together) plus a shared {@link Appearance} for the text lines. Pure: it spawns no
 * entities, so it can be assembled and asserted off a server. {@link Holograms} turns it into a column of
 * native {@code Display} entities, one per line, auto-stacked top-down by each line's gap.
 *
 * <p>This is the mixed-line companion to the text-only {@link HologramSpec}: when every line is text, prefer
 * {@code HologramSpec} (a single {@code TextDisplay}); reach for this when you want an item or block line in
 * the same column.
 */
public final class MixedHologramSpec {

    private final List<HologramLine> lines;
    private final Appearance appearance;
    private final @Nullable Double lineGapOverride;

    private MixedHologramSpec(List<HologramLine> lines, Appearance appearance, @Nullable Double lineGapOverride) {
        this.lines = List.copyOf(lines);
        this.appearance = appearance;
        this.lineGapOverride = lineGapOverride;
    }

    /** Start configuring a mixed-line hologram. */
    public static Builder builder() {
        return new Builder();
    }

    /** The lines, in top-to-bottom order. */
    public List<HologramLine> lines() {
        return lines;
    }

    /** The shared appearance applied to every text line. */
    public Appearance appearance() {
        return appearance;
    }

    /**
     * The vertical offset (in blocks, relative to the spec's anchor) of each line, top-down. The first line
     * is at {@code 0.0}; each line below drops by the line above it's gap (or the builder's uniform
     * {@code lineGap} override when set). Offsets decrease going down, matching world-space Y.
     */
    public List<Double> stackOffsets() {
        List<Double> offsets = new ArrayList<>(lines.size());
        double y = 0.0;
        for (int i = 0; i < lines.size(); i++) {
            offsets.add(y);
            y -= gapOf(lines.get(i));
        }
        return List.copyOf(offsets);
    }

    private double gapOf(HologramLine line) {
        return lineGapOverride != null ? lineGapOverride : line.gap();
    }

    /** Fluent builder for a mixed-line hologram. */
    public static final class Builder {
        private final List<HologramLine> lines = new ArrayList<>();
        private Appearance appearance = Appearance.DEFAULT;
        private @Nullable Double lineGapOverride;

        private Builder() {}

        /** Append a text line at the default text gap. */
        public Builder text(Component text) {
            lines.add(new HologramLine.TextLine(Objects.requireNonNull(text, "text")));
            return this;
        }

        /** Append a floating item line at the default model gap. */
        public Builder item(ItemStack item) {
            lines.add(new HologramLine.ItemLine(Objects.requireNonNull(item, "item")));
            return this;
        }

        /** Append a floating block line at the default model gap. */
        public Builder block(BlockData block) {
            lines.add(new HologramLine.BlockLine(Objects.requireNonNull(block, "block")));
            return this;
        }

        /** Append an already-built line (e.g. with a custom gap). */
        public Builder line(HologramLine line) {
            lines.add(Objects.requireNonNull(line, "line"));
            return this;
        }

        /** The shared appearance for the text lines. */
        public Builder appearance(Appearance value) {
            appearance = Objects.requireNonNull(value, "appearance");
            return this;
        }

        /** Override every line's gap with one uniform vertical spacing (in blocks). */
        public Builder lineGap(double gap) {
            if (!(gap > 0)) {
                throw new IllegalArgumentException("lineGap must be > 0");
            }
            lineGapOverride = gap;
            return this;
        }

        /** Build the immutable spec. Requires at least one line. */
        public MixedHologramSpec build() {
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("a hologram needs at least one line");
            }
            return new MixedHologramSpec(lines, appearance, lineGapOverride);
        }
    }
}
