package com.uxplima.uxmlib.hook;

import java.util.Objects;

import org.bukkit.entity.Player;

import me.clip.placeholderapi.PlaceholderAPI;

/**
 * A present-guarded bridge to PlaceholderAPI. {@link #apply(Player, String)} expands {@code %papi%}
 * placeholders when PlaceholderAPI is installed and leaves them as written when it is not, so callers can use
 * it unconditionally. The player's own name and id are filled either way. The {@code me.clip} classes are only touched inside the guarded
 * branch, so a server without PlaceholderAPI never resolves them.
 *
 * <p>Named after the plugin it bridges, not after the idea. {@code text.Placeholders} is the general one: a
 * typed, lazy placeholder layer over any subject, needing no plugin at all. This module depends on that one,
 * so both are in scope in the same file, and the specific of the two is the one that takes the specific name.
 */
public final class PlaceholderApi {

    /** The PlaceholderAPI plugin name, used for the presence guard. */
    public static final String PLUGIN = "PlaceholderAPI";

    private static final String OWN_NAME = "%player_name%";
    private static final String OWN_UUID = "%player_uuid%";

    private PlaceholderApi() {}

    /** Whether PlaceholderAPI is installed and enabled. */
    public static boolean isAvailable() {
        return Hooks.isPresent(PLUGIN);
    }

    /**
     * Expand placeholders in {@code text} for {@code player}. The player's own {@code %player_name%} and
     * {@code %player_uuid%} are filled here, with or without PlaceholderAPI and its Player expansion; every other
     * placeholder is left to PlaceholderAPI, and stays as written when it is absent.
     */
    public static String apply(Player player, String text) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(text, "text");
        // Most lines hold no placeholder, and such a line asks the player nothing.
        if (text.indexOf('%') < 0) {
            return text;
        }
        String own = text;
        if (own.contains(OWN_NAME)) {
            own = own.replace(OWN_NAME, player.getName());
        }
        if (own.contains(OWN_UUID)) {
            own = own.replace(OWN_UUID, player.getUniqueId().toString());
        }
        if (!isAvailable()) {
            return own;
        }
        return PlaceholderAPI.setPlaceholders(player, own);
    }
}
