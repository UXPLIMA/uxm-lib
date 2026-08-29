package com.uxplima.uxmlib.hologram;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

import org.jspecify.annotations.Nullable;

/**
 * A bounded, time-expiring cache from a player name to its resolved base64 skin texture, for
 * {@link MojangSkinResolver}. A {@code null} value is a cached <em>negative</em> result (the name has no
 * profile), kept so an unknown name is not re-fetched on every call.
 *
 * <p>The library's shared Caffeine cache lives in {@code uxmlib-storage}; pulling that module in just for a
 * skin lookup would be a heavy, one-directional dependency, so this hand-rolled cache keeps the resolver
 * self-contained with the same size-and-TTL semantics. The clock is injected as a nanosecond
 * {@link LongSupplier} so expiry is testable without real time. Access is synchronised: lookups are rare
 * (network-bound) so the simple lock is cheaper than the eviction bookkeeping a lock-free map would need.
 */
final class SkinCache {

    private final int capacity;
    private final long ttlNanos;
    private final LongSupplier clockNanos;
    // Insertion-ordered, so the first key is always the oldest: that is the one evicted when over capacity.
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

    SkinCache(int capacity, Duration ttl, LongSupplier clockNanos) {
        Objects.requireNonNull(ttl, "ttl");
        this.clockNanos = Objects.requireNonNull(clockNanos, "clockNanos");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be > 0");
        }
        this.capacity = capacity;
        this.ttlNanos = ttl.toNanos();
    }

    /** Store a resolved texture for {@code name}. */
    synchronized void put(String name, String texture) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(texture, "texture");
        store(name, texture);
    }

    /** Record that {@code name} has no profile, so the negative result is not re-fetched until it expires. */
    synchronized void putAbsent(String name) {
        Objects.requireNonNull(name, "name");
        store(name, null);
    }

    private void store(String name, @Nullable String texture) {
        // Re-insert so a refreshed entry counts as the newest for eviction order.
        entries.remove(name);
        entries.put(name, new Entry(texture, clockNanos.getAsLong()));
        if (entries.size() > capacity) {
            Iterator<String> oldest = entries.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /** The cached texture for {@code name}, or empty when absent or expired (negative hits read as empty). */
    synchronized Optional<String> get(String name) {
        Objects.requireNonNull(name, "name");
        return lookup(name).orElse(Optional.empty());
    }

    /**
     * A three-state read: an outer empty means {@code name} is not cached (caller should fetch); an outer
     * present whose inner is empty is a cached negative (no profile); an outer present with an inner value is
     * the cached texture. Lets the resolver skip a network call for a known-absent name.
     */
    synchronized Optional<Optional<String>> lookup(String name) {
        Objects.requireNonNull(name, "name");
        Entry entry = entries.get(name);
        if (entry == null) {
            return Optional.empty();
        }
        if (clockNanos.getAsLong() - entry.storedAtNanos() >= ttlNanos) {
            entries.remove(name);
            return Optional.empty();
        }
        return Optional.of(Optional.ofNullable(entry.texture()));
    }

    private record Entry(@Nullable String texture, long storedAtNanos) {}
}
