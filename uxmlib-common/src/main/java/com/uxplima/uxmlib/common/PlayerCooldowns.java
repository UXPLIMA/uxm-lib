package com.uxplima.uxmlib.common;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * How long each player still waits before they may do the plugin's one waiting thing again.
 *
 * <p>The same store as {@link KeyedCooldowns} with the key left out, for the plugin that waits on one
 * action: a bow that shoots, a lobby that is switched. A plugin with more than one wait holds a
 * {@link KeyedCooldowns} instead, rather than one {@code PlayerCooldowns} per action.
 *
 * <p>This is a gameplay wait, held against a player. The gate that {@code @Cooldown} puts on a command is a
 * different thing and lives in {@code uxmlib-command}: it is keyed by an opaque string and can be persisted
 * through a store, because a daily command has to survive a restart.
 *
 * @see KeyedCooldowns for what {@code take} promises, why the clock is monotonic, and what a zero wait does
 */
public final class PlayerCooldowns {

    /** The one key, so the keyed store can hold an unkeyed wait without knowing it is unkeyed. */
    private enum Only {
        WAIT
    }

    private final KeyedCooldowns<Only> waits;

    private PlayerCooldowns(KeyedCooldowns<Only> waits) {
        this.waits = waits;
    }

    /** A store on the monotonic clock, which is what a server wants. */
    public static PlayerCooldowns create() {
        return new PlayerCooldowns(KeyedCooldowns.create());
    }

    /** A store on a clock the caller controls, which is what a test wants. */
    public static PlayerCooldowns withClock(LongSupplier millis) {
        Objects.requireNonNull(millis, "millis");
        return new PlayerCooldowns(KeyedCooldowns.withClock(millis));
    }

    /**
     * Take the action if no wait is left, and arm the next wait in the same step.
     *
     * @return {@link Duration#ZERO} when the action was taken, otherwise how long is still to wait
     */
    public Duration take(UUID owner, Duration wait) {
        return waits.take(owner, Only.WAIT, wait);
    }

    /** How long {@code owner} still waits. Zero means they may act now. */
    public Duration remaining(UUID owner) {
        return waits.remaining(owner, Only.WAIT);
    }

    /** Start a wait whatever is left of the last one, for a caller that has already made the decision. */
    public void start(UUID owner, Duration wait) {
        waits.start(owner, Only.WAIT, wait);
    }

    /**
     * Drop the wait held against {@code owner}.
     *
     * <p>Both callers want this one method: the one giving an action back because what it charged for did
     * not happen, and the one dropping a player who has left. With no key to tell apart they are the same
     * operation, and a second name for it would only be a second thing to keep in step.
     */
    public void clear(UUID owner) {
        waits.forget(owner);
    }

    /** Drop every wait that has run out, and answer how many were dropped. */
    public int purgeExpired() {
        return waits.purgeExpired();
    }

    /** How many waits are held, expired ones included. */
    public int tracked() {
        return waits.tracked();
    }
}
