package com.uxplima.uxmlib.gui.input;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NullMarked;

/**
 * The second raising of a chat line already captured as input. Paper raises a line as the legacy
 * {@code AsyncPlayerChatEvent} first whenever any plugin listens to it, and then as {@code AsyncChatEvent}; a line
 * captured from the first is hidden from the second as well, so no listener of either reads it.
 *
 * <p>A mark lasts a few seconds at most, so a line Paper never raised a second time cannot hide the player's next
 * one later.
 */
@NullMarked
final class ChatEcho {

    static final long WINDOW_MILLIS = 5_000L;

    private final Map<UUID, Long> marked = new ConcurrentHashMap<>();

    /** A line of {@code player}'s was captured at {@code now} from the first of its two raisings. */
    void mark(UUID player, long now) {
        marked.put(Objects.requireNonNull(player, "player"), now);
    }

    /** Whether this raising of {@code player}'s line is the second one of a captured line; the mark is used up. */
    boolean take(UUID player, long now) {
        Long at = marked.remove(Objects.requireNonNull(player, "player"));
        return at != null && now - at < WINDOW_MILLIS;
    }

    /** Forget {@code player}'s mark. */
    void forget(UUID player) {
        marked.remove(Objects.requireNonNull(player, "player"));
    }

    void clear() {
        marked.clear();
    }
}
