package com.uxplima.uxmlib.command.annotation;

import java.util.OptionalLong;

/**
 * An optional persistence seam behind {@link Cooldowns}. The default cooldown gate keeps its windows in
 * memory, which is fine for short rate-limits but loses long ones (a daily kit, a weekly reward) across a
 * restart. A consumer that wants those to survive supplies a {@code CooldownStore}: typically backed by
 * {@code uxmlib-storage}, and {@link Cooldowns} reads through it on a cold key and writes through it when a
 * window is armed.
 *
 * <p>The contract is deliberately tiny so the command module never depends on storage: the store maps an
 * opaque key to the epoch-millis at which that key's window expires. It owns its own threading and durability;
 * implementations should be fast (they sit on the command path) and may keep their own cache. This module
 * ships no implementation (the in-memory default needs none), so a persisted store is entirely the
 * consumer's to provide.
 */
public interface CooldownStore {

    /**
     * The epoch-millis at which {@code key}'s window expires, or {@link OptionalLong#empty()} when the store
     * holds no (or an already-expired) window for it.
     *
     * @param key the opaque cooldown key, never {@code null}
     */
    OptionalLong load(String key);

    /**
     * Persist that {@code key}'s window expires at {@code expiryMillis} (epoch millis), replacing any earlier
     * value for the key.
     *
     * @param key the opaque cooldown key, never {@code null}
     * @param expiryMillis the epoch-millis at which the window expires
     */
    void save(String key, long expiryMillis);
}
