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
import org.bukkit.entity.Player;

import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * Run something on the region that owns a player, and wait for the answer.
 *
 * <p>A currency that lives in an inventory or in an experience bar can only be read and written on the
 * thread that owns the player, and on Folia that thread is whichever one owns their region at that
 * moment. The plugin does its money work off the main threads, so it has to hop over and come back.
 *
 * <p>The caller must not be a region thread itself. It never is: every path that moves money is already
 * off-thread, because it started with a database read. When the caller does happen to own the region
 * already, the work runs in place rather than waiting for itself.
 */
public final class RegionCall {

    /** Long enough for a busy server to answer, short enough that a stuck region does not hold a worker. */
    private static final Duration LIMIT = Duration.ofSeconds(5);

    private RegionCall() {}

    /** The answer, or empty when the region did not produce one in time. */
    public static <T> Optional<T> on(Scheduler scheduler, Player player, Supplier<T> work) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(work, "work");

        if (Bukkit.isOwnedByCurrentRegion(player)) {
            return Optional.ofNullable(work.get());
        }

        CompletableFuture<T> answer = new CompletableFuture<>();
        scheduler.entity(player, () -> {
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
            // A player who left between the check and the hop is the ordinary case, and it is not a
            // fault: the caller reads an empty answer as "not paid" and writes to the mailbox instead.
            return Optional.empty();
        }
    }
}
