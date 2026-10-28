package com.uxplima.uxmlib.condition.currency;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * One lock per player, shared by every take this currency makes for that player.
 *
 * <p>A currency that reads a balance and then sends a take is two steps, and two takes for one player
 * that interleave would both read the old balance and both pass. Holding the player's lock across the
 * read and the take makes them one step for everything this plugin moves. The locks are striped, so the
 * set stays the same size however many players the server has seen.
 */
public final class PlayerLocks {

    private static final int STRIPES = 64;

    private final Object[] stripes = new Object[STRIPES];

    public PlayerLocks() {
        for (int at = 0; at < STRIPES; at++) {
            stripes[at] = new Object();
        }
    }

    /** Run {@code step} while holding the lock of {@code player}, and return what it answered. */
    public boolean holding(UUID player, BooleanSupplier step) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(step, "step");
        synchronized (stripes[Math.floorMod(player.hashCode(), STRIPES)]) {
            return step.getAsBoolean();
        }
    }
}
