package com.uxplima.uxmlib.data;

import java.util.UUID;

/**
 * The backend seam an {@link OnlineDataManager} writes through. It is deliberately the smallest contract that
 * lets the manager load a player's value on join and persist it on quit and on a timer: one blocking
 * {@link #load} and one blocking {@link #save}, both keyed by player {@link UUID}.
 *
 * <p>Keeping this seam here (rather than depending on {@code uxmlib-storage}) is what lets the manager live
 * in {@code uxmlib-integration} without dragging the storage module (and its JDBC stack) onto every consumer.
 * The consumer wires the seam to whatever backend they already run: a {@code uxmlib-storage}
 * {@code Repository}/{@code WriteBehindStorage}, a flat file, an in-memory map for tests. Both methods are
 * called off the main thread by the manager (on {@code Scheduler.async}), so an implementation may block on
 * I/O; it must be safe to call from a pool thread and must never touch the Bukkit API.
 *
 * @param <V> the per-player value type
 */
@FunctionalInterface
public interface DataStore<V> {

    /**
     * Load the value for {@code id}, returning a non-null value (a freshly defaulted one if the player has no
     * stored row yet). Called on a background thread on player join. May block on I/O; may throw, in which
     * case the manager routes the failure to its error sink and leaves the player unloaded.
     */
    V load(UUID id);

    /**
     * Persist {@code value} for {@code id}. Called on a background thread on quit and on the periodic flush.
     * May block on I/O; may throw, in which case the manager routes the failure to its error sink. The
     * default no-op suits a read-only store; override it to persist.
     */
    default void save(UUID id, V value) {}
}
