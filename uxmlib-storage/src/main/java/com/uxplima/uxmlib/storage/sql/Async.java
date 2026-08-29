package com.uxplima.uxmlib.storage.sql;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Runs blocking storage work on a caller-supplied {@link Executor} and hands back a
 * {@link CompletableFuture}, without {@code CompletableFuture.supplyAsync}, which the project bans
 * because it can silently fall back to the common pool. The future completes with the result, or
 * exceptionally if the work throws, so failures propagate instead of being swallowed.
 */
final class Async {

    private Async() {}

    static <T> CompletableFuture<T> on(Executor executor, Supplier<T> work) {
        java.util.Objects.requireNonNull(executor, "executor");
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                future.complete(work.get());
            } catch (RuntimeException failure) {
                future.completeExceptionally(failure);
            }
        });
        return future;
    }
}
