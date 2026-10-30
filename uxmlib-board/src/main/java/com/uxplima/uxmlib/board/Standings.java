package com.uxplima.uxmlib.board;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The scores of the players who are online, held in memory so a placeholder answers without the database.
 *
 * <p>A player's scores in the open windows are read when they log in ({@link #load}, blocking, which the
 * pre-login event already runs off the main thread) and dropped when they quit. An event for a loaded player moves
 * their score here at once and in the store after; an event for one who is not loaded goes to the store only.
 */
public final class Standings {

    private final BoardStore store;
    private final Leaderboard leaderboard;
    private final Map<UUID, Map<BoardStore.Slot, Long>> held = new ConcurrentHashMap<>();

    public Standings(BoardStore store, Leaderboard leaderboard) {
        this.store = Objects.requireNonNull(store, "store");
        this.leaderboard = Objects.requireNonNull(leaderboard, "leaderboard");
    }

    /** Read {@code player}'s scores in the open windows. Blocks; call it off the main thread. */
    public void load(UUID player) {
        Objects.requireNonNull(player, "player");
        held.put(player, Map.copyOf(store.scoresOf(player, leaderboard.openWindows())));
    }

    /** Drop {@code player}'s scores. */
    public void forget(UUID player) {
        held.remove(Objects.requireNonNull(player, "player"));
    }

    /** Whether {@code player}'s scores are in memory. */
    public boolean loaded(UUID player) {
        return held.containsKey(Objects.requireNonNull(player, "player"));
    }

    /** Move a loaded player's scores by an event the store is about to keep. A player who is not loaded is left. */
    void added(UUID player, Board board, Map<BoardPeriod, Long> windows, long amount) {
        held.computeIfPresent(player, (id, mine) -> {
            Map<BoardStore.Slot, Long> next = new HashMap<>(mine);
            windows.forEach((period, window) -> next.merge(
                    new BoardStore.Slot(board.word(), period, window), board.after(0, amount), board::after));
            return Map.copyOf(next);
        });
    }

    /**
     * What {@code player} holds on {@code board} in the open window of {@code period}: from the board's snapshot when
     * it reads one, from memory otherwise, and zero for a player who holds nothing or is not loaded.
     */
    public long scoreOf(UUID player, Board board, BoardPeriod period) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(period, "period");
        var snapshot = board.snapshotFor(period);
        if (snapshot.isPresent()) {
            return snapshot.get().scoreOf(player).orElse(0L);
        }
        Map<BoardStore.Slot, Long> mine = held.get(player);
        if (mine == null) {
            return 0;
        }
        return mine.getOrDefault(new BoardStore.Slot(board.word(), period, leaderboard.windowOf(period)), 0L);
    }
}
