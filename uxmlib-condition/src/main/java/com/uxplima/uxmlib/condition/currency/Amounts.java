package com.uxplima.uxmlib.condition.currency;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Reading a decimal amount as the whole number a currency of whole numbers needs.
 *
 * <p>Two amounts cannot be paid in such a currency: one with a fraction in it, and one too large to
 * count. Both answer the same way, because the caller does the same thing with either: it refuses the
 * payment and nothing moves.
 */
final class Amounts {

    /** The amount that cannot be paid at all, whichever of the two reasons it is. */
    static final int NOT_WHOLE = -1;

    private Amounts() {}

    static int whole(Money amount) {
        Objects.requireNonNull(amount, "amount");
        BigDecimal value = amount.amount();
        if (value.stripTrailingZeros().scale() > 0) {
            return NOT_WHOLE;
        }
        try {
            return value.intValueExact();
        } catch (ArithmeticException tooLarge) {
            // Points and items are counted in an int, so an amount that does not fit in one cannot be
            // paid. It is refused here rather than thrown out of a use case that is moving money.
            return NOT_WHOLE;
        }
    }
}
