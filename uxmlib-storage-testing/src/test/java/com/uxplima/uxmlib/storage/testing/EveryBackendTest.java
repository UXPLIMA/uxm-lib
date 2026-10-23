package com.uxplima.uxmlib.storage.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.uxplima.uxmlib.storage.migration.Migration;
import com.uxplima.uxmlib.storage.migration.MigrationRunner;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Sql;

/**
 * What a plugin does with a configured store, on every backend: open it, build a prefixed schema, carry an
 * old shared history over, and write and read a row through the dialect's upsert.
 */
class EveryBackendTest {

    private static final List<Migration> SCHEMA = List.of(
            new Migration(
                    1,
                    "rows",
                    "CREATE TABLE {prefix}rows (id VARCHAR(36) NOT NULL PRIMARY KEY, score BIGINT NOT NULL)"),
            new Migration(2, "index", "CREATE INDEX {prefix}rows_score ON {prefix}rows (score)"));

    @EveryBackend
    void aConfiguredStoreBuildsItsSchemaAndKeepsARow(RealDatabase real) {
        try (Database database = real.open()) {
            MigrationRunner runner =
                    new MigrationRunner(database, real.settings().historyTable());
            runner.apply(SCHEMA.stream()
                    .map(m -> m.withTablePrefix(real.settings().tablePrefix()))
                    .toList());
            Sql sql = new Sql(database);
            String table = real.settings().table("rows");
            String upsert = database.dialect().upsert(table, "id", List.of("id", "score"));
            sql.update(upsert, ps -> {
                ps.setString(1, "a");
                ps.setLong(2, 1L);
            });
            sql.update(upsert, ps -> {
                ps.setString(1, "a");
                ps.setLong(2, 7L);
            });

            assertThat(sql.queryFirst(
                            "SELECT score FROM " + table + " WHERE id = ?",
                            ps -> ps.setString(1, "a"),
                            r -> r.getLong(1)))
                    .contains(7L);
            assertThat(runner.currentVersion()).isEqualTo(2);
        }
    }

    @EveryBackend
    void aSharedHistoryIsCarriedOverOnce(RealDatabase real) {
        try (Database database = real.open()) {
            List<Migration> schema = SCHEMA.stream()
                    .map(m -> m.withTablePrefix(real.settings().tablePrefix()))
                    .toList();
            new MigrationRunner(database).apply(schema);
            MigrationRunner own = new MigrationRunner(database, real.settings().historyTable());

            assertThat(own.carryOver(
                            MigrationRunner.DEFAULT_HISTORY_TABLE,
                            real.settings().table("rows")))
                    .isEqualTo(2);
            assertThat(own.apply(schema)).isZero();
            assertThat(own.carryOver(
                            MigrationRunner.DEFAULT_HISTORY_TABLE,
                            real.settings().table("rows")))
                    .isZero();
        }
    }
}
