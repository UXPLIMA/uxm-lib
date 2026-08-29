package com.uxplima.uxmlib.storage.sync;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.jspecify.annotations.Nullable;

/**
 * The opt-in lifecycle around a {@link RowSyncPoller}: {@link #start(Scheduler, Duration)} schedules
 * {@link RowSyncPoller#pollOnce()} on the library {@link Scheduler}'s <b>async</b> timer (off the main/region
 * threads, Folia-safe: never a {@code BukkitScheduler}) and {@link #stop()} cancels it. Row-sync runs only
 * while started, so a plugin that does not configure it pays nothing.
 *
 * <p>The poll runs on the async thread because it does JDBC I/O; the {@link RowSyncListener} it drives must
 * therefore hop to a region/global task itself before touching the Bukkit API. Starting twice without an
 * intervening {@link #stop()} is rejected so a stray second call cannot leak a second timer.
 *
 * @param <T> the mapped value type of the underlying poller
 */
public final class RowSyncService<T> {

    private final RowSyncPoller<T> poller;
    private @Nullable TaskHandle handle;

    /**
     * Wrap {@code poller} in a startable service.
     *
     * @throws NullPointerException if {@code poller} is {@code null}
     */
    public RowSyncService(RowSyncPoller<T> poller) {
        this.poller = Objects.requireNonNull(poller, "poller");
    }

    /**
     * Begin polling: schedule {@link RowSyncPoller#pollOnce()} on {@code scheduler}'s async timer to run after
     * one {@code period} and then every {@code period}.
     *
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code period} is zero or negative
     * @throws IllegalStateException if already running
     */
    public void start(Scheduler scheduler, Duration period) {
        Objects.requireNonNull(scheduler, "scheduler");
        start((p, tick) -> scheduler.asyncTimer(p, p, ignored -> tick.run()), period);
    }

    // Package-private seam: the lifecycle and guard logic are tested without Paper by supplying the timer
    // directly, since the storage module's tests run no MockBukkit to back a real Scheduler.
    void start(RepeatingTimer timer, Duration period) {
        Objects.requireNonNull(timer, "timer");
        Objects.requireNonNull(period, "period");
        if (period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("period must be positive (got " + period + ")");
        }
        if (handle != null) {
            throw new IllegalStateException("row-sync is already running");
        }
        this.handle = timer.schedule(period, poller::pollOnce);
    }

    /** Stop polling, cancelling the timer. Idempotent; safe to call when never started. */
    public void stop() {
        TaskHandle current = handle;
        if (current != null) {
            current.cancel();
            handle = null;
        }
    }

    /** Whether the poll timer is currently scheduled. */
    public boolean isRunning() {
        return handle != null;
    }

    /**
     * Schedules a repeating task that runs {@code tick} every {@code period}, returning a handle to cancel it.
     * The production binding forwards to {@link Scheduler#asyncTimer}; tests supply their own to drive the
     * lifecycle without Paper on the classpath.
     */
    @FunctionalInterface
    interface RepeatingTimer {
        TaskHandle schedule(Duration period, Runnable tick);
    }
}
