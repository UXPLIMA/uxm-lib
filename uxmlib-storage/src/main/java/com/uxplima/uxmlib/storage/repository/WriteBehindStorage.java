package com.uxplima.uxmlib.storage.repository;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Ticker;

/**
 * A write-behind cache over a {@link StorageProvider}. Unlike {@link CachedStorage} (which writes through to
 * the backend on every {@code save}), a write here only marks the key DIRTY and updates the in-memory copy:
 * no provider write happens until {@link #flush} or {@link #flushAll}. So N writes to one key between flushes
 * collapse to a single provider save (coalescing), with last-write-wins. This is the path for hot data that
 * changes many times per tick (a player's stats) where one batched persist is far cheaper than one-per-edit.
 *
 * <p>The read tier is a Caffeine-backed {@link Cache} with optional size/TTL eviction ({@code maximumSize} /
 * {@code expireAfterAccess}), so cold entries are dropped instead of leaking. Pending dirty values are held
 * in a separate buffer that eviction never touches, so an unsaved write is never lost. Scheduling a periodic
 * flush is intentionally left to the caller (and to the Paper-coupled online manager): this class stays
 * Paper-free and persists only when asked.
 */
public final class WriteBehindStorage<I, T> {

    private final StorageProvider<I, T> backend;
    private final Function<T, I> idOf;
    private final Cache<I, T> readTier;
    private final ConcurrentHashMap<I, T> pending = new ConcurrentHashMap<>();

    private WriteBehindStorage(StorageProvider<I, T> backend, Function<T, I> idOf, Cache<I, T> readTier) {
        this.backend = backend;
        this.idOf = idOf;
        this.readTier = readTier;
    }

    /** Start configuring a write-behind cache over {@code backend}, given how to read an entity's id. */
    public static <I, T> Builder<I, T> builder(StorageProvider<I, T> backend, Function<T, I> idOf) {
        return new Builder<>(Objects.requireNonNull(backend, "backend"), Objects.requireNonNull(idOf, "idOf"));
    }

    /**
     * Stage {@code entity} as a dirty write: update the in-memory copy now, persist later on a flush. A
     * later {@code save} of the same key before a flush overwrites the pending value, so the flush coalesces
     * them into one provider save.
     */
    public void save(T entity) {
        Objects.requireNonNull(entity, "entity");
        I id = idOf.apply(entity);
        pending.put(id, entity);
        readTier.put(id, entity);
    }

    /** The value for {@code id}: a pending dirty write if present, else read through to the backend. */
    public Optional<T> get(I id) {
        Objects.requireNonNull(id, "id");
        T dirty = pending.get(id);
        if (dirty != null) {
            return Optional.of(dirty);
        }
        Optional<T> cached = readTier.getIfPresent(id);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<T> loaded = backend.findById(id);
        loaded.ifPresent(value -> readTier.put(id, value));
        return loaded;
    }

    /** Load {@code id} into the read tier (e.g. on join) and return it if present. */
    public Optional<T> load(I id) {
        return get(id);
    }

    /** The currently cached value for {@code id} without touching the backend. */
    public Optional<T> cached(I id) {
        Objects.requireNonNull(id, "id");
        T dirty = pending.get(id);
        return dirty != null ? Optional.of(dirty) : readTier.getIfPresent(id);
    }

    /** Persist the pending write for {@code id} if it is dirty, then mark it clean; else do nothing. */
    public boolean flush(I id) {
        Objects.requireNonNull(id, "id");
        T staged = pending.get(id);
        if (staged == null) {
            return false;
        }
        // Persist first, then drop the key only on success: if save throws the key stays dirty and is
        // retried on the next flush instead of being silently lost. The value-conditional remove also
        // avoids clobbering a newer pending write that arrived while this save was in flight.
        backend.save(staged);
        pending.remove(id, staged);
        return true;
    }

    /** Persist every dirty key to the backend in one pass and clear the dirty set. */
    public void flushAll() {
        for (I id : Set.copyOf(pending.keySet())) {
            flush(id);
        }
    }

    /**
     * Drop {@code id} from the read tier without deleting it from the backend. A dirty key is kept (dropping
     * it would lose an unsaved write); flush it first if you mean to release it.
     */
    public void invalidate(I id) {
        Objects.requireNonNull(id, "id");
        if (pending.containsKey(id)) {
            return;
        }
        readTier.invalidate(id);
        // A save can land between the containsKey check and the invalidate above, leaving the key dirty in
        // pending but dropped from the read tier. Re-check after the drop and restore the read-tier copy so
        // a dirty key is never left out of the read tier (its documented invariant).
        T dirty = pending.get(id);
        if (dirty != null) {
            readTier.put(id, dirty);
        }
    }

    /** Flush {@code id} if dirty, then drop it from the read tier: the save-on-quit step. */
    public void flushAndInvalidate(I id) {
        flush(id);
        readTier.invalidate(Objects.requireNonNull(id, "id"));
    }

    /** How many keys are dirty (staged for the next flush). */
    public int dirtyCount() {
        return pending.size();
    }

    /** Run pending read-tier maintenance (size/TTL eviction); chiefly for tests driving a fake ticker. */
    public void cleanUp() {
        readTier.cleanUp();
    }

    /** Fluent builder for a {@link WriteBehindStorage}, configuring its read/eviction tier. */
    public static final class Builder<I, T> {
        private final StorageProvider<I, T> backend;
        private final Function<T, I> idOf;
        private final Cache.Builder readTier = Cache.builder();

        private Builder(StorageProvider<I, T> backend, Function<T, I> idOf) {
            this.backend = backend;
            this.idOf = idOf;
        }

        /** The maximum number of entries in the read tier before it starts evicting. */
        public Builder<I, T> maximumSize(long size) {
            readTier.maximumSize(size);
            return this;
        }

        /** Evict a read-tier entry this long after it was last read or written (the TTL tier). */
        public Builder<I, T> expireAfterAccess(Duration duration) {
            readTier.expireAfterAccess(duration);
            return this;
        }

        /** The time source for the read tier's expiry window; supply a fake one for deterministic tests. */
        public Builder<I, T> ticker(Ticker ticker) {
            readTier.ticker(ticker);
            return this;
        }

        /** Build the write-behind cache. */
        public WriteBehindStorage<I, T> build() {
            return new WriteBehindStorage<>(backend, idOf, readTier.build());
        }
    }
}
