package com.uxplima.uxmlib.board;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.testing.EveryBackend;
import com.uxplima.uxmlib.storage.testing.RealDatabase;

class SqlBoardStoreTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final Map<BoardPeriod, Long> WINDOWS = Map.of(BoardPeriod.ALLTIME, 0L, BoardPeriod.WEEKLY, 100L);

    private static SqlBoardStore store(RealDatabase real, Database database) {
        String board = real.settings().table("board");
        String names = real.settings().table("name");
        database.inTransaction(sql -> {
            sql.execute(
                    "CREATE TABLE " + board + " (board_name VARCHAR(16) NOT NULL, board_period VARCHAR(16) NOT NULL,"
                            + " window_start BIGINT NOT NULL, player_id VARCHAR(36) NOT NULL, score BIGINT NOT NULL,"
                            + " reached_at BIGINT NOT NULL, PRIMARY KEY (board_name, board_period, window_start, player_id))");
            sql.execute("CREATE TABLE " + names + " (player_id VARCHAR(36) NOT NULL, name VARCHAR(32) NOT NULL,"
                    + " PRIMARY KEY (player_id))");
        });
        return new SqlBoardStore(database, board, names, Logger.getLogger("test"));
    }

    @EveryBackend
    void aCounterAddsInTheDatabase(RealDatabase real) {
        try (Database database = real.open()) {
            SqlBoardStore store = store(real, database);
            Board kills = Board.counter("kills");

            store.add(ALEX, kills, WINDOWS, 2, 10);
            store.add(ALEX, kills, WINDOWS, 3, 20);
            store.add(SAM, kills, WINDOWS, 5, 15);
            store.name(ALEX, "Alex");

            List<Ranked> top = store.top(kills, BoardPeriod.WEEKLY, 100, 10);
            assertThat(top).extracting(Ranked::player).containsExactly(SAM, ALEX);
            assertThat(top).extracting(Ranked::score).containsExactly(5L, 5L);
            assertThat(top.get(1).name()).isEqualTo("Alex");
            assertThat(store.countByScore(kills, BoardPeriod.ALLTIME, 0)).isEqualTo(Map.of(5L, 2));
        }
    }

    @EveryBackend
    void aRecordKeepsTheBestAndWhenItWasReached(RealDatabase real) {
        try (Database database = real.open()) {
            SqlBoardStore store = store(real, database);
            Board best = Board.record("best");

            store.add(ALEX, best, WINDOWS, 30, 10);
            store.add(ALEX, best, WINDOWS, 20, 20);
            store.add(SAM, best, WINDOWS, 30, 15);

            assertThat(store.top(best, BoardPeriod.ALLTIME, 0, 10))
                    .extracting(Ranked::player, Ranked::score)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(ALEX, 30L),
                            org.assertj.core.groups.Tuple.tuple(SAM, 30L));
            store.add(SAM, best, WINDOWS, 31, 30);
            assertThat(store.top(best, BoardPeriod.ALLTIME, 0, 1))
                    .extracting(Ranked::player)
                    .containsExactly(SAM);
        }
    }

    @EveryBackend
    void aPlayersScoresWindowsAndErasure(RealDatabase real) {
        try (Database database = real.open()) {
            SqlBoardStore store = store(real, database);
            Board kills = Board.counter("kills");
            Board best = Board.record("best");
            store.add(ALEX, kills, WINDOWS, 4, 10);
            store.add(ALEX, best, Map.of(BoardPeriod.WEEKLY, 100L), 9, 10);
            store.add(ALEX, kills, Map.of(BoardPeriod.WEEKLY, 50L), 1, 5);

            assertThat(store.scoresOf(ALEX, WINDOWS))
                    .isEqualTo(Map.of(
                            new BoardStore.Slot("kills", BoardPeriod.ALLTIME, 0), 4L,
                            new BoardStore.Slot("kills", BoardPeriod.WEEKLY, 100), 4L,
                            new BoardStore.Slot("best", BoardPeriod.WEEKLY, 100), 9L));

            store.forgetBefore(BoardPeriod.WEEKLY, 100);
            assertThat(store.top(kills, BoardPeriod.WEEKLY, 50, 10)).isEmpty();
            assertThat(store.top(kills, BoardPeriod.WEEKLY, 100, 10)).hasSize(1);

            store.erase(ALEX);
            assertThat(store.scoresOf(ALEX, WINDOWS)).isEmpty();
        }
    }
}
