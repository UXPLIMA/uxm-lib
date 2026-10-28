package com.uxplima.uxmlib.condition.currency;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    /** Long enough for a busy server to answer, short enough that a stuck tick does not hold a worker. */
    private static final Duration LIMIT = Duration.ofSeconds(5);

    private GlobalCall() {}

    /** The answer, or empty when the server did not produce one in time. */
    public static <T> Optional<T> on(Scheduler scheduler, Supplier<T> work) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(work, "work");

        // On Folia every region thread is a primary thread; only the global one may run this in place.
        if (Bukkit.isGlobalTickThread()) {
            return Optional.ofNullable(work.get());
        }

        CompletableFuture<T> answer = new CompletableFuture<>();
        scheduler.global(() -> {
            try {
                answer.complete(work.get());
            } catch (RuntimeException failure) {
                answer.completeExceptionally(failure);
            }
        });
        try {
            return Optional.ofNullable(answer.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (TimeoutException | ExecutionException failure) {
            // The caller reads an empty answer as "it did not happen", which is the safe way round for
            // money that belongs to somebody else.
            return Optional.empty();
        }
    }
}
