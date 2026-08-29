package com.uxplima.uxmlib.nametag;

import java.util.Objects;

import org.joml.Vector3f;

/**
 * The visual configuration of a packet nametag: the knobs of a vanilla text {@code Display} that a viewer
 * sees, expressed as a pure immutable value so the renderer (and its tests) need no server. The NMS packet
 * builder behind {@link NametagPackets} maps these onto the display's data-watcher fields.
 *
 * @param billboard how the text faces the viewer
 * @param backgroundArgb the text-box background colour as packed ARGB; {@code 0} is fully transparent
 * @param textShadow whether the glyphs cast a drop shadow
 * @param seeThrough whether the text renders through solid geometry
 * @param alignment how multi-line text is justified
 * @param lineWidth the maximum line width in pixels before the client wraps the text
 * @param viewRange the view-range multiplier applied to the entity-tracking distance
 * @param translation the offset, relative to the mount, applied before billboarding
 * @param scale the per-axis scale of the text
 * @param interpolationDurationTicks how many ticks the client interpolates a transform change over
 * @param hideThroughBlocks whether the text fades when the line of sight is obscured by blocks
 * @param obscuredOpacity the text opacity (0-255) used while the line of sight is obscured
 */
public record Appearance(
        Billboard billboard,
        int backgroundArgb,
        boolean textShadow,
        boolean seeThrough,
        Alignment alignment,
        int lineWidth,
        float viewRange,
        Vector3f translation,
        Vector3f scale,
        int interpolationDurationTicks,
        boolean hideThroughBlocks,
        int obscuredOpacity) {

    /** Full opacity, used when the nametag is in clear line of sight. */
    public static final int FULL_OPACITY = 255;

    public Appearance {
        Objects.requireNonNull(billboard, "billboard");
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(translation, "translation");
        Objects.requireNonNull(scale, "scale");
        if (obscuredOpacity < 0 || obscuredOpacity > 255) {
            throw new IllegalArgumentException("obscuredOpacity must be 0-255, was " + obscuredOpacity);
        }
        if (lineWidth < 0) {
            throw new IllegalArgumentException("lineWidth must be >= 0, was " + lineWidth);
        }
        if (interpolationDurationTicks < 0) {
            throw new IllegalArgumentException(
                    "interpolationDurationTicks must be >= 0, was " + interpolationDurationTicks);
        }
        // Defensive copies: Vector3f is mutable, so callers cannot reach in and change a stored appearance.
        translation = new Vector3f(translation);
        scale = new Vector3f(scale);
    }

    /**
     * The conventional out-of-the-box look: a centred, transparent, shadowless, see-through-disabled nametag
     * with centred text, a 200-pixel wrap width, default view range, no offset, unit scale, no interpolation,
     * line-of-sight fade disabled, and a 64/255 obscured opacity.
     */
    public static Appearance defaults() {
        return new Appearance(
                Billboard.CENTER,
                0,
                false,
                false,
                Alignment.CENTER,
                200,
                1.0f,
                new Vector3f(0f, 0f, 0f),
                new Vector3f(1f, 1f, 1f),
                0,
                false,
                64);
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
}
