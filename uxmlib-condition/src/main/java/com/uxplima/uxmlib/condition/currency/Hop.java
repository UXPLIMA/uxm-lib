package com.uxplima.uxmlib.condition.currency;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Hand work to another thread and wait a bounded time for its answer, without ever losing one.
 *
 * <p>The work moves money, so "the caller gave up" and "the work ran" must never both be true. Whoever
 * claims the flag first wins: the work claims it when it starts, the caller claims it when its wait runs
 * out. A caller that loses the claim knows the work has started, and waits for its real outcome instead
 * of reporting that nothing happened while a payment goes through behind it.
 */
final class Hop {

    /** Long enough for a busy server to answer, short enough that a stuck tick does not hold a worker. */
    static final Duration LIMIT = Duration.ofSeconds(5);

    private Hop() {}

    static <T> Optional<T> await(Consumer<Runnable> lane, Supplier<T> work) {
        AtomicBoolean claimed = new AtomicBoolean();
        CompletableFuture<T> answer = new CompletableFuture<>();
        lane.accept(() -> {
            if (!claimed.compareAndSet(false, true)) {
                return;
            }
            try {
                answer.complete(work.get());
            } catch (RuntimeException failure) {
                answer.completeExceptionally(failure);
            }
        });
        try {
            return Optional.ofNullable(answer.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS));
        } catch (ExecutionException failure) {
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return claimed.compareAndSet(false, true) ? Optional.empty() : outcome(answer);
        } catch (TimeoutException late) {
            // A player who left takes their scheduled work with them, so the work may never start: the claim
            // then succeeds and the empty answer is true. If the work already started, its outcome is the truth.
            return claimed.compareAndSet(false, true) ? Optional.empty() : outcome(answer);
        }
    }

    private static <T> Optional<T> outcome(CompletableFuture<T> answer) {
        try {
            return Optional.ofNullable(answer.join());
        } catch (CompletionException failure) {
            return Optional.empty();
        }
    }
}
