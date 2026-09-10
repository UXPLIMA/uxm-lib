package com.uxplima.uxmlib.health;

/**
 * One thing that can be wrong with a plugin, and the read that finds out.
 *
 * <p>A check is read only and it answers for itself: it never throws at the caller, because a report of
 * six things must not be lost to the one that threw. {@link #safely()} is the boundary and
 * {@link HealthReport#run} calls it.
 *
 * <p>A check may reach a database or another plugin, so it is run off the server threads. What that means
 * for the caller is that a check must not touch a block or a player.
 */
@FunctionalInterface
public interface HealthCheck {

    /** What this check answers about, in a word or two: {@code storage}, {@code content}, {@code economy}. */
    default String name() {
        return getClass().getSimpleName();
    }

    /** Look, and say what was found. */
    HealthResult check();

    /** The same, with the throw turned into the answer. */
    default HealthResult safely() {
        try {
            return check();
        } catch (RuntimeException failed) {
            String said = failed.getMessage() == null ? failed.getClass().getSimpleName() : failed.getMessage();
            return HealthResult.fail(said);
        }
    }
}
