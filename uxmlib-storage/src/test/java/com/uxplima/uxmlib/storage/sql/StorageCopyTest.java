package com.uxplima.uxmlib.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.uxplima.uxmlib.storage.testing.EveryBackend;
import com.uxplima.uxmlib.storage.testing.RealDatabase;
import com.uxplima.uxmlib.storage.testing.RealDatabases;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;

/** A schema's rows carried from its SQLite file into a database on every backend a store can name. */
final class StorageCopyTest {

    private static final String PREFIX = RealDatabases.TABLE_PREFIX;

    private final List<AutoCloseable> opened = new CopyOnWriteArrayList<>();

    @AfterEach
    void closeEverything() throws Exception {
        for (AutoCloseable each : opened.reversed()) {
            each.close();
        }
    }

    @EveryBackend
    @DisplayName("every row of every table under the prefix arrives, each value as the target column holds it")
    void everyRowArrives(RealDatabase real) {
        Database file = sqliteFile();
        Database target = open(real);
        homes(file);
        homes(target);
        Sql source = new Sql(file);
        source.update("INSERT INTO t_homes (id, owner, name, hidden, x, icon) VALUES (?, ?, ?, ?, ?, ?)", binder -> {
            binder.setLong(1, 1);
            binder.setString(2, "00000000-0000-0000-0000-0000000000a1");
            binder.setString(3, "base");
            binder.setBoolean(4, true);
            binder.setDouble(5, 12.5);
            binder.setBytes(6, new byte[] {1, 2, 3});
        });
        source.update("INSERT INTO t_homes (id, owner, name, hidden, x, icon) VALUES (?, ?, ?, ?, ?, ?)", binder -> {
            binder.setLong(1, 2);
            binder.setString(2, "00000000-0000-0000-0000-0000000000a2");
            binder.setNull(3, java.sql.Types.VARCHAR);
            binder.setBoolean(4, false);
            binder.setDouble(5, -3.25);
            binder.setNull(6, java.sql.Types.BINARY);
        });

        StorageCopy.Copied copied = StorageCopy.copy(file, target, PREFIX);

        assertThat(copied.rows()).containsEntry("t_homes", 2);
        assertThat(copied.total()).isEqualTo(2);
        List<String> rows = new Sql(target)
                .query(
                        "SELECT id, owner, name, hidden, x, icon FROM t_homes ORDER BY id",
                        StatementBinder.NONE,
                        row -> row.getLong(1) + " " + row.getString(2) + " " + row.getString(3) + " "
                                + row.getBoolean(4) + " " + row.getDouble(5) + " "
                                + java.util.Arrays.toString(row.getBytes(6)));
        assertThat(rows)
                .containsExactly(
                        "1 00000000-0000-0000-0000-0000000000a1 base true 12.5 [1, 2, 3]",
                        "2 00000000-0000-0000-0000-0000000000a2 null false -3.25 null");
    }

    @EveryBackend
    @DisplayName("the history and the notices between servers stay behind, and so does another prefix's table")
    void bookkeepingStaysBehind(RealDatabase real) {
        Database file = sqliteFile();
        Database target = open(real);
        for (Database each : List.of(file, target)) {
            Sql sql = new Sql(each);
            sql.update("CREATE TABLE t_schema_history (version INTEGER PRIMARY KEY)", StatementBinder.NONE);
            sql.update("CREATE TABLE t_sync (id BIGINT PRIMARY KEY, body VARCHAR(64))", StatementBinder.NONE);
        }
        Sql source = new Sql(file);
        source.update("INSERT INTO t_schema_history (version) VALUES (1)", StatementBinder.NONE);
        source.update("INSERT INTO t_sync (id, body) VALUES (1, 'stale')", StatementBinder.NONE);
        source.update("CREATE TABLE other_rows (id BIGINT PRIMARY KEY)", StatementBinder.NONE);
        source.update("INSERT INTO other_rows (id) VALUES (1)", StatementBinder.NONE);

        StorageCopy.Copied copied = StorageCopy.copy(file, target, PREFIX);

        assertThat(copied.rows()).isEmpty();
        assertThat(count(target, "t_schema_history")).isZero();
        assertThat(count(target, "t_sync")).isZero();
    }

