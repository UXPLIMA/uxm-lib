package com.uxplima.uxmlib.condition.action;

/**
 * A single side effect to run against an {@link ActionContext}. Parsed once from a config string into a
 * closure, then run per target: no string parsing happens at run time. An action reads the context's target
 * audience/player and resolver and performs its delivery (a message, a command, a sound, …); it must not throw
 * on a merely-absent target (for example a {@code [close]} with no player is a no-op), so an {@link
 * ActionList} can run the whole sequence without guarding each one.
 *
 * <p>{@link #async()} is a hint to the driver: an action that only touches an {@code Audience}'s text or sound
 * is thread-agnostic, while anything dispatching a command or closing an inventory must run on the main
 * thread. The default is {@code false} (sync): the safe choice; the engine never schedules on its own, it
 * exposes the flag so a caller can route through the library {@code Scheduler}.
 */
@FunctionalInterface
public interface Action {

    /** Perform this action's effect against the given context. */
    void run(ActionContext context);

    /** Whether this action is safe to run off the main thread. Defaults to {@code false} (run sync). */
    default boolean async() {
        return false;
    }
}
