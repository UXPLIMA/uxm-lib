package com.uxplima.uxmlib.storage.sql;

import java.util.Objects;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/**
 * A non-throwing outcome of a fallible operation: either {@link #ok(Object) a value} or
 * {@link #error(String) a failure message}. It lets a storage operation report failure without forcing the
 * caller into a {@code try}/{@code catch} (or a {@code .join()} that unwraps a wrapped exception): branch on
 * {@link #isOk()} and read {@link #value()} or {@link #error()}.
 *
 * <p>This is deliberately small sugar that pairs with the async helpers; it is not a replacement for
 * {@link java.util.concurrent.CompletableFuture}, and it does not carry a throwable (only a message), so an
 * error path stays cheap and log-friendly.
 *
 * @param <T> the success value type
 */
public final class Result<T> {

    private final @Nullable T value;
    private final @Nullable String error;

    private Result(@Nullable T value, @Nullable String error) {
        this.value = value;
        this.error = error;
    }

    /** A successful result carrying {@code value}. */
    public static <T> Result<T> ok(T value) {
        Objects.requireNonNull(value, "value");
        return new Result<>(value, null);
    }

    /** A failed result carrying a human-readable {@code message}. */
    public static <T> Result<T> error(String message) {
        Objects.requireNonNull(message, "message");
        return new Result<>(null, message);
    }

    /** Whether this is a success. */
    public boolean isOk() {
        return error == null;
    }

    /** Whether this is a failure. */
    public boolean isError() {
        return error != null;
    }

    /**
     * The success value.
     *
     * @throws IllegalStateException if this is a failure
     */
    public T value() {
        if (value == null) {
            throw new IllegalStateException("result is an error: " + error);
        }
        return value;
    }

    /**
     * The failure message.
     *
     * @throws IllegalStateException if this is a success
     */
    public String error() {
        if (error == null) {
            throw new IllegalStateException("result is ok, not an error");
        }
        return error;
    }

    /** The success value, or {@code fallback} when this is a failure. */
    public T orElse(T fallback) {
        Objects.requireNonNull(fallback, "fallback");
        return value != null ? value : fallback;
    }

    /**
     * Transform the success value with {@code mapper}, leaving a failure untouched (it keeps its message and
     * is re-typed). Lets callers chain a conversion without unwrapping first.
     */
    public <R> Result<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        if (value == null) {
            return new Result<>(null, error);
        }
        return Result.ok(mapper.apply(value));
    }
}
