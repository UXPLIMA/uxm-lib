package com.uxplima.uxmlib.text.language;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The seam a network-wide language provider registers itself through, and the only thing a plugin needs to
 * know about one.
 *
 * <p>A plugin asks the service first and its own store second, so a server that runs a provider gives a
 * player one language across every plugin and every backend, and a server that runs none loses nothing but
 * that convenience. Nothing here is required: a plugin with no provider resolves a language on its own, which
 * is why no plugin of ours may ever declare a dependency on one.
 *
 * <p>An implementation answers for a player who is not online and who has never been seen, so
 * {@link #languageOf} returns empty rather than a guess.
 */
public interface LanguageService {

    /** The language this player chose, or empty when they have not chosen one. */
    Optional<Locale> languageOf(UUID player);

    /** Record this player's choice, for every plugin and every server the provider reaches. */
    void choose(UUID player, Locale locale);

    /** Drop this player's choice, so the client language or the server default answers again. */
    void forget(UUID player);

    /**
     * The language this player's client last reported, anywhere on the network, or empty when nobody has seen
     * it yet.
     *
     * <p>A client reports its language after it joins, so at the moment a player arrives on a server the live
     * value is still that server's own default. A provider that has seen the player on another server already
     * knows the answer, which is what makes the first message of a transfer right rather than nearly right.
     *
     * <p>A provider that keeps nothing may leave this alone: the plugin then falls back to what it remembers
     * on its own.
     */
    default Optional<Locale> lastClientLanguageOf(UUID player) {
        return Optional.empty();
    }

    /** Record what a client reported, for every server the provider reaches. */
    default void rememberClientLanguage(UUID player, Locale locale) {
        // A provider that keeps nothing keeps this too.
    }
}