    @EveryBackend
    @DisplayName("a target that already holds rows is refused, and not one row is written to it")
    void aTargetWithRowsIsRefused(RealDatabase real) {
        Database file = sqliteFile();
        Database target = open(real);
        homes(file);
        homes(target);
        Sql sql = new Sql(target);
        sql.update("CREATE TABLE t_names (id BIGINT PRIMARY KEY)", StatementBinder.NONE);
        new Sql(file).update("CREATE TABLE t_names (id BIGINT PRIMARY KEY)", StatementBinder.NONE);
        new Sql(file).update("INSERT INTO t_names (id) VALUES (7)", StatementBinder.NONE);
        new Sql(file)
                .update(
                        "INSERT INTO t_homes (id, owner, hidden) VALUES (1, 'a', ?)",
                        binder -> binder.setBoolean(1, true));
        sql.update("INSERT INTO t_homes (id, owner, hidden) VALUES (9, 'b', ?)", binder -> binder.setBoolean(1, false));

        assertThatThrownBy(() -> StorageCopy.copy(file, target, PREFIX))
                .isInstanceOfSatisfying(StorageCopy.Refused.class, refused -> {
                    assertThat(refused.reason()).isEqualTo(StorageCopy.Reason.TARGET_HOLDS_ROWS);
                    assertThat(refused.table()).isEqualTo("t_homes");
                });
        assertThat(count(target, "t_names")).isZero();
        assertThat(count(target, "t_homes")).isEqualTo(1);
    }

    @EveryBackend
    @DisplayName("a table the target has not created is refused by name: the target is migrated first")
    void aMissingTableIsRefused(RealDatabase real) {
        Database file = sqliteFile();
        Database target = open(real);
        homes(file);

        assertThatThrownBy(() -> StorageCopy.copy(file, target, PREFIX))
                .isInstanceOfSatisfying(StorageCopy.Refused.class, refused -> {
                    assertThat(refused.reason()).isEqualTo(StorageCopy.Reason.TABLE_MISSING);
                    assertThat(refused.table()).isEqualTo("t_homes");
                })
                .hasMessageContaining("migrate");
    }

    @EveryBackend
    @DisplayName("a column the target does not have is refused by name rather than dropped")
    void aMissingColumnIsRefused(RealDatabase real) {
        Database file = sqliteFile();
        Database target = open(real);
        new Sql(file).update("CREATE TABLE t_rows (id BIGINT PRIMARY KEY, extra VARCHAR(8))", StatementBinder.NONE);
        new Sql(target).update("CREATE TABLE t_rows (id BIGINT PRIMARY KEY)", StatementBinder.NONE);

        assertThatThrownBy(() -> StorageCopy.copy(file, target, PREFIX))
                .isInstanceOfSatisfying(StorageCopy.Refused.class, refused -> {
                    assertThat(refused.reason()).isEqualTo(StorageCopy.Reason.COLUMN_MISSING);
                    assertThat(refused.table()).isEqualTo("t_rows");
                })
                .hasMessageContaining("extra");
    }

    @EveryBackend
    @DisplayName("a table another one points at is filled first, whatever the names sort as")
    void aParentIsFilledFirst(RealDatabase real) {
        Database file = sqliteFile();
        Database target = open(real);
        for (Database each : List.of(file, target)) {
            Sql sql = new Sql(each);
            sql.update("CREATE TABLE t_z_parent (id BIGINT PRIMARY KEY)", StatementBinder.NONE);
            sql.update(
                    // MySQL reads a REFERENCES written on the column and drops it, so the key is the table's.
                    "CREATE TABLE t_a_child (id BIGINT PRIMARY KEY, parent BIGINT NOT NULL,"
                            + " FOREIGN KEY (parent) REFERENCES t_z_parent (id))",
                    StatementBinder.NONE);
        }
        Sql source = new Sql(file);
        source.update("INSERT INTO t_z_parent (id) VALUES (1)", StatementBinder.NONE);
        source.update("INSERT INTO t_a_child (id, parent) VALUES (10, 1)", StatementBinder.NONE);

        StorageCopy.Copied copied = StorageCopy.copy(file, target, PREFIX);

        assertThat(copied.rows().keySet()).containsExactly("t_z_parent", "t_a_child");
        assertThat(count(target, "t_a_child")).isEqualTo(1);
    }

