package com.uxplima.uxmlib.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A copy is kept in backups/ before a file is written again, and only the last five of a file stay. */
class ConfigBackupsTest {

    @TempDir
    Path folder;

    private static Clock at(int minute) {
        return Clock.fixed(Instant.parse("2026-11-02T03:00:00Z").plusSeconds(60L * minute), ZoneOffset.UTC);
    }

    @Test
    void theLastFiveCopiesOfAFileAreKept() throws IOException {
        Path file = folder.resolve("config.conf");
        for (int minute = 0; minute < 7; minute++) {
            Files.writeString(file, "value = " + minute + "\n");
            ConfigBackups.keep(file, at(minute));
        }

        assertThat(ConfigBackups.of(file)).hasSize(ConfigBackups.KEPT);
        assertThat(ConfigBackups.of(file).get(0).getFileName()).hasToString("config-2026-11-02-030200.conf");
        assertThat(Files.readString(ConfigBackups.of(file).get(4))).isEqualTo("value = 6\n");
    }

    @Test
    void eachFileKeepsItsOwnCopies() throws IOException {
        Path config = folder.resolve("config.conf");
        Path kit = folder.resolve("kit.conf");
        Files.writeString(config, "a = 1\n");
        Files.writeString(kit, "b = 1\n");
        for (int minute = 0; minute < 6; minute++) {
            ConfigBackups.keep(config, at(minute));
        }

        ConfigBackups.keep(kit, at(0));

        assertThat(ConfigBackups.of(config)).hasSize(5);
        assertThat(ConfigBackups.of(kit)).hasSize(1);
    }

    @Test
    void theCopyAnEarlierReleaseKeptBesideTheFileMovesIn() throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, "now = 1\n");
        Files.writeString(folder.resolve("config.conf.bak"), "before = 1\n");

        ConfigBackups.keep(file, at(0));

        assertThat(folder.resolve("config.conf.bak")).doesNotExist();
        assertThat(ConfigBackups.of(file)).hasSize(2);
    }

    @Test
    void aMergeThatAddsKeepsItsCopyInBackups() throws IOException {
        Path file = folder.resolve("first.conf");
        Files.writeString(file, "first { value = 9 }\n");

        assertThat(HoconConfig.load(file).mergeDefaults("first.conf", getClass().getClassLoader()))
                .isTrue();

        assertThat(folder.resolve("first.conf.bak")).doesNotExist();
        assertThat(ConfigBackups.of(file))
                .singleElement()
                .satisfies(copy -> assertThat(Files.readString(copy)).isEqualTo("first { value = 9 }\n"));
    }
}
