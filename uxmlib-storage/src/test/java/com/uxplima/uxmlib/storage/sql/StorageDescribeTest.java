package com.uxplima.uxmlib.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.uxplima.uxmlib.storage.migration.Migration;
import com.uxplima.uxmlib.storage.migration.SchemaIntrospector;
import com.uxplima.uxmlib.storage.testing.EveryBackend;
import com.uxplima.uxmlib.storage.testing.RealDatabase;
import org.junit.jupiter.api.DisplayName;

/** What a health check can say of a store: which backend, where, and how far its schema has come. */
final class StorageDescribeTest {

    private static final List<Migration> SCHEMA = List.of(
            new Migration(1, "names", "CREATE TABLE {prefix}names (id BIGINT PRIMARY KEY)"),
            new Migration(2, "more", "CREATE TABLE {prefix}more (id BIGINT PRIMARY KEY)"));

    @EveryBackend
    @DisplayName("a migrated store is described by its backend, where it is, its prefix and its schema version")
    void aMigratedStoreIsDescribed(RealDatabase real) {
        StorageSettings settings = real.settings();
        try (Database database = real.open()) {
            settings.migrate(database, SCHEMA, "names");

            String described = settings.describe(database);

            assertThat(described).startsWith(settings.backend().id() + " ");
            assertThat(described).contains(settings.backend().isFile() ? settings.file() : settings.database());
            assertThat(described).contains("prefix " + settings.tablePrefix()).endsWith("schema v2");
            assertThat(described).doesNotContain(settings.password().isEmpty() ? "\u0000" : settings.password());
        }
    }

    @EveryBackend
    @DisplayName("a store nothing has migrated says so, and asking leaves no history table behind")
    void anUnmigratedStoreIsLeftAsItIs(RealDatabase real) {
        StorageSettings settings = real.settings();
        try (Database database = real.open()) {
            String described = settings.describe(database);

            assertThat(described).endsWith("no schema recorded");
            assertThat(new SchemaIntrospector(database).tableExists(settings.historyTable()))
                    .isFalse();
        }
    }
}
