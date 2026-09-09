package com.uxplima.uxmlib.condition.action;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import com.uxplima.uxmlib.condition.Condition;
import com.uxplima.uxmlib.condition.ConditionLines;

/**
 * One action, run under the conditions its own line named.
 *
 * <p>The order is the whole of it, and it is the order a reader would guess: the chance is rolled, then the
 * condition is asked, then the wait happens, then it runs however many times it was told to. Asking the
 * condition after the wait would be a different feature and a defensible one, but it is the one that
 * surprises people: an operator who wrote a delay meant "say this later", not "check again later".
 *
 * <p>A repeat with no gap runs its copies in the same tick, which is what an operator writing three particle
 * bursts wants. A repeat with a gap needs the context to be able to schedule, and a context that cannot
 * simply runs them all at once rather than dropping them: losing an effect is worse than bunching it.
 */
public final class ModifiedAction implements Action {

    private final Action delegate;
    private final ActionModifiers modifiers;
    private final Optional<Condition> condition;

    private ModifiedAction(Action delegate, ActionModifiers modifiers) {
        this.delegate = delegate;
        this.modifiers = modifiers;
        this.condition = modifiers.condition().map(ConditionLines::read);
    }

    /**
     * {@code action} under {@code modifiers}, or {@code action} itself when there is nothing to apply.
     *
     * @throws IllegalArgumentException when a cost action carries modifiers, which it may not
     */
    public static Action of(Action action, ActionModifiers modifiers) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(modifiers, "modifiers");
        if (modifiers.isPlain()) {
            return action;
        }
        if (action instanceof CostAction) {
            // Every cost in a list is checked before any action in it runs. A cost that might not run, or
            // might run later, cannot be part of that check, and a pre-flight that lies is worse than no
            // pre-flight: it takes the money and then refuses the reward.
            throw new IllegalArgumentException("a cost action takes no modifiers: it is checked before the list runs");
        }
        return new ModifiedAction(action, modifiers);
    }

    /** The modifiers this action carries, for a caller that wants to describe the line. */
    public ActionModifiers modifiers() {
        return modifiers;
    }

    /** The action underneath, for a caller that wants to know what it really is. */
    public Action delegate() {
        return delegate;
    }

    @Override
    public void run(ActionContext context) {
        Objects.requireNonNull(context, "context");
        if (modifiers.chance() < 1 && ThreadLocalRandom.current().nextDouble() >= modifiers.chance()) {
            return;
        }
        if (condition.isPresent() && !condition.get().test(context.asConditionRequest())) {
            return;
        }
        if (modifiers.delay().isZero()) {
            repeat(context, 0);
            return;
        }
        context.later(modifiers.delay(), () -> repeat(context, 0));
    }

    /** Run copy {@code at}, and hand the next one to the scheduler when a gap was asked for. */
    private void repeat(ActionContext context, int at) {
        delegate.run(context);
        int next = at + 1;
        if (next >= modifiers.repeat()) {
            return;
        }
        Duration gap = modifiers.every();
        if (gap.isZero()) {
            repeat(context, next);
            return;
        }
        context.later(gap, () -> repeat(context, next));
    }
}
