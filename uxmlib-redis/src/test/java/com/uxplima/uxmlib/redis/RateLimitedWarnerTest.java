package com.uxplima.uxmlib.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * Drives the publish-warn throttle deterministically with an injected clock: no Redis, no sleeping. Proves the
 * one-per-window guarantee that keeps a flapping Redis from flooding the log.
 */
@org.jspecify.annotations.NullUnmarked
class RateLimitedWarnerTest {

    private static final long WINDOW_MS = 60_000L;

    @Test
    void a_flood_within_one_window_warns_exactly_once() {
        List<String> emitted = new CopyOnWriteArrayList<>();
        AtomicLong now = new AtomicLong(1_000L);
        RateLimitedWarner warner = new RateLimitedWarner(emitted::add, WINDOW_MS, now::get);

        for (int i = 0; i < 1_000; i++) {
            now.addAndGet(10L); // ~10s of flapping, well inside the 60s window
            warner.warn("publish failed #" + i);
        }

        assertThat(emitted).hasSize(1);
        assertThat(emitted.get(0)).isEqualTo("publish failed #0");
    }

    @Test
    void the_first_message_always_warns_even_at_a_zero_clock() {
        List<String> emitted = new CopyOnWriteArrayList<>();
        RateLimitedWarner warner = new RateLimitedWarner(emitted::add, WINDOW_MS, () -> 0L);

        warner.warn("first");

        assertThat(emitted).containsExactly("first");
    }

    @Test
    void a_message_warns_again_once_a_full_window_has_elapsed() {
        List<String> emitted = new CopyOnWriteArrayList<>();
        AtomicLong now = new AtomicLong(0L);
        RateLimitedWarner warner = new RateLimitedWarner(emitted::add, WINDOW_MS, now::get);

        warner.warn("a"); // emitted
        now.set(WINDOW_MS - 1);
        warner.warn("b"); // still inside the window: dropped
        now.set(WINDOW_MS);
        warner.warn("c"); // window boundary reached, emitted
        now.set(WINDOW_MS + 5);
        warner.warn("d"); // back inside the new window, dropped

        assertThat(emitted).containsExactly("a", "c");
    }

    @Test
    void concurrent_callers_in_one_window_emit_a_single_message() throws Exception {
        List<String> emitted = new CopyOnWriteArrayList<>();
        RateLimitedWarner warner = new RateLimitedWarner(emitted::add, WINDOW_MS, () -> 5_000L);

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                tasks.add(pool.submit(() -> {
                    awaitUninterruptibly(start);
                    for (int i = 0; i < 200; i++) {
                        warner.warn("concurrent");
                    }
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(emitted).hasSize(1);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while awaiting the start signal", interrupted);
        }
    }
}
