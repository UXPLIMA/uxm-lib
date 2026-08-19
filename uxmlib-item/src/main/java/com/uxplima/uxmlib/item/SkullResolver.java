package com.uxplima.uxmlib.item;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * Resolves a player name or UUID to a head {@link SkullData.ByTexture} off the main thread, caching the
 * result so a head leaderboard does not re-fetch the same profile and a shared {@link RateLimiter} so it does
 * not burn the Mojang quota. The blocking lookup lives behind a {@link ProfileCompleter} seam dispatched on
 * {@link Scheduler#async}; callers get a {@link CompletableFuture} and never block a server thread.
 *
 * <p>The cache is a bounded, access-ordered LRU keyed by the lowercased name or UUID string, and it caches
 * <em>misses</em> too (negative caching): an unknown name resolves to an empty {@link Optional}. A negative
 * entry is remembered only for a short TTL (a few minutes by default), so a name that did not exist (or a
 * transient outage that returned no textures) is re-fetched once the TTL lapses rather than being pinned in
 * the cache until LRU pressure evicts it. Positive results never expire. Concurrent lookups of the same key
 * share one in-flight future (single-flight) rather than each firing a fetch.
 *
 * <p>When the limiter has no permit left a lookup resolves to empty without caching, so a later call (once
 * the window slides) can still succeed.
 */
public final class SkullResolver {

    private static final Duration DEFAULT_NEGATIVE_TTL = Duration.ofMinutes(5);

    private final Scheduler scheduler;
    private final ProfileCompleter completer;
    private final RateLimiter limiter;
    private final long negativeTtlMillis;
    private final LongSupplier clock;
    private final Map<String, CacheEntry> cache;

    /** A resolver backed by the live Paper completer, 1024 cached entries and 600 lookups/minute. */
    public static SkullResolver create(Scheduler scheduler) {
        return new SkullResolver(
                scheduler, new PaperProfileCompleter(), RateLimiter.of(600, Duration.ofMinutes(1)), 1024);
    }

    /**
     * @param scheduler the library scheduler; the completer runs on its async pool
     * @param completer the (blocking) name/uuid → texture lookup
     * @param limiter caps how fast lookups hit the completer
     * @param maxEntries the LRU cache bound (least-recently-used entries are evicted past it)
     */
    public SkullResolver(Scheduler scheduler, ProfileCompleter completer, RateLimiter limiter, int maxEntries) {
        this(scheduler, completer, limiter, maxEntries, DEFAULT_NEGATIVE_TTL, System::currentTimeMillis);
    }

    /**
     * @param scheduler the library scheduler; the completer runs on its async pool
     * @param completer the (blocking) name/uuid → texture lookup
     * @param limiter caps how fast lookups hit the completer
     * @param maxEntries the LRU cache bound (least-recently-used entries are evicted past it)
     * @param negativeTtl how long a remembered miss stays cached before it is re-fetched
     * @param clock the source of the current time (epoch millis); lets a test drive the TTL with a fake clock
     */
    public SkullResolver(
            Scheduler scheduler,
            ProfileCompleter completer,
            RateLimiter limiter,
            int maxEntries,
            Duration negativeTtl,
            LongSupplier clock) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.completer = Objects.requireNonNull(completer, "completer");
        this.limiter = Objects.requireNonNull(limiter, "limiter");
        Objects.requireNonNull(negativeTtl, "negativeTtl");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be >= 1");
        }
        if (negativeTtl.isNegative() || negativeTtl.isZero()) {
            throw new IllegalArgumentException("negativeTtl must be > 0");
        }
        this.negativeTtlMillis = negativeTtl.toMillis();
        this.cache = boundedLru(maxEntries);
    }

    /** Resolve the player named {@code name} to a head texture (empty if unknown or skinless). */
    public CompletableFuture<Optional<SkullData>> resolveName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return resolve(name.toLowerCase(java.util.Locale.ROOT));
    }

    /** Resolve the account with {@code uuid} to a head texture (empty if unknown or skinless). */
    public CompletableFuture<Optional<SkullData>> resolveUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return resolve(uuid.toString());
    }

    /**
     * Resolve {@code data} to a head texture: {@link SkullData.ByTexture} is already resolved and returned
     * as-is; a {@link SkullData.ByName} or {@link SkullData.ByUuid} is looked up. Lets a config string parsed
     * by {@link SkullData#parse(String)} flow through one call.
     */
    public CompletableFuture<Optional<SkullData>> resolve(SkullData data) {
        Objects.requireNonNull(data, "data");
        return switch (data) {
            case SkullData.ByTexture texture -> CompletableFuture.completedFuture(Optional.of(texture));
            case SkullData.ByName name -> resolveName(name.name());
            case SkullData.ByUuid id -> resolveUuid(id.uuid());
        };
    }

    private CompletableFuture<Optional<SkullData>> resolve(String key) {
        synchronized (cache) {
            CacheEntry cached = cache.get(key);
            if (cached != null && !isExpiredNegative(cached)) {
                return cached.future();
            }
            CompletableFuture<Optional<SkullData>> pending = new CompletableFuture<>();
            cache.put(key, new CacheEntry(pending, clock.getAsLong()));
            scheduler.async(() -> fetchInto(key, pending));
            return pending;
        }
    }

    // A negative entry expires once its remembered miss is older than the TTL, so the next lookup re-fetches.
    // A positive (present) result, and any still-in-flight future, never expire this way.
    private boolean isExpiredNegative(CacheEntry entry) {
        CompletableFuture<Optional<SkullData>> future = entry.future();
        if (!future.isDone() || future.isCompletedExceptionally()) {
            return false;
        }
        if (future.getNow(Optional.empty()).isPresent()) {
            return false;
        }
        return clock.getAsLong() - entry.storedAt() >= negativeTtlMillis;
    }

    // Runs on the async pool. On a rate-limit miss the entry is dropped so a later window can retry; any
    // other outcome (hit or negative) stays cached. The completer's own exceptions resolve to a dropped miss.
    private void fetchInto(String key, CompletableFuture<Optional<SkullData>> pending) {
        if (!limiter.tryAcquire()) {
            forget(key);
            pending.complete(Optional.empty());
            return;
        }
        try {
            Optional<SkullData> result = completer.complete(key).map(texture -> (SkullData) texture);
            pending.complete(result);
        } catch (RuntimeException failure) {
            forget(key);
            pending.completeExceptionally(failure);
        }
    }

    private void forget(String key) {
        synchronized (cache) {
            cache.remove(key);
        }
    }

    private static Map<String, CacheEntry> boundedLru(int maxEntries) {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > maxEntries;
            }
        };
    }

    // A cached future plus the wall-clock millis it was stored, so a negative result can age out.
    private record CacheEntry(CompletableFuture<Optional<SkullData>> future, long storedAt) {}
}
