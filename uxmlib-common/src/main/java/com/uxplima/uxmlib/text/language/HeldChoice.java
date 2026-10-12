package com.uxplima.uxmlib.text.language;

import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * A language set for a player who was not online, waiting for them to join.
 *
 * <p>The shared choice lives on the player's own data, and the server can only write that while the player is
 * on it. So an operator's {@code set} or {@code reset} for an absent player is kept by the plugin until the
 * player arrives, and is applied then.
 *
 * @param locale the language to set, or {@code null} to clear the choice
 */
public record HeldChoice(@Nullable Locale locale) {

    /** The language to set, or empty when the hold is a reset. */
    public Optional<Locale> language() {
        return Optional.ofNullable(locale);
    }
}
