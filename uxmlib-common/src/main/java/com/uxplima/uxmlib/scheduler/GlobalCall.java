package com.uxplima.uxmlib.scheduler;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.Bukkit;

/**
 * Run something on the thread the server ticks on, and wait for the answer.
 *
 * <p>{@link RegionCall} hops to the region that owns a player. This one is for work that belongs to no
 * player: a command that the console sends, or a plugin that answers a question and was written for one
 * thread. Its wait follows the same rule as {@link RegionCall}: work that had not started when the wait
 * ran out never runs, and work that had started is waited for.
 *
 * <p>A caller that is already on that thread runs the work in place. Waiting for itself would be a
 * server that stops.
 */
public final class GlobalCall {

    private GlobalCall() {}

    /** The answer, or empty when the server did not produce one in time. */
    public static <T> Optional<T> on(Scheduler scheduler, Supplier<T> work) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(work, "work");

        // On Folia every region thread is a primary thread; only the global one may run this in place.
        if (Bukkit.isGlobalTickThread()) {
            return Optional.ofNullable(work.get());
        }

        return Hop.await(scheduler::global, work);
    }
}
