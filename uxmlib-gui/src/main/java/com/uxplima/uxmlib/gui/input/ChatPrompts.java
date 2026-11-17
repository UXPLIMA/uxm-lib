package com.uxplima.uxmlib.gui.input;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.jspecify.annotations.NullMarked;

/**
 * The chat lines awaited as input, one per player, and whether a raising of a chat line is one to hide from every
 * other listener. A pending prompt carries a deadline, so an unanswered prompt expires instead of swallowing a chat
 * line minutes later, and a second prompt for the same player replaces the first.
 */
@NullMarked
final class ChatPrompts {

    private record Pending(Consumer<String> line, long deadline) {}

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final ChatEcho echo = new ChatEcho();

    /** Hand {@code player}'s next chat line before {@code deadline} to {@code line}. */
    void await(UUID player, Consumer<String> line, long deadline) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(line, "line");
        echo.forget(player);
        pending.put(player, new Pending(line, deadline));
    }

    /**
     * {@code player} chatted {@code text} at {@code now}: whether this raising must be hidden. An awaited line is
     * handed on and hidden; the second raising of a line captured from the first is hidden too. {@code first} says
     * whether a second raising of this line will follow.
     */
    boolean take(UUID player, String text, long now, boolean first) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(text, "text");
        Pending awaited = pending.remove(player);
        if (awaited == null) {
            return echo.take(player, now);
        }
        if (now > awaited.deadline()) {
            return false; // the prompt expired: the line posts as a normal chat message rather than be swallowed
        }
        if (first) {
            echo.mark(player, now);
        }
        awaited.line().accept(text);
        return true;
    }

    /** Drop {@code player}'s pending prompt without handing anything on. */
    void cancel(UUID player) {
        Objects.requireNonNull(player, "player");
        pending.remove(player);
        echo.forget(player);
    }

    void clear() {
        pending.clear();
        echo.clear();
    }
}
