package com.uxplima.uxmlib.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LeaderboardTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID KIM = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    // A Wednesday.
    private static final Instant START = Instant.parse("2030-01-02T12:00:00Z");

    private final MovableClock clock = new MovableClock(START);
    private final InMemoryBoardStore store = new InMemoryBoardStore();
    private final BoardSettings settings = new BoardSettings(
            10,
            Duration.ofSeconds(15),
            EnumSet.allOf(BoardPeriod.class),
            new BoardCalendar(ZoneOffset.UTC, DayOfWeek.MONDAY),
            12,
            BoardSettings.STEVE);

    private Leaderboard leaderboard(Board... boards) {
        return new Leaderboard(store, List.of(boards), id -> "?" + id.toString().substring(34), () -> settings, clock);
    }

    @Test
    void aCounterAddsEveryEventInEveryPeriod() {
        Board kills = Board.counter("kills");
        Leaderboard board = leaderboard(kills);
        Standings standings = new Standings(store, board);
        BoardRecorder recorder = new BoardRecorder(store, board, standings, Runnable::run, clock);

        recorder.add(ALEX, kills, 2);
        recorder.add(ALEX, kills, 3);
        recorder.add(SAM, kills, 4);
        board.refresh();

        for (BoardPeriod period : BoardPeriod.values()) {
            assertThat(board.at(kills, period, 1)).hasValueSatisfying(row -> {
                assertThat(row.player()).isEqualTo(ALEX);
                assertThat(row.score()).isEqualTo(5);
            });
        }
        assertThat(board.at(kills, BoardPeriod.WEEKLY, 2))
                .hasValueSatisfying(row -> assertThat(row.score()).isEqualTo(4));
        assertThat(board.at(kills, BoardPeriod.WEEKLY, 3)).isEmpty();
    }

    @Test
    void boardsFromASupplierFollowWhatItGivesNow() {
        Board mining = Board.counter("mining");
        Board fishing = Board.counter("fishing");
        java.util.concurrent.atomic.AtomicReference<List<Board>> tracks =
                new java.util.concurrent.atomic.AtomicReference<>(List.of(mining));
        Leaderboard board = new Leaderboard(store, tracks::get, id -> "?", () -> settings, clock);
        BoardRecorder recorder = new BoardRecorder(store, board, new Standings(store, board), Runnable::run, clock);
        recorder.add(ALEX, mining, 3);
        recorder.add(SAM, fishing, 5);
        board.refresh();

        assertThat(board.board("fishing")).isEmpty();
        assertThat(board.at(mining, BoardPeriod.DAILY, 1)).isPresent();

        tracks.set(List.of(mining, fishing));
        assertThat(board.boards()).containsExactly(mining, fishing);
        board.refresh();
        assertThat(board.at(fishing, BoardPeriod.DAILY, 1))
                .hasValueSatisfying(row -> assertThat(row.player()).isEqualTo(SAM));

        tracks.set(List.of(fishing));
        assertThat(board.board("mining"))
                .as("a board taken away stops answering at once")
                .isEmpty();
    }

    @Test
    void aWordIsUpToThirtyTwoLong() {
        assertThat(Board.counter("a".repeat(32)).word()).hasSize(32);
        assertThatThrownBy(() -> Board.counter("a".repeat(33))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Leaderboard(
                        store,
                        List.of(Board.counter("kills"), Board.counter("kills")),
                        id -> "?",
                        () -> settings,
                        clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRecordKeepsTheBestAndTheFirstToReachItRanksFirst() {
        Board best = Board.record("best");
        Leaderboard board = leaderboard(best);
        BoardRecorder recorder = new BoardRecorder(store, board, new Standings(store, board), Runnable::run, clock);

        recorder.add(ALEX, best, 30);
        clock.advance(Duration.ofMinutes(1));
        recorder.add(SAM, best, 30);
        recorder.add(ALEX, best, 10);
        board.refresh();

        assertThat(board.at(best, BoardPeriod.ALLTIME, 1)).hasValueSatisfying(row -> {
            assertThat(row.player()).isEqualTo(ALEX);
            assertThat(row.score()).isEqualTo(30);
        });
        assertThat(board.placeOf(best, BoardPeriod.ALLTIME, 30)).hasValue(1);
    }

    @Test
    void playersOnTheSameScoreShareAPlaceAndAScoreThatJustMovedShowsItsPlace() {
        Board kills = Board.counter("kills");
        Leaderboard board = leaderboard(kills);
        BoardRecorder recorder = new BoardRecorder(store, board, new Standings(store, board), Runnable::run, clock);
        recorder.add(ALEX, kills, 5);
        recorder.add(SAM, kills, 5);
        recorder.add(KIM, kills, 1);
        board.refresh();

        assertThat(board.placeOf(kills, BoardPeriod.ALLTIME, 5)).hasValue(1);
        assertThat(board.placeOf(kills, BoardPeriod.ALLTIME, 1)).hasValue(3);
        assertThat(board.placeOf(kills, BoardPeriod.ALLTIME, 6)).hasValue(1);
        assertThat(board.placeOf(kills, BoardPeriod.ALLTIME, 0)).isEmpty();
    }

    @Test
    void aNewWeekStartsEmptyAndAllTimeKeepsCounting() {
        Board kills = Board.counter("kills");
        Leaderboard board = leaderboard(kills);
        BoardRecorder recorder = new BoardRecorder(store, board, new Standings(store, board), Runnable::run, clock);
        recorder.add(ALEX, kills, 5);
        board.refresh();

        clock.advance(Duration.ofDays(7));
        assertThat(board.at(kills, BoardPeriod.WEEKLY, 1)).isEmpty();
        board.refresh();

        assertThat(board.at(kills, BoardPeriod.WEEKLY, 1)).isEmpty();
        assertThat(board.at(kills, BoardPeriod.ALLTIME, 1)).isPresent();
    }

    @Test
    void anEventForAPlayerWhoIsAwayIsStillCounted() {
        Board kills = Board.counter("kills");
        Leaderboard board = leaderboard(kills);
        Standings standings = new Standings(store, board);
        BoardRecorder recorder = new BoardRecorder(store, board, standings, Runnable::run, clock);

        recorder.add(ALEX, kills, 3);
        standings.load(ALEX);
        recorder.add(ALEX, kills, 2);

        assertThat(standings.scoreOf(ALEX, kills, BoardPeriod.MONTHLY)).isEqualTo(5);
        board.refresh();
        assertThat(board.at(kills, BoardPeriod.MONTHLY, 1))
                .hasValueSatisfying(row -> assertThat(row.score()).isEqualTo(5));
    }

    @Test
    void aSnapshotBoardReadsWhatPlayersHoldForAllTimeOnly() {
        Board balance = Board.snapshot("balance", new FixedSnapshot(Map.of(ALEX, 900L, SAM, 50L)));
        Leaderboard board = leaderboard(balance);
        Standings standings = new Standings(store, board);
        board.refresh();

        assertThat(board.at(balance, BoardPeriod.ALLTIME, 1))
                .hasValueSatisfying(row -> assertThat(row.score()).isEqualTo(900));
        assertThat(board.runs(balance, BoardPeriod.WEEKLY)).isFalse();
        assertThat(standings.scoreOf(SAM, balance, BoardPeriod.ALLTIME)).isEqualTo(50);
        assertThat(board.placeOf(balance, BoardPeriod.ALLTIME, 50)).hasValue(2);
        BoardRecorder recorder = new BoardRecorder(store, board, standings, Runnable::run, clock);
        assertThatThrownBy(() -> recorder.add(ALEX, balance, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aCounterCanTakeItsAllTimeBoardFromASnapshot() {
        Board xp = Board.counter("xp").withAllTime(new FixedSnapshot(Map.of(SAM, 7L)));
        Leaderboard board = leaderboard(xp);
        BoardRecorder recorder = new BoardRecorder(store, board, new Standings(store, board), Runnable::run, clock);

        recorder.add(ALEX, xp, 40);
        board.refresh();

        assertThat(board.at(xp, BoardPeriod.ALLTIME, 1))
                .hasValueSatisfying(row -> assertThat(row.player()).isEqualTo(SAM));
        assertThat(board.at(xp, BoardPeriod.DAILY, 1))
                .hasValueSatisfying(row -> assertThat(row.player()).isEqualTo(ALEX));
    }

    @Test
    void aRowWithNoKeptNameIsNamedByThePlugin() {
        Board kills = Board.counter("kills");
        Leaderboard board = leaderboard(kills);
        new BoardRecorder(store, board, new Standings(store, board), Runnable::run, clock).add(ALEX, kills, 1);
        store.name(SAM, "Sam");
        board.refresh();

        assertThat(board.at(kills, BoardPeriod.ALLTIME, 1))
                .hasValueSatisfying(row -> assertThat(row.name()).isEqualTo("?0a"));
    }

    @Test
    void twoBoardsCannotShareAWord() {
        assertThatThrownBy(() -> leaderboard(Board.counter("kills"), Board.record("kills")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Board.counter("top_kills")).isInstanceOf(IllegalArgumentException.class);
    }

    /** What players hold now, fixed for the test. */
    record FixedSnapshot(Map<UUID, Long> held) implements Snapshot {

        @Override
        public List<Ranked> top(int limit) {
            return held.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                    .limit(limit)
                    .map(e -> new Ranked(e.getKey(), null, e.getValue()))
                    .toList();
        }

        @Override
        public Map<Long, Integer> countByScore() {
            return held.values().stream()
                    .collect(java.util.stream.Collectors.toMap(score -> score, score -> 1, Integer::sum));
        }

        @Override
        public OptionalLong scoreOf(UUID player) {
            Long score = held.get(player);
            return score == null ? OptionalLong.empty() : OptionalLong.of(score);
        }
    }
}
