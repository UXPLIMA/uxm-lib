package com.uxplima.uxmlib.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

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
        assertThatThrownBy(() -> Dialect.GENERIC.upsert("t", "id", COLUMNS))
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
}
