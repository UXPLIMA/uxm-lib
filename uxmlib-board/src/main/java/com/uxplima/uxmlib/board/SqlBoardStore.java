package com.uxplima.uxmlib.board;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Dialect;
import org.jspecify.annotations.Nullable;

/**
 * A {@link BoardStore} in the plugin's own SQL database, on SQLite, MySQL, MariaDB or PostgreSQL.
 *
 * <p>It reads and writes two tables the plugin creates in its own migrations, in this shape:
 *
 * <pre>{@code
 * CREATE TABLE {prefix}board (
 *     board_name   VARCHAR(32) NOT NULL,   -- as long as the longest board word
 *     board_period VARCHAR(16) NOT NULL,
 *     window_start BIGINT      NOT NULL,
 *     player_id    VARCHAR(36) NOT NULL,
 *     score        BIGINT      NOT NULL,
 *     reached_at   BIGINT      NOT NULL,
 *     PRIMARY KEY (board_name, board_period, window_start, player_id)
 * );
 * CREATE INDEX {prefix}board_rank ON {prefix}board (board_name, board_period, window_start, score);
 * CREATE INDEX {prefix}board_player ON {prefix}board (player_id);
 * CREATE TABLE {prefix}name (
 *     player_id VARCHAR(36) NOT NULL,
 *     name      VARCHAR(32) NOT NULL,
 *     PRIMARY KEY (player_id)
 * );
 * }</pre>
 *
 * A read that fails is written to the log and answers nothing, so a board shows empty for a refresh rather than
 * failing the timer that reads it. A write that fails is written to the log and dropped.
 */
public final class SqlBoardStore implements BoardStore {

    private static final List<String> KEYS = List.of("board_name", "board_period", "window_start", "player_id");
    private static final List<String> NAME_COLUMNS = List.of("player_id", "name");

    private final Database database;
    private final Logger logger;
    private final String namesTable;
    private final String addCounter;
    private final String addRecord;
    private final String selectTop;
    private final String countByScore;
    private final String selectPlayer;
    private final String deleteBefore;
    private final String deletePlayer;

    /**
     * @param boardTable the board table's full name, prefix included
     * @param namesTable the names table's full name, prefix included
     */
    public SqlBoardStore(Database database, String boardTable, String namesTable, Logger logger) {
        this.database = Objects.requireNonNull(database, "database");
        Objects.requireNonNull(boardTable, "boardTable");
        this.namesTable = Objects.requireNonNull(namesTable, "namesTable");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.addCounter = adding(database.dialect(), boardTable, false);
        this.addRecord = adding(database.dialect(), boardTable, true);
        this.selectTop = "SELECT b.player_id, b.score, p.name AS player_name FROM " + boardTable + " b"
                + " LEFT JOIN " + namesTable + " p ON p.player_id = b.player_id"
                + " WHERE b.board_name = ? AND b.board_period = ? AND b.window_start = ? AND b.score > 0"
                + " ORDER BY b.score DESC, b.reached_at ASC, b.player_id ASC LIMIT ?";
        this.countByScore = "SELECT score, COUNT(*) AS players FROM " + boardTable
                + " WHERE board_name = ? AND board_period = ? AND window_start = ? AND score > 0 GROUP BY score";
        this.selectPlayer = "SELECT board_name, score FROM " + boardTable
                + " WHERE player_id = ? AND board_period = ? AND window_start = ?";
        this.deleteBefore = "DELETE FROM " + boardTable + " WHERE board_period = ? AND window_start < ?";
        this.deletePlayer = "DELETE FROM " + boardTable + " WHERE player_id = ?";
    }

