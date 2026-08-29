package com.uxplima.uxmlib.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the skip-bad-row load against a real in-memory SQLite database: a mapper that throws on a
 * sentinel row must not abort the whole load: the good rows come back and the bad one is counted, not fatal.
 */
class ResilientLoadTest {

    private Database database;
    private Sql sql;

    @BeforeEach
    void setUp() {
        database = Database.builder()
                .jdbcUrl("jdbc:sqlite:file:uxmlibresilient?mode=memory&cache=shared")
                .maxPoolSize(1)
                .build();
        sql = new Sql(database);
        sql.execute("CREATE TABLE players (id INTEGER PRIMARY KEY, name TEXT NOT NULL)");
        insertPlayer(1, "Steve");
        insertPlayer(2, "__corrupt__");
        insertPlayer(3, "Alex");
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void skipsTheRowThatTheMapperRejectsAndKeepsTheRest() {
        LoadResult<String> loaded = sql.queryResilient(
                "SELECT name FROM players ORDER BY id", StatementBinder.NONE, ResilientLoadTest::mapName);

        assertThat(loaded.rows()).containsExactly("Steve", "Alex");
        assertThat(loaded.skipped()).isEqualTo(1);
    }

    @Test
    void countsEveryBadRowWhenSeveralAreMalformed() {
        insertPlayer(4, "__corrupt__");

        LoadResult<String> loaded = sql.queryResilient(
                "SELECT name FROM players ORDER BY id", StatementBinder.NONE, ResilientLoadTest::mapName);

        assertThat(loaded.rows()).containsExactly("Steve", "Alex");
        assertThat(loaded.skipped()).isEqualTo(2);
    }

    @Test
    void reportsZeroSkippedWhenEveryRowIsGood() {
        sql.update("DELETE FROM players WHERE name = ?", ps -> ps.setString(1, "__corrupt__"));

        LoadResult<String> loaded = sql.queryResilient(
                "SELECT name FROM players ORDER BY id", StatementBinder.NONE, ResilientLoadTest::mapName);

        assertThat(loaded.rows()).containsExactly("Steve", "Alex");
        assertThat(loaded.skipped()).isZero();
    }

    private static String mapName(java.sql.ResultSet row) throws SQLException {
        String name = row.getString("name");
        if ("__corrupt__".equals(name)) {
            throw new IllegalStateException("malformed row: " + name);
        }
        return name;
    }

    private void insertPlayer(int id, String name) {
        sql.update("INSERT INTO players (id, name) VALUES (?, ?)", ps -> {
            ps.setInt(1, id);
            ps.setString(2, name);
        });
    }
}
