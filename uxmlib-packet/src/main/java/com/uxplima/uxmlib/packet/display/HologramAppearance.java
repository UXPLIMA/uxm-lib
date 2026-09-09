package com.uxplima.uxmlib.packet.display;

import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import org.joml.Vector3f;

/**
 * How a {@link PacketHologram} looks: the text-display knobs a viewer's client applies, held as an immutable
 * value so the driver and its tests need no server.
 *
 * <p>The vocabulary is Bukkit's own ({@link Display.Billboard}, {@link TextDisplay.TextAlignment},
 * {@link Color}) and not an enum of ours, so a plugin that moves an entity hologram onto packets keeps the
 * words it already wrote for {@code Holograms.builder()}.
 *
 * <p>This carries the whole of the appearance table in the UI canon, which is the level uxmEssentials draws a
 * hologram at. It held ten of the properties until 2026-09-09 and a plugin that wanted a glow, a brightness
 * override, a ground shadow or a line gap had no way to ask for one. That is a library gap and it is fixed
 * here rather than worked around in the plugin that found it.
 *
 * <p>A property an operator did not set keeps the vanilla value, and each one that has no natural "unset"
 * carries a sentinel that says so: {@link #NO_GLOW}, {@link #WORLD_BRIGHTNESS} and {@link #VANILLA_SHADOW}.
 * A stored hologram that was never styled therefore reads back as vanilla and never as a magic number.
 *
 * <p>{@code com.uxplima.uxmlib.nametag.Appearance} is the entity-riding twin of this record and the two are
 * deliberately separate. That one carries the line-of-sight fade a nametag needs and rides a player; this one
 * is anchored in the world and fades for nobody. Sharing one record would mean moving the nametag types down
 * into this module, which renames types uxmEssentials already imports.
 *
 * @param billboard how the text turns to face the viewer
 * @param background the colour behind the glyphs; alpha 0 is a transparent panel
 * @param textShadow whether the glyphs cast a drop shadow
 * @param seeThrough whether the text draws through solid blocks
 * @param alignment how several lines are justified against each other
 * @param lineWidth the width in pixels at which the client wraps a line
 * @param viewRange the multiplier on the distance at which a client still draws the text
 * @param translation the offset from the anchor, applied before the billboard turn
 * @param scale the per-axis size of the text
 * @param textOpacity how opaque the glyphs are, 0 to 255
 * @param glowArgb the outline colour as packed ARGB, or {@link #NO_GLOW} for no outline
 * @param brightnessBlock the block-light override 0 to 15, or {@link #WORLD_BRIGHTNESS}
 * @param brightnessSky the sky-light override 0 to 15, or {@link #WORLD_BRIGHTNESS}
 * @param shadowRadius the shadow cast on the ground, or {@link #VANILLA_SHADOW}
 * @param shadowStrength how dark that ground shadow is, or {@link #VANILLA_SHADOW}
 * @param lineGap the vertical distance between two stacked lines, in translation units
 */
