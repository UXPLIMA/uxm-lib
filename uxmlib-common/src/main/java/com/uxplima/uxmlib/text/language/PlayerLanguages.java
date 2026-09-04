package com.uxplima.uxmlib.text.language;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a plugin keeps its own answer to "what does this player read".
 *
 * <p>Two values per player, and they are not the same thing. The <b>choice</b> is what the player asked for
 * and it is theirs until they change it. The <b>last client language</b> is what their client last said,
 * remembered because a client says it too late: at the moment a player joins, the settings packet has not
 * arrived and the server would draw its first message in the wrong language. A remembered value makes every
 * join after the first one right.
 *
 * <p>An implementation is read on the thread that draws a message, so it answers from memory and never from
 * a disk or a database on that path.
 */
public interface PlayerLanguages {

    /** The language this player chose, or empty when they have not chosen one. */
    Optional<Locale> chosen(UUID player);

    /** Record this player's choice. */
    void choose(UUID player, Locale locale);

    /** Drop this player's choice, so the client language or the default answers again. */
    void forget(UUID player);

    /** The language this player's client last reported, or empty when it has never reported one. */
    Optional<Locale> lastClient(UUID player);

    /** Record what this player's client reports, so the next join starts in the right language. */
    void rememberClient(UUID player, Locale locale);

    /** A store that lives for as long as the server runs. It is the seam's test double and its floor. */
    static PlayerLanguages inMemory() {
        return new InMemory();
    }

    /** Two maps and nothing else: correct while the server runs, forgotten when it stops. */
    final class InMemory implements PlayerLanguages {

        private final Map<UUID, Locale> choices = new ConcurrentHashMap<>();

        private final Map<UUID, Locale> clients = new ConcurrentHashMap<>();

        @Override
        public Optional<Locale> chosen(UUID player) {
            Objects.requireNonNull(player, "player");
            return Optional.ofNullable(choices.get(player));
        }

        @Override
        public void choose(UUID player, Locale locale) {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(locale, "locale");
            choices.put(player, locale);
        }

        @Override
        public void forget(UUID player) {
            Objects.requireNonNull(player, "player");
            choices.remove(player);
        }

        @Override
        public Optional<Locale> lastClient(UUID player) {
            Objects.requireNonNull(player, "player");
            return Optional.ofNullable(clients.get(player));
        }

        @Override
        public void rememberClient(UUID player, Locale locale) {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(locale, "locale");
            clients.put(player, locale);
        }
    }
}
