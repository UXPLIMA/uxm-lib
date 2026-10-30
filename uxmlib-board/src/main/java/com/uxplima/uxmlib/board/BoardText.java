package com.uxplima.uxmlib.board;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * The words a board answers with, in the language of the player who reads them. The plugin supplies them; this
 * module names no wording.
 *
 * <p>{@code reader} is null when a line is filled for no player at all.
 */
public interface BoardText {

    /** The time until a period starts again, split the way a line shows it. */
    sealed interface Left {

        /** A day or more: whole days and the hours past them. */
        record Days(long days, long hours) implements Left {}

        /** Under a day: whole hours and the minutes past them. */
        record Hours(long hours, long minutes) implements Left {}

        /** Under an hour: the minutes, at least one. */
        record Minutes(long minutes) implements Left {}
    }

    /** The name of a place nobody holds. */
    String nobody(@Nullable UUID reader);

    /** The value of a place nobody holds, and the place of a player who holds nothing. */
    String none(@Nullable UUID reader);

    /** The time until a period starts again. */
    String resets(@Nullable UUID reader, Left left);

    /** How {@code score} on {@code board} is shown: a plain number unless the plugin shows a time or a sum of money. */
    default String score(Board board, long score, @Nullable UUID reader) {
        return Long.toString(score);
    }

    /**
     * How {@code score} on {@code board} is shown in {@code period}. The same as {@link #score(Board, long, UUID)}
     * unless the plugin shows all time another way, such as a level where the periods show the experience earned.
     */
    default String score(Board board, BoardPeriod period, long score, @Nullable UUID reader) {
        return score(board, score, reader);
    }
}