public record HologramAppearance(
        Display.Billboard billboard,
        Color background,
        boolean textShadow,
        boolean seeThrough,
        TextDisplay.TextAlignment alignment,
        int lineWidth,
        float viewRange,
        Vector3f translation,
        Vector3f scale,
        int textOpacity,
        int glowArgb,
        int brightnessBlock,
        int brightnessSky,
        float shadowRadius,
        float shadowStrength,
        float lineGap) {

    /** Fully opaque glyphs, which is what a hologram wants unless an operator asks for a ghost. */
    public static final int FULL_OPACITY = 255;

    /** The transparent panel a hologram carries when nobody asked for a background. */
    public static final Color NO_BACKGROUND = Color.fromARGB(0, 0, 0, 0);

    /** The glow sentinel: the text carries no outline at all, which is the vanilla look. */
    public static final int NO_GLOW = Integer.MIN_VALUE;

    /** The brightness sentinel: the text takes whatever light the world gives it at that point. */
    public static final int WORLD_BRIGHTNESS = -1;

    /** The shadow sentinel: the display casts the shadow vanilla gives it, which is none. */
    public static final float VANILLA_SHADOW = -1.0f;

    /** The gap between two stacked lines when a caller names none. The first line sits highest. */
    public static final float DEFAULT_LINE_GAP = 0.28f;

    /** The brightest a light override may be. */
    private static final int MAX_LIGHT = 15;

    public HologramAppearance {
        Objects.requireNonNull(billboard, "billboard");
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(translation, "translation");
        Objects.requireNonNull(scale, "scale");
        if (textOpacity < 0 || textOpacity > 255) {
            throw new IllegalArgumentException("textOpacity must be 0-255, was " + textOpacity);
        }
        if (lineWidth < 0) {
            throw new IllegalArgumentException("lineWidth must be >= 0, was " + lineWidth);
        }
        if (viewRange < 0f) {
            throw new IllegalArgumentException("viewRange must be >= 0, was " + viewRange);
        }
        checkLight(brightnessBlock, "brightnessBlock");
        checkLight(brightnessSky, "brightnessSky");
        if (lineGap < 0f) {
            throw new IllegalArgumentException("lineGap must be >= 0, was " + lineGap);
        }
        // Defensive copies: Vector3f is mutable, so a caller cannot reach in and change a stored appearance.
        translation = new Vector3f(translation);
        scale = new Vector3f(scale);
    }

    /**
     * The plain look: text that always faces the viewer, no background panel, no shadow, hidden behind
     * blocks, centred, wrapped at 200 pixels, default view range, no offset, unit scale, fully opaque, no
     * outline, the world's own light, no ground shadow and the ordinary line gap.
     */
    public static HologramAppearance defaults() {
        return new HologramAppearance(
                Display.Billboard.CENTER,
                NO_BACKGROUND,
                false,
                false,
                TextDisplay.TextAlignment.CENTER,
                200,
                1.0f,
                new Vector3f(0f, 0f, 0f),
                new Vector3f(1f, 1f, 1f),
                FULL_OPACITY,
                NO_GLOW,
                WORLD_BRIGHTNESS,
                WORLD_BRIGHTNESS,
                VANILLA_SHADOW,
                VANILLA_SHADOW,
                DEFAULT_LINE_GAP);
    }

    @Override
    public Vector3f translation() {
        // Hand back a copy so the stored value stays immutable.
        return new Vector3f(translation);
    }

    @Override
    public Vector3f scale() {
        return new Vector3f(scale);
    }

    /** The background as packed ARGB, which is what the metadata field takes. */
    public int backgroundArgb() {
        return background.asARGB();
    }

    /** Whether an outline was asked for at all. */
    public boolean hasGlow() {
        return glowArgb != NO_GLOW;
    }

    /** Whether either light override was set, which is what decides if a brightness value is written. */
    public boolean hasBrightness() {
        return brightnessBlock != WORLD_BRIGHTNESS || brightnessSky != WORLD_BRIGHTNESS;
    }

    /** Whether a ground shadow was asked for. */
    public boolean hasShadow() {
        return shadowRadius != VANILLA_SHADOW || shadowStrength != VANILLA_SHADOW;
    }

    /**
     * The two light overrides packed the way the display's own field takes them.
     *
     * <p>An override of one and not the other is not a thing the field can carry, so the one that was left
     * alone is written as full dark rather than as the sentinel, which would read as light level 15.
     */
    public int brightnessPacked() {
        int block = brightnessBlock == WORLD_BRIGHTNESS ? 0 : brightnessBlock;
        int sky = brightnessSky == WORLD_BRIGHTNESS ? 0 : brightnessSky;
        return block << 4 | sky << 20;
    }

    /** The same look with another billboard mode. */
    public HologramAppearance withBillboard(Display.Billboard value) {
        return toBuilder().billboard(value).build();
    }

    /** The same look with another background panel. */
    public HologramAppearance withBackground(Color value) {
        return toBuilder().background(value).build();
    }

    /** The same look with the drop shadow switched on or off. */
    public HologramAppearance withTextShadow(boolean value) {
        return toBuilder().textShadow(value).build();
    }

    /** The same look with drawing through blocks switched on or off. */
    public HologramAppearance withSeeThrough(boolean value) {
        return toBuilder().seeThrough(value).build();
    }

    /** The same look with another justification. */
    public HologramAppearance withAlignment(TextDisplay.TextAlignment value) {
        return toBuilder().alignment(value).build();
    }

    /** The same look with another wrap width. */
    public HologramAppearance withLineWidth(int value) {
        return toBuilder().lineWidth(value).build();
    }

    /** The same look with another view-range multiplier. */
    public HologramAppearance withViewRange(float value) {
        return toBuilder().viewRange(value).build();
    }

    /** The same look offset from the anchor by {@code (x, y, z)}. */
    public HologramAppearance withTranslation(float x, float y, float z) {
        return toBuilder().translation(new Vector3f(x, y, z)).build();
    }

    /** The same look scaled uniformly. */
    public HologramAppearance withScale(float factor) {
        return withScale(factor, factor, factor);
    }

    /** The same look scaled per axis. */
    public HologramAppearance withScale(float x, float y, float z) {
        return toBuilder().scale(new Vector3f(x, y, z)).build();
    }

    /** The same look at another glyph opacity, 0 to 255. */
    public HologramAppearance withTextOpacity(int value) {
        return toBuilder().textOpacity(value).build();
    }

    /** The same look with an outline in {@code value}, or {@link #NO_GLOW} for none. */
    public HologramAppearance withGlow(int value) {
        return toBuilder().glowArgb(value).build();
    }

    /** The same look with an outline in {@code value}. */
    public HologramAppearance withGlow(Color value) {
        return withGlow(Objects.requireNonNull(value, "value").asARGB());
    }

    /** The same look with the two light overrides, either of them {@link #WORLD_BRIGHTNESS}. */
    public HologramAppearance withBrightness(int block, int sky) {
        return toBuilder().brightnessBlock(block).brightnessSky(sky).build();
    }

    /** The same look with a ground shadow, either value {@link #VANILLA_SHADOW}. */
    public HologramAppearance withShadow(float radius, float strength) {
        return toBuilder().shadowRadius(radius).shadowStrength(strength).build();
    }

    /** The same look with another gap between stacked lines. */
    public HologramAppearance withLineGap(float value) {
        return toBuilder().lineGap(value).build();
    }

    /** This look, opened up so one property can be changed without naming the other fifteen. */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * A mutable copy of one look.
     *
     * <p>Sixteen properties is past the point where a wither can spell the whole constructor: the version
     * that did was ten copies of a ten-argument call, and every new property meant editing all of them. Each
     * wither is one line now, and the list of fields is written once.
     */
    public static final class Builder {

        private Display.Billboard billboard;
        private Color background;
        private boolean textShadow;
        private boolean seeThrough;
        private TextDisplay.TextAlignment alignment;
        private int lineWidth;
        private float viewRange;
        private Vector3f translation;
        private Vector3f scale;
        private int textOpacity;
        private int glowArgb;
        private int brightnessBlock;
        private int brightnessSky;
        private float shadowRadius;
        private float shadowStrength;
        private float lineGap;

        private Builder(HologramAppearance from) {
            this.billboard = from.billboard();
            this.background = from.background();
            this.textShadow = from.textShadow();
            this.seeThrough = from.seeThrough();
            this.alignment = from.alignment();
            this.lineWidth = from.lineWidth();
            this.viewRange = from.viewRange();
            this.translation = from.translation();
            this.scale = from.scale();
            this.textOpacity = from.textOpacity();
            this.glowArgb = from.glowArgb();
            this.brightnessBlock = from.brightnessBlock();
            this.brightnessSky = from.brightnessSky();
            this.shadowRadius = from.shadowRadius();
            this.shadowStrength = from.shadowStrength();
            this.lineGap = from.lineGap();
        }

        public Builder billboard(Display.Billboard value) {
            this.billboard = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder background(Color value) {
            this.background = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder textShadow(boolean value) {
            this.textShadow = value;
            return this;
        }

        public Builder seeThrough(boolean value) {
            this.seeThrough = value;
            return this;
        }

        public Builder alignment(TextDisplay.TextAlignment value) {
            this.alignment = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder lineWidth(int value) {
            this.lineWidth = value;
            return this;
        }

        public Builder viewRange(float value) {
            this.viewRange = value;
            return this;
        }

        public Builder translation(Vector3f value) {
            this.translation = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder scale(Vector3f value) {
            this.scale = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder textOpacity(int value) {
            this.textOpacity = value;
            return this;
        }

        public Builder glowArgb(int value) {
            this.glowArgb = value;
            return this;
        }

        public Builder brightnessBlock(int value) {
            this.brightnessBlock = value;
            return this;
        }

        public Builder brightnessSky(int value) {
            this.brightnessSky = value;
            return this;
        }

        public Builder shadowRadius(float value) {
            this.shadowRadius = value;
            return this;
        }

        public Builder shadowStrength(float value) {
            this.shadowStrength = value;
            return this;
        }

        public Builder lineGap(float value) {
            this.lineGap = value;
            return this;
        }

        /** The look these fields describe. Every rule the record enforces is enforced here too. */
        public HologramAppearance build() {
            return new HologramAppearance(
                    billboard,
                    background,
                    textShadow,
                    seeThrough,
                    alignment,
                    lineWidth,
                    viewRange,
                    translation,
                    scale,
                    textOpacity,
                    glowArgb,
                    brightnessBlock,
                    brightnessSky,
                    shadowRadius,
                    shadowStrength,
                    lineGap);
        }
    }

    private static void checkLight(int value, String name) {
        if (value != WORLD_BRIGHTNESS && (value < 0 || value > MAX_LIGHT)) {
            throw new IllegalArgumentException(name + " must be 0-" + MAX_LIGHT + " or unset, was " + value);
        }
    }
}