    @EveryBackend
    @DisplayName("a number the target counts by itself carries on after the highest one copied")
    void aCounterCarriesOn(RealDatabase real) {
        Database file = sqliteFile();
        Database target = open(real);
        new Sql(file)
                .update(
                        "CREATE TABLE t_log (id " + counter(Backend.SQLITE) + ", body VARCHAR(16))",
                        StatementBinder.NONE);
        new Sql(target)
                .update(
                        "CREATE TABLE t_log (id " + counter(real.backend()) + ", body VARCHAR(16))",
                        StatementBinder.NONE);
        Sql source = new Sql(file);
        source.update("INSERT INTO t_log (id, body) VALUES (5, 'five')", StatementBinder.NONE);
        source.update("INSERT INTO t_log (id, body) VALUES (9, 'nine')", StatementBinder.NONE);

        StorageCopy.copy(file, target, PREFIX);
        new Sql(target).update("INSERT INTO t_log (body) VALUES ('next')", StatementBinder.NONE);

        assertThat(new Sql(target)
                        .queryFirst(
                                "SELECT id FROM t_log WHERE body = 'next'",
                                StatementBinder.NONE,
                                row -> row.getLong(1)))
                .hasValue(10L);
    }

    @EveryBackend
    @DisplayName("a network store takes the rows of the file its block names, after the file is brought up to date")
    void aNetworkStoreTakesItsFile(RealDatabase real) {
        Database target = open(real);
        StorageSettings settings = real.settings();
        if (settings.backend().isFile()) {
            assertThatThrownBy(() -> StorageCopy.fromFile(settings, real.dataFolder(), StorageCopyTest::names, target))
                    .isInstanceOfSatisfying(
                            StorageCopy.Refused.class,
                            refused -> assertThat(refused.reason()).isEqualTo(StorageCopy.Reason.ALREADY_ON_FILE));
            return;
        }
        homes(target);
        names(target);
        try (Database file = settings.localFile().open(real.dataFolder(), "file")) {
            homes(file);
            new Sql(file)
                    .update(
                            "INSERT INTO t_homes (id, owner, hidden) VALUES (1, 'a', ?)",
                            binder -> binder.setBoolean(1, true));
        }

        StorageCopy.Copied copied = StorageCopy.fromFile(settings, real.dataFolder(), StorageCopyTest::names, target);

        assertThat(copied.rows()).containsEntry("t_homes", 1).containsEntry("t_names", 0);
        assertThat(count(target, "t_homes")).isEqualTo(1);
    }

    @EveryBackend
    @DisplayName("a network store with no file beside it is refused, and no empty file is left behind")
    void noFileIsRefused(RealDatabase real) throws java.io.IOException {
        Database target = open(real);
        StorageSettings settings = real.settings();
        java.nio.file.Path empty = java.nio.file.Files.createTempDirectory("uxmlib-copy-");
        opened.add(() -> java.nio.file.Files.deleteIfExists(empty));

        assertThatThrownBy(() -> StorageCopy.fromFile(settings, empty, StorageCopyTest::names, target))
                .isInstanceOfSatisfying(
                        StorageCopy.Refused.class,
                        refused -> assertThat(refused.reason())
                                .isEqualTo(
                                        settings.backend().isFile()
                                                ? StorageCopy.Reason.ALREADY_ON_FILE
                                                : StorageCopy.Reason.NO_FILE));
        assertThat(empty).isEmptyDirectory();
    }

    private static void names(Database database) {
        new Sql(database).update("CREATE TABLE IF NOT EXISTS t_names (id BIGINT PRIMARY KEY)", StatementBinder.NONE);
    }

    private static String counter(Backend backend) {
        return switch (backend) {
            case SQLITE -> "INTEGER PRIMARY KEY AUTOINCREMENT";
            case MYSQL, MARIADB -> "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY";
            case POSTGRESQL -> "BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY";
        };
    }

    private static void homes(Database database) {
        String bytes = database.dialect() == Dialect.POSTGRES ? "BYTEA" : "BLOB";
        new Sql(database)
                .update(
                        "CREATE TABLE t_homes (id BIGINT PRIMARY KEY, owner VARCHAR(36) NOT NULL, name VARCHAR(64),"
                                + " hidden BOOLEAN NOT NULL, x DOUBLE PRECISION, icon " + bytes + ")",
                        StatementBinder.NONE);
    }

    private static int count(Database database, String table) {
        return new Sql(database)
                .queryFirst("SELECT COUNT(*) FROM " + table, StatementBinder.NONE, row -> row.getInt(1))
                .orElseThrow();
    }

    private Database sqliteFile() {
        return open(RealDatabases.fresh(Backend.SQLITE));
    }

    private Database open(RealDatabase real) {
        opened.add(real);
        Database database = real.open();
        opened.add(database);
        return database;
    }
}
