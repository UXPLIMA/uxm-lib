package com.uxplima.uxmlib.condition.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount in one currency.
 *
 * <p>The amount is a {@link BigDecimal} because the plugin moves other people's money. A {@code double}
 * loses a fraction on every third addition, and a plugin that loses a fraction per sale is a plugin that
 * prints money over a week. Every economy adapter that can only offer a {@code double} converts at its
 * own edge, and the loss stays there.
 *
 * <p>Two amounts in two currencies are not comparable and never become one sum. Every method that takes
 * a second amount refuses one of another currency, so a menu that adds a price to a fee cannot silently
 * mix them.
 */
public record Money(CurrencyId currency, BigDecimal amount) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative, got: " + amount);
        }
        amount = amount.stripTrailingZeros();
        if (amount.scale() < 0) {
            // stripTrailingZeros turns 100 into 1E+2, which reads wrong in a log and in a column.
            amount = amount.setScale(0, RoundingMode.UNNECESSARY);
        }
    }

    public static Money of(CurrencyId currency, String amount) {
        return new Money(currency, new BigDecimal(amount));
    }

    public static Money zero(CurrencyId currency) {
        return new Money(currency, BigDecimal.ZERO);
    }

    public Money plus(Money other) {
        return new Money(currency, amount.add(sameCurrency(other).amount));
    }

    /** This amount less {@code other}. The plugin never holds a negative sum, so a bigger subtrahend is refused. */
    public Money minus(Money other) {
        BigDecimal left = amount.subtract(sameCurrency(other).amount);
        if (left.signum() < 0) {
            throw new IllegalArgumentException("result must not be negative: " + amount + " minus " + other.amount);
        }
        return new Money(currency, left);
    }

    /** The unit price of one item, multiplied out for a stack. */
    public Money times(long factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("factor must not be negative, got: " + factor);
        }
        return new Money(currency, amount.multiply(BigDecimal.valueOf(factor)));
    }

    /**
     * This amount shared between {@code parts}, rounded down.
     *
     * <p>Down, for the reason {@link #percent} gives: the plugin may lose the fraction and the player may
     * not. It answers what one of a stack went for, and a stack of three that sold for ten is three and a
     * third each, which no currency can hold exactly.
     */
    public Money dividedBy(long parts) {
        if (parts < 1) {
            throw new IllegalArgumentException("parts must be at least one, got: " + parts);
        }
        return new Money(currency, amount.divide(BigDecimal.valueOf(parts), amount.scale() + 4, RoundingMode.DOWN));
    }

    /**
     * The part of this amount that a rate takes, rounded down.
     *
     * <p>Down, and never half up: a fee that rounds up takes a fraction from the player that nobody
     * authorised, and it takes it on every sale. The plugin may lose the fraction; the player may not.
     */
    public Money percent(BigDecimal rate) {
        Objects.requireNonNull(rate, "rate");
        if (rate.signum() < 0) {
            throw new IllegalArgumentException("rate must not be negative, got: " + rate);
        }
        BigDecimal hundred = BigDecimal.valueOf(100);
        return new Money(currency, amount.multiply(rate).divide(hundred, amount.scale() + 4, RoundingMode.DOWN));
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isAtLeast(Money other) {
        return compareTo(other) >= 0;
    }

    public boolean isMoreThan(Money other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(sameCurrency(other).amount);
    }

    /**
     * Value equality that ignores the scale.
     *
     * <p>{@code BigDecimal.equals} calls 1.0 and 1.00 different, and the two arrive from two adapters for
     * the same balance. The record's generated method would therefore split one price into two.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof Money money && currency.equals(money.currency) && amount.compareTo(money.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(currency, amount.stripTrailingZeros().toPlainString());
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }

    private Money sameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("cannot mix " + currency + " and " + other.currency);
        }
        return other;
    }
}
