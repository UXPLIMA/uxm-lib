package com.uxplima.uxmlib.board;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * How long a board counts before it starts again. All time never starts again.
 *
 * <p>The word is what a placeholder and a config write, so it is part of what an operator types and does not change
 * with the language.
 */
public enum BoardPeriod {
    ALLTIME("alltime"),
    MONTHLY("monthly"),
    WEEKLY("weekly"),
    DAILY("daily");

    private final String word;

    BoardPeriod(String word) {
        this.word = word;
    }

    /** The word a placeholder and a config use for this period. */
    public String word() {
        return word;
    }

    /** The period a word names, written exactly as {@link #word()} gives it. */
    public static Optional<BoardPeriod> byWord(String word) {
        Objects.requireNonNull(word, "word");
        return Arrays.stream(values())
                .filter(period -> period.word.equals(word))
                .findFirst();
    }
}
