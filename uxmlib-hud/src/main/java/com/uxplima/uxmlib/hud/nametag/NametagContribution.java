package com.uxplima.uxmlib.hud.nametag;

import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.jspecify.annotations.Nullable;

/**
 * One plugin's claim on a player's name: a prefix, a suffix, a colour, or any combination of the three.
 *
 * <p>{@code priority} is a position, not a rank, and it decides layout only. Contributions compose in
 * ascending order, so the smaller the number the earlier the part sits in the finished name. It says nothing
 * about the colour: a name has one colour and it belongs to whichever plugin asked for it last, because
 * where a part sits and how recently a player asked for an effect are unrelated questions.
 * {@link #DEFAULT_PRIORITY} leaves room on both sides so a plugin can be placed before or after another
 * without every other plugin being edited. Read the number from your own config: settling which of two
 * plugins comes first is an operator's decision, not a compile-time one.
 *
 * <p>{@code plugin} identifies the contributor. It keys the contribution — a second contribution from the
 * same name replaces the first rather than stacking — it names the plugin in a logged colour clash, and it
 * is what a registry drops when that plugin disables. Use your plugin's own name.
 *
 * @param plugin the contributing plugin's name; never blank
 * @param priority the composition position; smaller is earlier. Layout only — it does not claim the colour
 * @param prefix the part that goes before the player's name, or {@code null} to contribute none
 * @param suffix the part that goes after it, or {@code null} to contribute none
 * @param color the colour of the name itself, or {@code null} to leave it to another contributor
 */
public record NametagContribution(
        String plugin,
        int priority,
        @Nullable Component prefix,
        @Nullable Component suffix,
        @Nullable NamedTextColor color) {

    /** The position a plugin takes when its config says nothing, with room to be placed on either side. */
    public static final int DEFAULT_PRIORITY = 100;

    public NametagContribution {
        Objects.requireNonNull(plugin, "plugin");
        if (plugin.isBlank()) {
            throw new IllegalArgumentException("plugin must not be blank");
        }
        if (prefix == null && suffix == null && color == null) {
            throw new IllegalArgumentException("a contribution must carry a prefix, a suffix or a colour");
        }
    }

    /** A prefix-only contribution at {@link #DEFAULT_PRIORITY}. */
    public static NametagContribution prefix(String plugin, Component prefix) {
        return prefix(plugin, DEFAULT_PRIORITY, prefix);
    }

    /** A prefix-only contribution at {@code priority}. */
    public static NametagContribution prefix(String plugin, int priority, Component prefix) {
        return new NametagContribution(plugin, priority, Objects.requireNonNull(prefix, "prefix"), null, null);
    }

    /** A suffix-only contribution at {@code priority}. */
    public static NametagContribution suffix(String plugin, int priority, Component suffix) {
        return new NametagContribution(plugin, priority, null, Objects.requireNonNull(suffix, "suffix"), null);
    }

    /**
     * A colour-only contribution at {@code priority}, for a plugin that only tints the name. Contributing one
     * takes the name's colour from whoever had it, so a plugin switching an effect on gets what it asked for.
     */
    public static NametagContribution color(String plugin, int priority, NamedTextColor color) {
        return new NametagContribution(plugin, priority, null, null, Objects.requireNonNull(color, "color"));
    }
}
