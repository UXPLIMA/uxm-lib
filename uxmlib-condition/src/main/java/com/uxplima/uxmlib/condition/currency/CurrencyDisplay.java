package com.uxplima.uxmlib.condition.currency;

import java.util.List;
import java.util.Objects;

/**
 * How one currency is written where a player reads it.
 *
 * <p>It is the operator's, because a currency is theirs: a server writes {@code $1,250}, another writes
 * {@code 1250 coins}, and a third writes {@code 1.2k gems}. None of them is ours to choose.
 *
 * @param format where the number and the name go. {@code {amount}} is the number and {@code {currency}}
 *     is the id of the currency
 * @param formatOne the same, for an amount of exactly one, because a name with a plural reads badly
 *     beside a single item
 * @param decimals how many places a fraction may take
 * @param trim whether a fraction of zeros is dropped, so ten is written {@code 10} and not {@code 10.00}
 * @param thousands what goes between the groups of three, or nothing
 * @param point what goes before the fraction
 * @param shortForm whether a large number is shortened, as {@code 1.2k}
 * @param suffixes the short forms, from the first thousand upwards
 */
public record CurrencyDisplay(
        String format,
        String formatOne,
        int decimals,
        boolean trim,
        String thousands,
        String point,
        boolean shortForm,
        List<String> suffixes) {

    public CurrencyDisplay {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(formatOne, "formatOne");
        Objects.requireNonNull(thousands, "thousands");
        Objects.requireNonNull(point, "point");
        suffixes = List.copyOf(Objects.requireNonNull(suffixes, "suffixes"));
        if (decimals < 0) {
            throw new IllegalArgumentException("decimals must not be less than nothing, got: " + decimals);
        }
        if (point.isEmpty() && decimals > 0) {
            throw new IllegalArgumentException("a fraction needs something before it");
        }
    }

    /** A display that says nothing about one, so one is written the way every other number is. */
    public CurrencyDisplay(
            String format,
            int decimals,
            boolean trim,
            String thousands,
            String point,
            boolean shortForm,
            List<String> suffixes) {
        this(format, format, decimals, trim, thousands, point, shortForm, suffixes);
    }

    /** What a currency that says nothing about itself looks like. */
    public static CurrencyDisplay plain() {
        return new CurrencyDisplay("{amount} {currency}", 2, true, ",", ".", false, List.of("k", "M", "B", "T"));
    }
}
