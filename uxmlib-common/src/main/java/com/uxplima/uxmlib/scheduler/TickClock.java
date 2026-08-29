package com.uxplima.uxmlib.scheduler;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A monotonic server-uptime tick counter. Once {@link #start} schedules its global timer the count climbs by
 * one each tick, giving animations and cooldowns a cheap shared clock that never depends on wall time and so
 * cannot jump backwards over an NTP correction.
 *
 * <p>The count is an instance field (no static mutable state); a plugin holds one clock and reads it from any
 * thread via an {@link AtomicLong}. Advancing runs on the global region timer, so the value the main thread
 * reads is consistent. {@link #start} is idempotent: a second call is ignored, keeping a single timer.
 */
public final class TickClock {

    private final Scheduler scheduler;
    private final AtomicLong ticks = new AtomicLong();
    private @org.jspecify.annotations.Nullable TaskHandle handle;

    /** Build a clock that will drive itself off {@code scheduler}'s global timer once {@link #start} is called. */
    public TickClock(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Begin advancing the count once per tick on the global region. A no-op if already started. */
    public synchronized void start() {
        if (handle != null) {
            return;
        }
        handle = scheduler.globalTimer(Duration.ZERO, Ticks.ONE_TICK, t -> ticks.incrementAndGet());
    }

    /** Stop advancing the count. The current value is retained; {@link #start} may be called again later. */
    public synchronized void stop() {
        if (handle != null) {
            handle.cancel();
            handle = null;
        }
    }

    /** The number of ticks elapsed since {@link #start}, monotonic and never negative. */
    public long ticks() {
        return ticks.get();
    }
}
