package com.uxplima.uxmlib.condition.action;

/**
 * Thrown when money or items could not move: a {@link CostAction} whose wallet refused the withdrawal or
 * whose item store did not hold enough, and {@code [give-money]} whose wallet could not pay out. Nothing
 * moved when this is thrown, and an {@link ActionList} that throws it stops there, so the rewards behind an
 * unpaid cost never run.
 *
 * <p>It is unchecked because a failure to move money is a run-time state of the world, not a branch every
 * call site should be forced to write out. Failing loudly is deliberate in both directions: a take that
 * quietly did nothing and let the reward run is how a shop gives its stock away, and a payment that quietly
 * did nothing is a wage a player never sees and nobody can explain.
 */
public class ActionCostException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** A failure naming the cost that could not be met. */
    public ActionCostException(String message) {
        super(message);
    }
}
