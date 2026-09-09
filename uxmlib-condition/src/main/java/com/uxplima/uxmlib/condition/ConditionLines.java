package com.uxplima.uxmlib.condition;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * One condition, written as an operator writes it.
 *
 * <p>The library owned three condition kinds and no way to read a line into one, so every plugin that wanted
 * a requirement in a file wrote the same twenty lines of prefix matching. uxmCrates had a copy called
 * {@code RequirementLines}, and a second copy is a grammar that drifts: the drift shows up as a requirement
 * that loads and then never applies, which nobody notices until an economy has already leaked.
 *
 * <p>Three shapes, and the last is the fallback so a bare placeholder comparison needs no prefix:
 *
 * <pre>{@code
 *   money vault >= 500
 *   item DIAMOND >= 3
 *   %player_level% >= 10
 * }</pre>
 */
public final class ConditionLines {

    private static final String MONEY = "money ";
    private static final String ITEM = "item ";

    private ConditionLines() {}

    /**
     * The condition one line asks for.
     *
     * @throws IllegalArgumentException when the line is not one of the three shapes, with a message that
     *     names what was written
     */
    public static Condition read(String line) {
        Objects.requireNonNull(line, "line");
        String trimmed = line.strip();
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        if (lowered.startsWith(MONEY)) {
            return MoneyCondition.parse(trimmed.substring(MONEY.length()));
        }
        if (lowered.startsWith(ITEM)) {
            return ItemCondition.parse(trimmed.substring(ITEM.length()));
        }
        return PlaceholderCondition.parse(trimmed);
    }

    /**
     * The currency this line reads a balance from, or nothing when the line reads no balance.
     *
     * <p>A money line with nothing on its left side names no backend, and that is an empty string here rather
     * than an absent one, because the caller has to tell "no backend written" from "not a money line".
     */
    public static Optional<String> currency(String line) {
        Objects.requireNonNull(line, "line");
        String trimmed = line.strip();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith(MONEY)) {
            return Optional.empty();
        }
        return Optional.of(MoneyCondition.parse(trimmed.substring(MONEY.length()))
                .currencyTemplate()
                .strip());
    }
}
