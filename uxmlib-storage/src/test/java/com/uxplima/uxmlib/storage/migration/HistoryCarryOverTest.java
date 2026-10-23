package com.uxplima.uxmlib.storage.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Sql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A schema that kept its versions in the shared history moves to a history of its own without running a
 * migration twice, and a schema that never ran starts from nothing even when the shared history is full.
 */
class HistoryCarryOverTest {

    private static final List<Migration> SCHEMA = List.of(
            new Migration(1, "boards", "CREATE TABLE x_boards (id INTEGER PRIMARY KEY)"),
            new Migration(2, "scores", "ALTER TABLE x_boards ADD COLUMN score INTEGER NOT NULL DEFAULT 0"));

    private Database database;

    @BeforeEach
    void setUp() {
        database = Database.builder()
                .jdbcUrl("jdbc:sqlite:file:uxmlibcarry?mode=memory&cache=shared")
                .maxPoolSize(1)
                .build();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    @DisplayName("versions the shared history kept for this schema are carried over and not run again")
    void theSharedVersionsAreCarriedOver() {
        new MigrationRunner(database).apply(SCHEMA);
        MigrationRunner own = new MigrationRunner(database, "x_schema_history");

        own.carryOver(MigrationRunner.DEFAULT_HISTORY_TABLE, "x_boards");

        assertThat(own.currentVersion()).isEqualTo(2);
        assertThat(own.apply(SCHEMA)).isZero();
    }

    @Test
    @DisplayName("a shared history that holds another schema's versions is not read as this one's")
    void anotherSchemasHistoryIsNotCarried() {
        new MigrationRunner(database)
                .apply(List.of(new Migration(5, "other", "CREATE TABLE y_rows (id INTEGER PRIMARY KEY)")));
        MigrationRunner own = new MigrationRunner(database, "x_schema_history");

        own.carryOver(MigrationRunner.DEFAULT_HISTORY_TABLE, "x_boards");

        assertThat(own.currentVersion()).isZero();
        assertThat(own.apply(SCHEMA)).isEqualTo(2);
        assertThat(new Sql(database).queryFirst("SELECT COUNT(*) FROM x_boards", ps -> {}, r -> r.getInt(1)))
                .contains(0);
    }

    @Test
    @DisplayName("a history that already has rows is left alone")
    void anOwnHistoryIsNotOverwritten() {
        MigrationRunner own = new MigrationRunner(database, "x_schema_history");
        own.apply(SCHEMA.subList(0, 1));
        new MigrationRunner(database).apply(List.of(new Migration(9, "later", "CREATE TABLE z (id INTEGER)")));

        own.carryOver(MigrationRunner.DEFAULT_HISTORY_TABLE, "x_boards");

        assertThat(own.currentVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("the table prefix is written into every statement that names it")
    void thePrefixIsWrittenIn() {
        Migration written = new Migration(1, "boards", "CREATE TABLE {prefix}boards (id INTEGER); -- {prefix}");

        assertThat(written.withTablePrefix("uxmcrates_").sql())
                .isEqualTo("CREATE TABLE uxmcrates_boards (id INTEGER); -- uxmcrates_");
        assertThat(written.withTablePrefix("").sql()).isEqualTo("CREATE TABLE boards (id INTEGER); -- ");
    }
}