    @Override
    public void add(UUID player, Board board, Map<BoardPeriod, Long> windows, long amount, long at) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(windows, "windows");
        if (board.kind() == Board.Kind.SNAPSHOT) {
            throw new IllegalArgumentException("a snapshot board is not counted: " + board);
        }
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative, got: " + amount);
        }
        if (amount == 0 || windows.isEmpty()) {
            return;
        }
        String upsert = board.kind() == Board.Kind.RECORD ? addRecord : addCounter;
        try {
            database.inTransaction(sql -> windows.forEach((period, window) -> sql.update(upsert, statement -> {
                bindSlot(statement, board.word(), period, window);
                statement.setString(4, player.toString());
                statement.setLong(5, amount);
                statement.setLong(6, at);
            })));
        } catch (RuntimeException unwritable) {
            logger.log(Level.SEVERE, "Cannot add to the " + board + " board of " + player, unwritable);
        }
    }

    @Override
    public List<Ranked> top(Board board, BoardPeriod period, long window, int limit) {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(period, "period");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1, got: " + limit);
        }
        try {
            return database.transaction(sql -> sql.query(
                    selectTop,
                    statement -> {
                        bindSlot(statement, board.word(), period, window);
                        statement.setInt(4, limit);
                    },
                    row -> new Ranked(
                            UUID.fromString(row.getString("player_id")),
                            blankToNull(row.getString("player_name")),
                            row.getLong("score"))));
        } catch (RuntimeException unreadable) {
            logger.log(Level.SEVERE, "Cannot read the " + board + " " + period.word() + " board", unreadable);
            return List.of();
        }
    }

    @Override
    public Map<Long, Integer> countByScore(Board board, BoardPeriod period, long window) {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(period, "period");
        try {
            List<long[]> rows = database.transaction(sql ->
                    sql.query(countByScore, statement -> bindSlot(statement, board.word(), period, window), row ->
                            new long[] {row.getLong("score"), row.getLong("players")}));
            Map<Long, Integer> players = new HashMap<>();
            rows.forEach(row -> players.put(row[0], (int) row[1]));
            return Map.copyOf(players);
        } catch (RuntimeException unreadable) {
            logger.log(Level.SEVERE, "Cannot count the " + board + " " + period.word() + " board", unreadable);
            return Map.of();
        }
    }

    @Override
    public Map<Slot, Long> scoresOf(UUID player, Map<BoardPeriod, Long> windows) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(windows, "windows");
        Map<Slot, Long> held = new HashMap<>();
        try {
            database.inTransaction(sql -> windows.forEach((period, window) -> sql.query(
                            selectPlayer,
                            statement -> {
                                statement.setString(1, player.toString());
                                statement.setString(2, period.word());
                                statement.setLong(3, window);
                            },
                            row -> Map.entry(row.getString("board_name"), row.getLong("score")))
                    .forEach(score -> held.put(new Slot(score.getKey(), period, window), score.getValue()))));
        } catch (RuntimeException unreadable) {
            logger.log(Level.SEVERE, "Cannot read the boards of " + player, unreadable);
            return Map.of();
        }
        return Map.copyOf(held);
    }

    @Override
    public void forgetBefore(BoardPeriod period, long window) {
        Objects.requireNonNull(period, "period");
        try {
            database.inTransaction(sql -> sql.update(deleteBefore, statement -> {
                statement.setString(1, period.word());
                statement.setLong(2, window);
            }));
        } catch (RuntimeException unwritable) {
            logger.log(Level.SEVERE, "Cannot drop the old windows of the " + period.word() + " boards", unwritable);
        }
    }

    @Override
    public void erase(UUID player) {
        Objects.requireNonNull(player, "player");
        try {
            database.inTransaction(
                    sql -> sql.update(deletePlayer, statement -> statement.setString(1, player.toString())));
        } catch (RuntimeException unwritable) {
            logger.log(Level.SEVERE, "Cannot erase the boards of " + player, unwritable);
        }
    }

    @Override
    public void name(UUID player, String name) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(name, "name");
        String upsert = database.dialect().upsert(namesTable, "player_id", NAME_COLUMNS);
        try {
            database.inTransaction(sql -> sql.update(upsert, statement -> {
                statement.setString(1, player.toString());
                statement.setString(2, name);
            }));
        } catch (RuntimeException unwritable) {
            logger.log(Level.WARNING, "Cannot keep the name of " + player, unwritable);
        }
    }

    /**
     * The upsert that adds an event to a row in the database itself. A counter adds the amount and moves the time
     * it was reached; a record keeps the higher score and moves the time only when the score moves. MySQL assigns
     * left to right with the new values, so the time is set before the score it compares against.
     */
    static String adding(Dialect dialect, String table, boolean record) {
        String insert = "INSERT INTO " + table
                + " (board_name, board_period, window_start, player_id, score, reached_at) VALUES (?, ?, ?, ?, ?, ?)";
        String keys = String.join(", ", KEYS);
        return switch (dialect) {
            case SQLITE, POSTGRES -> {
                String old = table + ".";
                String best = dialect == Dialect.SQLITE ? "MAX" : "GREATEST";
                yield insert + " ON CONFLICT(" + keys + ") DO UPDATE SET "
                        + (record
                                ? "reached_at = CASE WHEN excluded.score > " + old + "score THEN excluded.reached_at"
                                        + " ELSE " + old + "reached_at END, score = " + best + "(" + old
                                        + "score, excluded.score)"
                                : "score = " + old + "score + excluded.score, reached_at = excluded.reached_at");
            }
            case MYSQL ->
                insert + " ON DUPLICATE KEY UPDATE "
                        + (record
                                ? "reached_at = IF(VALUES(score) > score, VALUES(reached_at), reached_at),"
                                        + " score = GREATEST(score, VALUES(score))"
                                : "score = score + VALUES(score), reached_at = VALUES(reached_at)");
            case H2, GENERIC ->
                throw new UnsupportedOperationException(
                        "boards need SQLite, MySQL, MariaDB or PostgreSQL, got: " + dialect);
        };
    }

    private static void bindSlot(PreparedStatement statement, String board, BoardPeriod period, long window)
            throws SQLException {
        statement.setString(1, board);
        statement.setString(2, period.word());
        statement.setLong(3, window);
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
