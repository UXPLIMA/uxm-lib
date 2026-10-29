package com.uxplima.uxmlib.scheduler;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Run something on the region that owns a player, and wait for the answer.
 *
 * <p>A player's inventory, experience bar and the rest of the player can only be read and written on the
 * thread that owns them, and on Folia that thread is whichever one owns their region at that moment. Work
 * that runs off the server's threads, such as a payment that started with a database read or an item
 * handed over after one, hops over with this and comes back with the answer.
 *
 * <p>The wait is bounded, and it never loses an answer: work that had not started when the wait ran out
 * never runs, and work that had started is waited for, so "empty" always means "did not happen". A
 * player who leaves takes their scheduled work with them, which reads as empty too.
 *
 * <p>The caller should not be a region thread, which would wait for another region. When the caller
 * already owns the player's region, the work runs in place rather than waiting for itself.
 */
public final class RegionCall {

    private RegionCall() {}

    /** The answer, or empty when the region did not produce one in time. */
    public static <T> Optional<T> on(Scheduler scheduler, Player player, Supplier<T> work) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(work, "work");

        if (Bukkit.isOwnedByCurrentRegion(player)) {
            return Optional.ofNullable(work.get());
        }

        return Hop.await(task -> scheduler.entity(player, task), work);
    }
}
