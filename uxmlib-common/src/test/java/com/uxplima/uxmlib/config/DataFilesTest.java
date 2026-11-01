package com.uxplima.uxmlib.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What a plugin writes for itself lives in data/, and a database from before moves there with its journal. */
class DataFilesTest {

    @TempDir
    Path folder;

    @Test
    void anOldDatabaseMovesWithItsJournal() throws IOException {
        Files.writeString(folder.resolve("rows.db"), "rows");
        Files.writeString(folder.resolve("rows.db-wal"), "wal");

        Path now = DataFiles.resolve(folder, "rows.db");

        assertThat(now).isEqualTo(folder.resolve("data").resolve("rows.db"));
        assertThat(Files.readString(now)).isEqualTo("rows");
        assertThat(Files.readString(folder.resolve("data").resolve("rows.db-wal")))
                .isEqualTo("wal");
        assertThat(folder.resolve("rows.db")).doesNotExist();
    }

    @Test
    void aFileAlreadyInDataIsNotOverwritten() throws IOException {
        Files.createDirectories(folder.resolve("data"));
        Files.writeString(folder.resolve("data").resolve("rows.db"), "new");
        Files.writeString(folder.resolve("rows.db"), "old");

        Path now = DataFiles.resolve(folder, "rows.db");

        assertThat(Files.readString(now)).isEqualTo("new");
        assertThat(folder.resolve("rows.db")).exists();
    }

    @Test
    void aFolderOfLogsMovesWhole() throws IOException {
        Files.createDirectories(folder.resolve("logs"));
        Files.writeString(folder.resolve("logs").resolve("today.log"), "line");

        Path now = DataFiles.resolve(folder, "logs");

        assertThat(Files.readString(now.resolve("today.log"))).isEqualTo("line");
    }

    @Test
    void aNameUnderDataIsUsedAsItIs() {
        assertThat(DataFiles.resolve(folder, "data/rows.db")).isEqualTo(folder.resolve("data/rows.db"));
    }
}
