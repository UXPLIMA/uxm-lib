package com.uxplima.uxmlib.text.message;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.jspecify.annotations.Nullable;

/**
 * Reports a player whose language changed, for the caller to redraw what that player already holds or sees.
 *
 * <p>Nothing tells every plugin on a server that a player chose a language, so the answer is asked of
 * {@code languageOf} on a timer, on each player's own thread. The first answer for a player is remembered and
 * reports nothing, so a player who joins is not redrawn twice. A player who leaves is forgotten.
 */
public final class LanguageWatch {

    private final Function<Player, ?> languageOf;
    private final Consumer<Player> changed;
    private final Map<UUID, Object> languages = new ConcurrentHashMap<>();

    private LanguageWatch(Function<Player, ?> languageOf, Consumer<Player> changed) {
        this.languageOf = languageOf;
        this.changed = changed;
    }

    /**
     * Start watching.
     *
     * @param languageOf the language a player reads, compared with {@code equals}; {@code null} is no answer yet
     * @param every how often each online player is asked
     * @param online who is on the server, asked on the global thread
     * @param changed called on the player's own thread once per change
     * @return the timer, for the caller to cancel when it shuts down
     */
    public static TaskHandle follow(
            Scheduler scheduler,
            Function<Player, ?> languageOf,
            Duration every,
            Supplier<? extends Collection<? extends Player>> online,
            Consumer<Player> changed) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(every, "every");
        Objects.requireNonNull(online, "online");
        LanguageWatch watch = new LanguageWatch(
                Objects.requireNonNull(languageOf, "languageOf"), Objects.requireNonNull(changed, "changed"));
        return scheduler.globalTimer(every, every, handle -> {
            Set<UUID> here = new HashSet<>();
            for (Player player : online.get()) {
                here.add(player.getUniqueId());
                scheduler.entity(player, () -> watch.notice(player, watch.languageOf.apply(player)));
            }
            watch.languages.keySet().retainAll(here);
        });
    }

    private void notice(Player player, @Nullable Object language) {
        if (language == null) {
            return;
        }
        Object before = languages.put(player.getUniqueId(), language);
        if (before != null && !before.equals(language)) {
            changed.accept(player);
        }
    }
}
