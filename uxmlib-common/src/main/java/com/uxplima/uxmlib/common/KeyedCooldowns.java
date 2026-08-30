package com.uxplima.uxmlib.common;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.jspecify.annotations.Nullable;

/**
 * How long each player still waits before they may do a thing again, one clock per player and key.
 *
 * <p>The key names what is being waited for: a kit, a warp, a category of cosmetic, an item id. A player
 * therefore holds one wait per key rather than one wait for the whole plugin, which is what lets a plugin
 * put a short wait on one action and a long one on another without a second store.
 *
 * <p>{@link #take} is the method most callers want. Asking whether a player may act and then arming the
 * next wait are one decision, and splitting them into a read and a write is a race: two clicks in the same
 * tick both read "no wait" and both act. {@code take} does the whole decision inside one atomic map
 * operation, so the second click loses.
 *
 * <p>The clock is monotonic, not the wall clock. An operator who moves the server clock, or a host that
 * corrects it by NTP, must not hand every waiting player a free action or freeze them for an hour. A test
 * supplies its own clock through {@link #withClock} and controls time exactly.
 *
 * <p>A wait of zero or less stores nothing, so a plugin whose cooldown is switched off in the file keeps an
 * empty map rather than one entry per player who has ever acted.
 *
 * @param <K> what a wait is held against
 */
public final class KeyedCooldowns<K> {

    private record Entry<K>(UUID owner, K key) {}

    private final Map<Entry<K>, Long> readyAtMillis = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    private KeyedCooldowns(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** A store on the monotonic clock, which is what a server wants. */
    public static <K> KeyedCooldowns<K> create() {
        return new KeyedCooldowns<>(KeyedCooldowns::monotonicMillis);
    }

    /** A store on a clock the caller controls, which is what a test wants. */
    public static <K> KeyedCooldowns<K> withClock(LongSupplier millis) {
        return new KeyedCooldowns<>(millis);
    }

    /**
     * Take the action if no wait is left, and arm the next wait in the same step.
     *
     * @return {@link Duration#ZERO} when the action was taken, otherwise how long is still to wait
     */
    public Duration take(UUID owner, K key, Duration wait) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(wait, "wait");

        long now = clock.getAsLong();
        long[] left = {0L};
        readyAtMillis.compute(new Entry<>(owner, key), (ignored, readyAt) -> {
            if (readyAt != null && readyAt > now) {
                left[0] = readyAt - now;
                return readyAt;
            }
            return armed(now, wait);
        });
        return Duration.ofMillis(left[0]);
    }

    /** How long {@code owner} still waits for {@code key}. Zero means they may act now. */
    public Duration remaining(UUID owner, K key) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(key, "key");
        Long readyAt = readyAtMillis.get(new Entry<>(owner, key));
        if (readyAt == null) {
            return Duration.ZERO;
        }
        long left = readyAt - clock.getAsLong();
        return left > 0L ? Duration.ofMillis(left) : Duration.ZERO;
    }

    /** Start a wait whatever is left of the last one, for a caller that has already made the decision. */
    public void start(UUID owner, K key, Duration wait) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(wait, "wait");
        Entry<K> entry = new Entry<>(owner, key);
        Long armed = armed(clock.getAsLong(), wait);
        if (armed == null) {
            readyAtMillis.remove(entry);
            return;
        }
        readyAtMillis.put(entry, armed);
    }

    /** End one wait now, which is what a caller does when the action it charged for did not happen. */
    public void clear(UUID owner, K key) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(key, "key");
        readyAtMillis.remove(new Entry<>(owner, key));
    }

    /** Drop every wait held against {@code owner}, which is what a plugin does when they leave. */
    public void forget(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        readyAtMillis.keySet().removeIf(entry -> entry.owner().equals(owner));
    }

    /**
     * Drop every wait that has run out.
     *
     * <p>Nothing is wrong with an expired entry: it answers zero like an absent one. What is wrong is the
     * size of the map on a server that never restarts, where it grows to every player who has ever acted
     * rather than the players who are waiting now. A plugin that forgets a player when they leave does not
     * need this; one that keeps waits across a session does.
     *
     * @return how many waits were dropped
     */
    public int purgeExpired() {
        long now = clock.getAsLong();
        int before = readyAtMillis.size();
        readyAtMillis.values().removeIf(readyAt -> readyAt <= now);
        return before - readyAtMillis.size();
    }

    /** How many waits are held, expired ones included. Written for the test that proves the purge works. */
    public int tracked() {
        return readyAtMillis.size();
    }

    /** When a wait of this length ends, or null when the wait is off and nothing should be stored. */
    private static @Nullable Long armed(long now, Duration wait) {
        if (wait.isZero() || wait.isNegative()) {
            return null;
        }
        return now + wait.toMillis();
    }

    /**
     * Milliseconds from a source that only ever moves forward.
     *
     * <p>The value means nothing on its own, and only the difference between two readings is used, which is
     * exactly what a wait is.
     */
    private static long monotonicMillis() {
        return System.nanoTime() / 1_000_000L;
    }
}
