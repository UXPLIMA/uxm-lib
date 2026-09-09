package com.uxplima.uxmlib.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DialectTest {

    private static final List<String> COLUMNS = List.of("uuid", "name", "coins");

    @Test
    void infersDialectFromJdbcUrl() {
        assertThat(Dialect.fromJdbcUrl("jdbc:sqlite:data.db")).isEqualTo(Dialect.SQLITE);
        assertThat(Dialect.fromJdbcUrl("jdbc:mysql://host/db")).isEqualTo(Dialect.MYSQL);
        assertThat(Dialect.fromJdbcUrl("jdbc:mariadb://host/db")).isEqualTo(Dialect.MYSQL);
        assertThat(Dialect.fromJdbcUrl("jdbc:postgresql://host/db")).isEqualTo(Dialect.POSTGRES);
        assertThat(Dialect.fromJdbcUrl("jdbc:h2:mem:test")).isEqualTo(Dialect.H2);
        assertThat(Dialect.fromJdbcUrl("jdbc:h2:./data/store")).isEqualTo(Dialect.H2);
    }

    @Test
    void h2UsesMergeIntoKey() {
        assertThat(Dialect.H2.upsert("players", "uuid", COLUMNS))
                .isEqualTo("MERGE INTO players (uuid, name, coins) KEY(uuid) VALUES (?, ?, ?)");
    }

    @Test
    void h2MergeHandlesAnIdOnlyTable() {
        assertThat(Dialect.H2.upsert("t", "id", List.of("id"))).isEqualTo("MERGE INTO t (id) KEY(id) VALUES (?)");
    }

    @Test
    void sqliteUsesOnConflictUpdate() {
        assertThat(Dialect.SQLITE.upsert("players", "uuid", COLUMNS))
                .isEqualTo("INSERT INTO players (uuid, name, coins) VALUES (?, ?, ?) "
                        + "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, coins = excluded.coins");
    }

    @Test
    void postgresMatchesTheOnConflictShape() {
        assertThat(Dialect.POSTGRES.upsert("players", "uuid", COLUMNS))
                .startsWith("INSERT INTO players (uuid, name, coins) VALUES (?, ?, ?)")
                .contains("ON CONFLICT(uuid) DO UPDATE SET name = excluded.name");
    }

    @Test
    void mysqlUsesOnDuplicateKeyUpdate() {
        assertThat(Dialect.MYSQL.upsert("players", "uuid", COLUMNS))
                .isEqualTo("INSERT INTO players (uuid, name, coins) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE name = VALUES(name), coins = VALUES(coins)");
    }

    @Test
    void anIdOnlyTableDoesNotTryToUpdate() {
        assertThat(Dialect.SQLITE.upsert("t", "id", List.of("id"))).endsWith("ON CONFLICT(id) DO NOTHING");
        assertThat(Dialect.MYSQL.upsert("t", "id", List.of("id"))).endsWith("ON DUPLICATE KEY UPDATE id = id");
    }

    @Test
    void aCompositeKeyNamesEveryKeyColumnInTheConflictClause() {
        List<String> columns = List.of("player_uuid", "tag_id", "granted_at");
        List<String> key = List.of("player_uuid", "tag_id");

        assertThat(Dialect.POSTGRES.upsert("grants", key, columns))
                .isEqualTo("INSERT INTO grants (player_uuid, tag_id, granted_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT(player_uuid, tag_id) DO UPDATE SET granted_at = excluded.granted_at");
        assertThat(Dialect.H2.upsert("grants", key, columns))
                .isEqualTo("MERGE INTO grants (player_uuid, tag_id, granted_at) "
                        + "KEY(player_uuid, tag_id) VALUES (?, ?, ?)");
        assertThat(Dialect.MYSQL.upsert("grants", key, columns))
                .endsWith("ON DUPLICATE KEY UPDATE granted_at = VALUES(granted_at)");
    }

    @Test
    void aKeyOnlyCompositeTableDoesNotTryToUpdate() {
        List<String> key = List.of("a", "b");
        assertThat(Dialect.SQLITE.upsert("t", key, key)).endsWith("ON CONFLICT(a, b) DO NOTHING");
    }

    @Test
    void aKeyColumnThatIsNotBoundIsRejected() {
        assertThatThrownBy(() -> Dialect.SQLITE.upsert("t", List.of("a", "missing"), List.of("a", "b")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dialect.SQLITE.upsert("t", List.of(), List.of("a")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void genericDialectHasNoPortableUpsert() {
        assertThatThrownBy(() -> Dialect.GENERIC.upsert("t", "uuid", COLUMNS))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void alterColumnTypeUsesEachBackendsSpelling() {
        assertThat(Dialect.POSTGRES.alterColumnType("players", "coins", "BIGINT"))
                .isEqualTo("ALTER TABLE players ALTER COLUMN coins TYPE BIGINT");
        assertThat(Dialect.H2.alterColumnType("players", "coins", "BIGINT"))
                .isEqualTo("ALTER TABLE players ALTER COLUMN coins BIGINT");
        assertThat(Dialect.MYSQL.alterColumnType("players", "coins", "BIGINT"))
                .isEqualTo("ALTER TABLE players MODIFY COLUMN coins BIGINT");
    }

    @Test
    void alterColumnTypeIsUnsupportedWhereThereIsNoPortableForm() {
        assertThatThrownBy(() -> Dialect.SQLITE.alterColumnType("t", "c", "BIGINT"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> Dialect.GENERIC.alterColumnType("t", "c", "BIGINT"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // -- the adding upsert, which is what a counter needs ------------------------------------------------

    /**
     * A counter written as a read and a write back is a lost update on every backend that does not serialise
     * its transactions. MySQL's default REPEATABLE READ takes no lock on a plain SELECT, so two openings at
     * the same moment both read one and both write two: a key spent twice, a win limit of one passed twice.
     */
    @Test
    @DisplayName("the conflict branch adds to the column instead of replacing it")
    void theAddingUpsertAdds() {
        List<String> columns = List.of("player_id", "reward_id", "wins", "counted_since");

        assertThat(Dialect.SQLITE.upsertAdding("t", List.of("player_id", "reward_id"), columns, List.of("wins")))
                .isEqualTo("INSERT INTO t (player_id, reward_id, wins, counted_since) VALUES (?, ?, ?, ?)"
                        + " ON CONFLICT(player_id, reward_id) DO UPDATE SET wins = t.wins + excluded.wins");
        assertThat(Dialect.MYSQL.upsertAdding("t", List.of("player_id", "reward_id"), columns, List.of("wins")))
                .isEqualTo("INSERT INTO t (player_id, reward_id, wins, counted_since) VALUES (?, ?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE wins = wins + VALUES(wins)");
    }

    /**
     * A column that is neither a key nor added is left as it stands. {@code counted_since} means the moment
     * the counting started, so an upsert that reset it on every win would move the window it names.
     */
    @Test
    @DisplayName("a column that is neither a key nor added is left exactly as it was")
    void anUntouchedColumnStays() {
        String sql = Dialect.SQLITE.upsertAdding(
                "t", List.of("id"), List.of("id", "wins", "counted_since"), List.of("wins"));

        assertThat(sql).doesNotContain("counted_since = ");
    }

    @Test
    @DisplayName("a backend with no portable form says so rather than emitting sql that silently replaces")
    void anUnsupportedBackendRefuses() {
        assertThatThrownBy(() -> Dialect.H2.upsertAdding("t", List.of("id"), List.of("id", "n"), List.of("n")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> Dialect.GENERIC.upsertAdding("t", List.of("id"), List.of("id", "n"), List.of("n")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("a key column cannot be added to, and an added column has to be bound")
    void theArgumentsAreChecked() {
        assertThatThrownBy(() -> Dialect.SQLITE.upsertAdding("t", List.of("id"), List.of("id"), List.of("id")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dialect.SQLITE.upsertAdding("t", List.of("id"), List.of("id"), List.of("n")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dialect.SQLITE.upsertAdding("t", List.of("id"), List.of("id", "n"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
