package com.uxplima.uxmlib.board;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Puts an event on a board, in every period the board counts.
 *
 * <p>The event lands in the window open now, so a window that started since the player joined starts from nothing,
 * which is right: a new week starts empty. Memory moves at once for a player who is online; the store is written on
 * {@code async}, and adds in the database itself, so an event for a player who is away is kept too.
 */
public final class BoardRecorder {

    private final BoardStore store;
    private final Leaderboard leaderboard;
    private final Standings standings;
    private final Executor async;
    private final Clock clock;

    /** @param async where the store is written: the plugin's async lane */
    public BoardRecorder(BoardStore store, Leaderboard leaderboard, Standings standings, Executor async, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.leaderboard = Objects.requireNonNull(leaderboard, "leaderboard");
        this.standings = Objects.requireNonNull(standings, "standings");
        this.async = Objects.requireNonNull(async, "async");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Add {@code amount} to {@code player} on {@code board}: summed for a counter, kept when higher for a record. An
     * amount of zero or less changes nothing.
     *
     * @throws IllegalArgumentException for a snapshot board, which counts nothing
     */
    public void add(UUID player, Board board, long amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(board, "board");
        if (board.kind() == Board.Kind.SNAPSHOT) {
            throw new IllegalArgumentException("a snapshot board is not counted: " + board);
        }
        if (amount <= 0) {
            return;
        }
        Map<BoardPeriod, Long> windows = leaderboard.countedWindows(board);
        if (windows.isEmpty()) {
            return;
        }
        long at = clock.millis();
        standings.added(player, board, windows, amount);
        async.execute(() -> store.add(player, board, windows, amount, at));
    }
}
