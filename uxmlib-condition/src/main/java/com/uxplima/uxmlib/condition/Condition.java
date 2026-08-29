package com.uxplima.uxmlib.condition;

/**
 * The condition SPI: a single predicate over a {@link ConditionRequest}. Implementations are pure with
 * respect to the request's subject: they read the subject and resolver and return whether the condition
 * holds. They must <b>not</b> mutate the request's error sink or cancel flag themselves; collecting failure
 * messages and applying a {@link FailurePolicy} is the job of the {@link ConditionList} that drives them, so
 * a condition stays reusable under any policy.
 *
 * <p>{@link PlaceholderCondition} is the built-in implementation that covers most config-only use cases.
 */
@FunctionalInterface
public interface Condition {

    /** Whether this condition holds for the given request. */
    boolean test(ConditionRequest request);

    /** A condition that always passes; a convenient neutral element for an empty {@link ConditionList}. */
    static Condition alwaysTrue() {
        return request -> true;
    }
}
