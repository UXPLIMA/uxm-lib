package com.uxplima.uxmlib.storage.repository;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

/**
 * A thin read-through cache over Caffeine. Build one with {@link #builder()}; {@link #get(Object,
 * Function)} returns the cached value or computes, stores, and returns it. Keys and values are non-null
 * (a loader must never return null). This is deliberately minimal: reach for Caffeine directly when you
 * need its full surface.
 */
public final class Cache<K, V> {

    private final com.github.benmanes.caffeine.cache.Cache<K, V> delegate;

    private Cache(com.github.benmanes.caffeine.cache.Cache<K, V> delegate) {
        this.delegate = delegate;
    }

    /** Start configuring a cache. */
    public static Builder builder() {
        return new Builder();
    }

    /** The cached value for {@code key}, or empty when absent. */
    public Optional<V> getIfPresent(K key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(delegate.getIfPresent(key));
    }

    /** The cached value for {@code key}, computing and storing it with {@code loader} when absent. */
    public V get(K key, Function<K, V> loader) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(loader, "loader");
        // Caffeine's get is unannotated (NullAway sees @Nullable), but our loader is non-null under
        // @NullMarked, so the value is always present; assert it to satisfy the non-null return.
        return Objects.requireNonNull(delegate.get(key, loader), "loader returned null");
    }

    /** Store a value. */
    public void put(K key, V value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        delegate.put(key, value);
    }

    /** Drop the entry for {@code key}. */
    public void invalidate(K key) {
        Objects.requireNonNull(key, "key");
        delegate.invalidate(key);
    }

    /** Drop every entry. */
    public void invalidateAll() {
        delegate.invalidateAll();
    }

    /** The approximate number of cached entries. */
    public long estimatedSize() {
        return delegate.estimatedSize();
    }

    /**
     * Run any pending maintenance (size/TTL eviction). Caffeine evicts lazily on access; call this to make
     * eviction observable immediately: chiefly useful in tests that drive a fake {@link Ticker}.
     */
    public void cleanUp() {
        delegate.cleanUp();
    }

    /** Fluent builder for a {@link Cache}. */
    public static final class Builder {
        private long maximumSize = 10_000L;
        private @org.jspecify.annotations.Nullable Duration expireAfterWrite;
        private @org.jspecify.annotations.Nullable Duration expireAfterAccess;
        private @org.jspecify.annotations.Nullable Ticker ticker;

        private Builder() {}

        /** The maximum number of entries before the cache starts evicting. */
        public Builder maximumSize(long size) {
            if (size < 1) {
                throw new IllegalArgumentException("maximumSize must be >= 1");
            }
            this.maximumSize = size;
            return this;
        }

        /** Evict an entry this long after it was written. */
        public Builder expireAfterWrite(Duration duration) {
            this.expireAfterWrite = Objects.requireNonNull(duration, "duration");
            return this;
        }

        /**
         * Evict an entry this long after it was last read or written: the TTL-after-quit tier, so a key
         * that goes idle (a player who left) is dropped without being held forever.
         */
        public Builder expireAfterAccess(Duration duration) {
            this.expireAfterAccess = Objects.requireNonNull(duration, "duration");
            return this;
        }

        /**
         * The time source for the expiry windows. Defaults to the system clock; supply a fake one to make
         * TTL eviction deterministic in a test.
         */
        public Builder ticker(Ticker ticker) {
            this.ticker = Objects.requireNonNull(ticker, "ticker");
            return this;
        }

        /** Build the cache. */
        public <K, V> Cache<K, V> build() {
            Caffeine<Object, Object> caffeine = Caffeine.newBuilder().maximumSize(maximumSize);
            Duration write = expireAfterWrite;
            if (write != null) {
                caffeine = caffeine.expireAfterWrite(write);
            }
            Duration access = expireAfterAccess;
            if (access != null) {
                caffeine = caffeine.expireAfterAccess(access);
            }
            Ticker clock = ticker;
            if (clock != null) {
                caffeine = caffeine.ticker(clock);
            }
            return new Cache<>(caffeine.build());
        }
    }
}
