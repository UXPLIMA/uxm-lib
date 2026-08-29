package com.uxplima.uxmlib.common.regex;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The single ReDoS-guarded regex execution primitive.
 *
 * <p>{@link #run(String, Supplier, Object)} executes a caller-supplied regex operation on the injected
 * {@link Executor} (typically a virtual-thread executor: never {@code new Thread}) under a per-call
 * timeout. An operation that exceeds the budget is abandoned (the runaway match is actually stopped via
 * {@link #interruptible(CharSequence)}, because the regex engine ignores {@code Future#cancel} alone during
 * backtracking), the caller's {@code fallback} is returned, and a throttled message is handed to the
 * {@code warn} sink with the operation id. An operation that throws also yields the fallback (reported).
 *
 * <p>Threading: the bounded {@code FutureTask#get(timeout)} wait blocks the calling thread for at most the
 * timeout, so invoke it off any latency-sensitive thread (a tick / region thread on a game server).
 */
public final class TimedRegex {

    private final Executor executor;
    private final long timeoutMs;
    private final Consumer<String> warn;
    // Per-id throttle for the overrun warning (id -> last-warned epoch milli).
    // Concurrent because matches with different ids may overrun in parallel.
    private final ConcurrentHashMap<String, Long> lastWarn = new ConcurrentHashMap<>();

    private static final long WARN_THROTTLE_MS = 60_000L;

    /**
     * @param executor the pool the timed match runs on (a virtual-thread executor is the intended fit)
     * @param timeout the per-call match budget; must be positive
     * @param warn the sink for a throttled overrun/failure message (e.g. {@code logger::warn})
     */
    public TimedRegex(Executor executor, Duration timeout, Consumer<String> warn) {
        this.executor = Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timed-regex timeout must be positive, got: " + timeout);
        }
        this.timeoutMs = timeout.toMillis();
        this.warn = Objects.requireNonNull(warn, "warn");
    }

    /**
     * Wraps {@code input} so the regex engine's per-character reads abort when the timed task is interrupted.
     * Callers MUST match against this view (not the raw string) for the timeout to actually stop a
     * backtracking pattern.
     */
    public static CharSequence interruptible(CharSequence input) {
        return new InterruptibleCharSequence(input);
    }

    /**
     * Runs {@code regexOp} under the timeout; on overrun or failure returns {@code fallback} and hands a
     * throttled message keyed by {@code id} to the warn sink.
     */
    public <T> T run(String id, Supplier<T> regexOp, T fallback) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(regexOp, "regexOp");
        FutureTask<T> task = new FutureTask<>(regexOp::get);
        executor.execute(task);
        try {
            return task.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException overrun) {
            task.cancel(true); // interrupt -> InterruptibleCharSequence aborts the backtracking
            warnThrottled(id, "exceeded the " + timeoutMs + "ms match budget");
            return fallback;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (ExecutionException failure) {
            warnThrottled(id, "match failed: " + failure.getMessage());
            return fallback;
        }
    }

    private void warnThrottled(String id, String reason) {
        long now = System.currentTimeMillis();
        Long previous = lastWarn.get(id);
        if (previous != null && now - previous < WARN_THROTTLE_MS) {
            return;
        }
        lastWarn.put(id, now);
        warn.accept("timed-regex '" + id + "' " + reason + "; abandoned (possible ReDoS pattern)");
    }
}
