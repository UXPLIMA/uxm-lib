package com.uxplima.uxmlib.board;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Where counted boards are kept: one row per board, period, window and player.
 *
 * <p>Every method blocks and is called on the async lane. A write adds to what the row holds in the database itself,
 * so an event for a player who is not online, or one counted on another server sharing the database, is never lost
 * to a stale copy.
 */
public interface BoardStore {

    /** One board in one window, the unit a player holds one score in. */
    record Slot(String board, BoardPeriod period, long window) {

        public Slot {
            Objects.requireNonNull(board, "board");
            Objects.requireNonNull(period, "period");
        }
    }

    /**
     * Add {@code amount} to {@code player}'s score on {@code board} in each of {@code windows}: summed for a counter,
     * kept when higher for a record. {@code at} is when it happened, in epoch milliseconds; of two equal scores the
     * one that reached it first ranks first.
     */
    void add(UUID player, Board board, Map<BoardPeriod, Long> windows, long amount, long at);

    /** The top of a board in one window: the higher score first, and of equal scores the one reached first. */
    List<Ranked> top(Board board, BoardPeriod period, long window, int limit);

    /** How many players hold each score above zero on a board in one window. */
    Map<Long, Integer> countByScore(Board board, BoardPeriod period, long window);

    /** What {@code player} holds in each of {@code windows}, on every board. A slot with nothing is left out. */
    Map<Slot, Long> scoresOf(UUID player, Map<BoardPeriod, Long> windows);

    /** Drop every window of {@code period} that started before {@code window}. */
    void forgetBefore(BoardPeriod period, long window);

    /** Delete every score of {@code player}, on every board and in every window. */
    void erase(UUID player);

    /** Keep the name {@code player} was let in under, so a board names them while they are away. */
    void name(UUID player, String name);
}
