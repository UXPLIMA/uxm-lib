package com.uxplima.uxmlib.hud.nametag;

import java.util.UUID;

/**
 * Where a composed name is actually written. {@link ScoreboardNametagSink} is the shipped implementation and
 * writes it to a scoreboard team; a consumer that renders names some other way: per-viewer packets, a
 * tablist-only display, implements this instead and keeps {@link NametagRegistry}'s composition rules.
 *
 * <p>Every method runs on whichever thread the registry was told the display belongs to, so an implementation
 * does not schedule for itself.
 */
public interface NametagSink {

    /** Show {@code name} for the player known to the display as {@code entry}. */
    void apply(UUID player, String entry, ComposedNametag name);

    /** Drop whatever was shown for {@code entry}, leaving the player as the server found them. */
    void clear(UUID player, String entry);

    /** Drop everything this sink created, for a plugin shutting down. */
    void clearAll();
}
