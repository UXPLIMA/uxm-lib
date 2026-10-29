package com.uxplima.uxmlib.condition.currency;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.Bukkit;

import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * Run something on the thread the server ticks on, and wait for the answer.
 *
 * <p>{@link RegionCall} hops to the region that owns a player. This one is for work that belongs to no
 * player: a command that the console sends, or a plugin that answers a question and was written for one
 * thread. The plugin does its money work off the main threads, so it has to hop over and come back.
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
