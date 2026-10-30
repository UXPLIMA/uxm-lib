package com.uxplima.uxmlib.board;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A board store in memory, adding the way the SQL store does. */
final class InMemoryBoardStore implements BoardStore {

    private record Row(long score, long reachedAt) {}

    private final Map<Slot, Map<UUID, Row>> rows = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    int writes;

    @Override
    public void add(UUID player, Board board, Map<BoardPeriod, Long> windows, long amount, long at) {
        writes++;
        windows.forEach((period, window) -> rows.computeIfAbsent(
                        new Slot(board.word(), period, window), s -> new HashMap<>())
                .merge(player, new Row(amount, at), (held, added) -> {
                    long next = board.after(held.score(), added.score());
                    return next == held.score() ? held : new Row(next, added.reachedAt());
                }));
    }

    @Override
    public List<Ranked> top(Board board, BoardPeriod period, long window, int limit) {
        List<Map.Entry<UUID, Row>> sorted =
                new ArrayList<>(rows.getOrDefault(new Slot(board.word(), period, window), Map.of())
                        .entrySet());
        sorted.sort(Comparator.<Map.Entry<UUID, Row>>comparingLong(
                        e -> -e.getValue().score())
                .thenComparingLong(e -> e.getValue().reachedAt()));
        return sorted.stream()
                .limit(limit)
                .map(e -> new Ranked(
                        e.getKey(), names.get(e.getKey()), e.getValue().score()))
                .toList();
    }

    @Override
    public Map<Long, Integer> countByScore(Board board, BoardPeriod period, long window) {
        Map<Long, Integer> counted = new HashMap<>();
        rows.getOrDefault(new Slot(board.word(), period, window), Map.of())
                .values()
                .forEach(row -> counted.merge(row.score(), 1, Integer::sum));
        return counted;
    }

    @Override
    public Map<Slot, Long> scoresOf(UUID player, Map<BoardPeriod, Long> windows) {
        Map<Slot, Long> held = new HashMap<>();
        rows.forEach((slot, players) -> {
            Row row = players.get(player);
            if (row != null && windows.get(slot.period()) != null && windows.get(slot.period()) == slot.window()) {
                held.put(slot, row.score());
            }
        });
        return held;
    }

    @Override
    public void forgetBefore(BoardPeriod period, long window) {
        rows.keySet().removeIf(slot -> slot.period() == period && slot.window() < window);
    }

    @Override
    public void erase(UUID player) {
        rows.values().forEach(players -> players.remove(player));
    }

    @Override
    public void name(UUID player, String name) {
        names.put(player, name);
    }
}
