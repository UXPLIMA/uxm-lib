package com.uxplima.uxmlib.redis;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * A warn sink that forwards at most one message per fixed window, so a flapping dependency cannot flood the
 * log. The first message always passes; afterwards a message is forwarded only once a full {@code windowMs}
 * has elapsed since the last one that was forwarded: the ones in between are dropped, not queued.
 *
 * <p>Thread-safe and lock-free: the "claim this window" step is a compare-and-set on the last-emitted
 * timestamp, so concurrent callers (e.g. publish-failure callbacks arriving together on an event loop) emit a
 * single message and the CAS losers drop theirs without blocking. The clock is injected so the windowing is
 * unit-testable deterministically without sleeping.
 */
final class RateLimitedWarner {

    private static final long NEVER_WARNED = Long.MIN_VALUE;

    private final Consumer<String> sink;
    private final long windowMs;
    private final LongSupplier clock;
    private final AtomicLong lastWarnAt = new AtomicLong(NEVER_WARNED);

    RateLimitedWarner(Consumer<String> sink, long windowMs, LongSupplier clock) {
        this.sink = Objects.requireNonNull(sink, "sink");
        if (windowMs <= 0) {
            throw new IllegalArgumentException("windowMs must be positive: " + windowMs);
        }
        this.windowMs = windowMs;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Forward {@code message} to the sink iff the current window is open; otherwise drop it. */
    void warn(String message) {
        Objects.requireNonNull(message, "message");
        long now = clock.getAsLong();
        long previous = lastWarnAt.get();
        // The first call always warns; afterwards only once a full window has elapsed. Comparing against
        // NEVER_WARNED explicitly (rather than via subtraction) keeps the first-warn check free of the signed
        // overflow that now - Long.MIN_VALUE would hit for a small injected test clock.
        boolean windowOpen = previous == NEVER_WARNED || now - previous >= windowMs;
        if (windowOpen && lastWarnAt.compareAndSet(previous, now)) {
            sink.accept(message);
        }
    }
}
