package com.uxplima.uxmlib.hologram;

import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import org.jspecify.annotations.Nullable;

/**
 * The visual styling of a hologram, separate from its text so {@link HologramSpec} stays small. Every field
 * maps to a native {@link Display} or {@link TextDisplay} setter; a {@code null} leaves Paper's default. Build
 * one fluently with {@link #DEFAULT} and the {@code with*} methods, or let {@link Holograms.Builder} set them.
 *
 * <p>The {@code alignment} (LEFT / CENTER / RIGHT) is a text-only property and applies only to a text display;
 * the {@code shadowRadius} and {@code shadowStrength} are the display drop-shadow on the ground beneath the
 * entity (distinct from {@code textShadow}, which is the per-glyph shadow on the text itself) and apply to every
 * display type.
 */
public record Appearance(
        Display.Billboard billboard,
        boolean seeThrough,
        @Nullable Color glow,
        @Nullable Color background,
        @Nullable Byte textOpacity,
        @Nullable Integer lineWidth,
        boolean textShadow,
        @Nullable Float viewRange,
        Display.@Nullable Brightness brightness,
        @Nullable Transform transform,
        TextDisplay.@Nullable TextAlignment alignment,
        @Nullable Float shadowRadius,
        @Nullable Float shadowStrength) {

    /** The default appearance: centred billboard, no overrides. */
    public static final Appearance DEFAULT = new Appearance(
            Display.Billboard.CENTER, false, null, null, null, null, false, null, null, null, null, null, null);

    public Appearance withBillboard(Display.Billboard value) {
        return new Appearance(
                value,
                seeThrough,
                glow,
                background,
                textOpacity,
                lineWidth,
                textShadow,
                viewRange,
                brightness,
                transform,
                alignment,
                shadowRadius,
                shadowStrength);
    }

    public Appearance withSeeThrough(boolean value) {
        return new Appearance(
                billboard,
                value,
                glow,
                background,
                textOpacity,
                lineWidth,
                textShadow,
                viewRange,
                brightness,
                transform,
                alignment,
                shadowRadius,
                shadowStrength);
    }

    public Appearance withGlow(@Nullable Color value) {
        return new Appearance(
                billboard,
                seeThrough,
                value,
                background,
                textOpacity,
                lineWidth,
                textShadow,
                viewRange,
                brightness,
                transform,
                alignment,
                shadowRadius,
                shadowStrength);
    }

    public Appearance withBackground(@Nullable Color value) {
        return new Appearance(
                billboard,
                seeThrough,
                glow,
                value,
                textOpacity,
                lineWidth,
                textShadow,
                viewRange,
                brightness,
                transform,
                alignment,
                shadowRadius,
                shadowStrength);
    }

    public Appearance withTextOpacity(@Nullable Byte value) {
        return new Appearance(
                billboard,
                seeThrough,
                glow,
                background,
                value,
                lineWidth,
                textShadow,
                viewRange,
                brightness,
                transform,
                alignment,
                shadowRadius,
                shadowStrength);
    }

    public Appearance withLineWidth(@Nullable Integer value) {
        return new Appearance(
                billboard,
                seeThrough,
                glow,
                background,
                textOpacity,
                value,
                textShadow,
                viewRange,
                brightness,
                transform,
                alignment,
                shadowRadius,
                shadowStrength);
    }

    public Appearance withTextShadow(boolean value) {
        return new Appearance(
                billboard,
                seeThrough,
                glow,
                background,
                textOpacity,
                lineWidth,
                value,
                viewRange,
                brightness,
                transform,
                alignment,
                shadowRadius,
                shadowStrength);
    }

    public Appearance withViewRange(@Nullable Float value) {
        return new Appearance(
                billboard,
                seeThrough,
                glow,
                background,
                textOpacity,
                lineWidth,
                textShadow,
                value,
                brightness,
                transform,
                alignment,
                shadowRadius,
                shadowStrength);
    }

    public Appearance withBrightness(Display.@Nullable Brightness value) {
        return new Appearance(
                billboard,
                seeThrough,
                glow,
                background,
                textOpacity,
                lineWidth,
                textShadow,
                viewRange,
                value,
                transform,
                alignment,
                shadowRadius,
                shadowStrength);
    }

    public Appearance withTransform(@Nullable Transform value) {
        return new Appearance(
                billboard,
                seeThrough,
                glow,
                background,
                textOpacity,
                lineWidth,
                textShadow,
                viewRange,
                brightness,
                value,
                alignment,
                shadowRadius,
                shadowStrength);
    }

    public Appearance withAlignment(TextDisplay.@Nullable TextAlignment value) {
        return new Appearance(
                billboard,
                seeThrough,
                glow,
                background,
                textOpacity,
                lineWidth,
                textShadow,
                viewRange,
                brightness,
                transform,
                value,
                shadowRadius,
                shadowStrength);
    }

    public Appearance withShadowRadius(@Nullable Float value) {
        return new Appearance(
                billboard,
                seeThrough,
                glow,
                background,
                textOpacity,
                lineWidth,
                textShadow,
                viewRange,
                brightness,
                transform,
                alignment,
                value,
                shadowStrength);
    }

    public Appearance withShadowStrength(@Nullable Float value) {
        return new Appearance(
                billboard,
                seeThrough,
                glow,
                background,
                textOpacity,
                lineWidth,
                textShadow,
                viewRange,
                brightness,
                transform,
                alignment,
                shadowRadius,
                value);
    }

    /** Apply every set field to {@code display}, leaving unset (null/false) fields at Paper's default. */
    void applyTo(TextDisplay display) {
        applyToDisplay(display);
        display.setSeeThrough(seeThrough);
        display.setShadowed(textShadow);
        if (background != null) {
            display.setBackgroundColor(background);
        }
        if (textOpacity != null) {
            display.setTextOpacity(textOpacity);
        }
        if (lineWidth != null) {
            display.setLineWidth(lineWidth);
        }
        if (alignment != null) {
            display.setAlignment(alignment);
        }
    }

    /**
     * Apply the fields every {@link Display} shares: billboard, glow, view range, brightness, transform and the
     * drop-shadow radius/strength, to {@code display}, leaving the text-only fields (background, opacity, line
     * width, text shadow, see-through, alignment) untouched. An item or block display has no text, so only this
     * subset applies to it; {@link #applyTo} layers the text-only fields on top for a {@link TextDisplay}.
     */
    void applyToDisplay(org.bukkit.entity.Display display) {
        display.setBillboard(billboard);
        if (glow != null) {
            display.setGlowing(true);
            display.setGlowColorOverride(glow);
        }
        if (viewRange != null) {
            display.setViewRange(viewRange);
        }
        if (brightness != null) {
            display.setBrightness(brightness);
        }
        if (transform != null) {
            display.setTransformation(transform.toBukkit());
        }
        if (shadowRadius != null) {
            display.setShadowRadius(shadowRadius);
        }
        if (shadowStrength != null) {
            display.setShadowStrength(shadowStrength);
        }
    }
}
