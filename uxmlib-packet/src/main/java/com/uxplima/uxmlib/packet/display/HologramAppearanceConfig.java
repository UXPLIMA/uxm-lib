package com.uxplima.uxmlib.packet.display;

import java.util.Locale;
import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import org.spongepowered.configurate.ConfigurationNode;

/**
 * Reads the whole appearance of a hologram out of one HOCON block.
 *
 * <p>The canon says every property in the appearance table is a key an operator can set, and that a plugin
 * that writes its own reader for them is the defect the rule was written for. This is the reader, once, so
 * that every plugin in the estate spells the same words in the same file.
 *
 * <pre>{@code
 * appearance {
 *   billboard        = "center"       # center, vertical, horizontal, fixed
 *   alignment        = "center"       # center, left, right
 *   text-shadow      = false
 *   see-through      = false
 *   background       = "#00000000"    # #rrggbb or #aarrggbb, "none" for no panel
 *   text-opacity     = 255
 *   glow             = "none"         # #rrggbb, or "none" for no outline
 *   brightness-block = -1             # 0 to 15, -1 for the world's own light
 *   brightness-sky   = -1
 *   line-width       = 200
 *   line-gap         = 0.28
 *   view-range       = 1.0
 *   shadow-radius    = -1             # -1 for the vanilla shadow, which is none
 *   shadow-strength  = -1
 *   scale            = 1.0            # or scale { x = 1, y = 1, z = 1 }
 *   offset { x = 0, y = 0, z = 0 }
 * }
 * }</pre>
 *
 * <p>Every key is optional and an absent one keeps the vanilla value. A key an operator mistyped is loud:
 * a colour or an enum that names nothing throws with the word in the message, because a hologram that
 * silently ignored half its file is a hologram whose operator edits it again and again.
 */
public final class HologramAppearanceConfig {

    /** What an operator writes for a colour that is not there. */
    private static final String NONE = "none";

    private HologramAppearanceConfig() {}

    /**
     * The look this node describes, starting from {@code defaults()}.
     *
     * <p>An absent or empty node is the plain look, which is what a crate that wrote only lines gets.
     *
     * @throws IllegalArgumentException when a value names no colour, no billboard or no alignment
     */
    public static HologramAppearance load(ConfigurationNode node) {
        return load(node, HologramAppearance.defaults());
    }

    /** The same, over a look a caller already has, so a plugin can ship its own starting point. */
    public static HologramAppearance load(ConfigurationNode node, HologramAppearance from) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(from, "from");
        if (node.virtual() || node.empty()) {
            return from;
        }
        HologramAppearance.Builder look = from.toBuilder();
        look.billboard(
                billboard(node.node("billboard").getString(from.billboard().name())));
        look.alignment(
                alignment(node.node("alignment").getString(from.alignment().name())));
        look.textShadow(node.node("text-shadow").getBoolean(from.textShadow()));
        look.seeThrough(node.node("see-through").getBoolean(from.seeThrough()));
        look.textOpacity(node.node("text-opacity").getInt(from.textOpacity()));
        look.lineWidth(node.node("line-width").getInt(from.lineWidth()));
        look.lineGap((float) node.node("line-gap").getDouble(from.lineGap()));
        look.viewRange((float) node.node("view-range").getDouble(from.viewRange()));
        look.brightnessBlock(node.node("brightness-block").getInt(from.brightnessBlock()));
        look.brightnessSky(node.node("brightness-sky").getInt(from.brightnessSky()));
        look.shadowRadius((float) node.node("shadow-radius").getDouble(from.shadowRadius()));
        look.shadowStrength((float) node.node("shadow-strength").getDouble(from.shadowStrength()));
        colours(node, from, look);
        transform(node, from, look);
        return look.build();
    }

    private static void colours(ConfigurationNode node, HologramAppearance from, HologramAppearance.Builder look) {
        String background = node.node("background").getString();
        if (background != null) {
            look.background(colour(background, "background").orElse(HologramAppearance.NO_BACKGROUND));
        }
        String glow = node.node("glow").getString();
        if (glow != null) {
            look.glowArgb(colour(glow, "glow").map(Color::asARGB).orElse(HologramAppearance.NO_GLOW));
        } else {
            look.glowArgb(from.glowArgb());
        }
    }

    private static void transform(ConfigurationNode node, HologramAppearance from, HologramAppearance.Builder look) {
        ConfigurationNode scale = node.node("scale");
        if (!scale.virtual()) {
            org.joml.Vector3f current = from.scale();
            look.scale(
                    scale.isMap()
                            ? new org.joml.Vector3f(
                                    (float) scale.node("x").getDouble(current.x()),
                                    (float) scale.node("y").getDouble(current.y()),
                                    (float) scale.node("z").getDouble(current.z()))
                            : uniform((float) scale.getDouble(current.x())));
        }
        ConfigurationNode offset = node.node("offset");
        if (!offset.virtual()) {
            org.joml.Vector3f current = from.translation();
            look.translation(new org.joml.Vector3f(
                    (float) offset.node("x").getDouble(current.x()),
                    (float) offset.node("y").getDouble(current.y()),
                    (float) offset.node("z").getDouble(current.z())));
        }
    }

    private static org.joml.Vector3f uniform(float factor) {
        return new org.joml.Vector3f(factor, factor, factor);
    }

    /** A colour an operator wrote, or empty for the word that means "not there". */
    private static java.util.Optional<Color> colour(String written, String key) {
        String value = written.trim();
        if (value.isEmpty() || NONE.equalsIgnoreCase(value)) {
            return java.util.Optional.empty();
        }
        String digits = value.startsWith("#") ? value.substring(1) : value;
        try {
            if (digits.length() == 6) {
                return java.util.Optional.of(Color.fromARGB(0xff, hex(digits, 0), hex(digits, 2), hex(digits, 4)));
            }
            if (digits.length() == 8) {
                return java.util.Optional.of(
                        Color.fromARGB(hex(digits, 0), hex(digits, 2), hex(digits, 4), hex(digits, 6)));
            }
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException(key + " is not a colour: " + written, malformed);
        }
        throw new IllegalArgumentException(key + " is not a colour: " + written);
    }

    private static int hex(String digits, int at) {
        return Integer.parseInt(digits.substring(at, at + 2), 16);
    }

    private static Display.Billboard billboard(String written) {
        try {
            return Display.Billboard.valueOf(written.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("billboard names no mode: " + written, unknown);
        }
    }

    private static TextDisplay.TextAlignment alignment(String written) {
        try {
            return TextDisplay.TextAlignment.valueOf(written.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("alignment names no justification: " + written, unknown);
        }
    }
}
