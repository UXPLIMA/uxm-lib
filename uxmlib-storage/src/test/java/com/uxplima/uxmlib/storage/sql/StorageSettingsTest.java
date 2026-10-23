package com.uxplima.uxmlib.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.uxplima.uxmlib.config.HoconConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageSettingsTest {

    @TempDir
    @SuppressWarnings("NullAway.Init") // JUnit writes it before each test
    Path folder;

    private HoconConfig config(String text) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return HoconConfig.load(file);
    }

    @Test
    @DisplayName("an absent block is a SQLite file in the data folder under the shipped prefix")
    void anAbsentBlockIsTheShippedFile() throws IOException {
        StorageSettings settings = StorageSettings.read(config(""), "storage", "glow.db", "uxmglow_");

        assertThat(settings.backend()).isEqualTo(Backend.SQLITE);
        assertThat(settings.file()).isEqualTo("glow.db");
        assertThat(settings.tablePrefix()).isEqualTo("uxmglow_");
        assertThat(settings.jdbcUrl(folder)).isEqualTo("jdbc:sqlite:" + folder.resolve("glow.db"));
    }

    @Test
    @DisplayName("each named backend builds the URL its driver reads")
    void eachBackendBuildsItsUrl() throws IOException {
        StorageSettings postgres = StorageSettings.read(
                config("storage { type = postgresql, host = db.local, database = network }"), "storage", "x.db", "x_");
        StorageSettings maria =
                StorageSettings.read(config("storage { type = MariaDB, port = 3307 }"), "storage", "x.db", "x_");
        StorageSettings mysql = StorageSettings.read(config("storage { type = mysql }"), "storage", "x.db", "x_");

        assertThat(postgres.jdbcUrl(folder)).isEqualTo("jdbc:postgresql://db.local:5432/network");
        assertThat(maria.jdbcUrl(folder)).isEqualTo("jdbc:mariadb://localhost:3307/minecraft");
        assertThat(mysql.jdbcUrl(folder)).isEqualTo("jdbc:mysql://localhost:3306/minecraft");
    }

    @Test
    @DisplayName("postgres is read as postgresql, and a name nobody ships is refused rather than read as SQLite")
    void anUnknownTypeIsRefused() throws IOException {
        assertThat(Backend.named("postgres")).isEqualTo(Backend.POSTGRESQL);
        HoconConfig typo = config("storage { type = mysqll }");

        assertThatThrownBy(() -> StorageSettings.read(typo, "storage", "x.db", "x_"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mysqll")
                .hasMessageContaining("postgresql");
    }

    @Test
    @DisplayName("a table prefix is a bare identifier, since it is written into every statement")
    void aPrefixIsABareIdentifier() throws IOException {
        HoconConfig quoted = config("storage { table-prefix = \"x; DROP TABLE y\" }");

        assertThatThrownBy(() -> StorageSettings.read(quoted, "storage", "x.db", "x_"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("table-prefix");
        assertThat(StorageSettings.read(config("storage { table-prefix = \"\" }"), "storage", "x.db", "x_")
                        .table("boards"))
                .isEqualTo("boards");
    }

    @Test
    @DisplayName("the history of the migrations is kept under the prefix, so two schemas never share one")
    void theHistoryIsUnderThePrefix() throws IOException {
        StorageSettings settings = StorageSettings.read(config(""), "storage", "x.db", "uxmcrates_");

        assertThat(settings.historyTable()).isEqualTo("uxmcrates_schema_history");
    }

    @Test
    @DisplayName("a SQLite file opens and answers")
    void aFileOpens() throws IOException {
        StorageSettings settings = StorageSettings.read(config("storage { pool-size = 3 }"), "storage", "x.db", "x_");

        try (Database database = settings.open(folder, "storage-settings-test")) {
            assertThat(database.dialect()).isEqualTo(Dialect.SQLITE);
            assertThat(database.ping()).isTrue();
        }
        assertThat(folder.resolve("x.db")).exists();
    }
}
