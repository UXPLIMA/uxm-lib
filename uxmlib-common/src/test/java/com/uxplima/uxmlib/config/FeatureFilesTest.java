package com.uxplima.uxmlib.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A large feature keeps its settings in a file of its own, and a config.conf from before moves out once. */
class FeatureFilesTest {

    @TempDir
    Path folder;

    private Path old() throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, """
                language {
                  default = "tr"
                }

                # Mine, not the shipped words.
                first {
                  value = 7
                }

                second {
                  value = 8
                }

                menu {
                  rows = 6
                }
                """);
        return file;
    }

    @Test
    void aBlockMovesWithItsCommentAndTheRestStays() throws IOException {
        Path file = old();

        assertThat(FeatureFiles.moveOut(file, List.of("first", "second"))).containsExactly("first.conf", "second.conf");

        assertThat(Files.readString(folder.resolve("first.conf"))).startsWith("# Mine, not the shipped words.");
        HoconConfig config = HoconConfig.load(file);
        assertThat(config.nodeAt("first").virtual()).isTrue();
        assertThat(config.nodeAt("second").virtual()).isTrue();
        assertThat(config.getString("language.default", "")).isEqualTo("tr");
        assertThat(config.getInt("menu.rows", 0)).isEqualTo(6);
        assertThat(Files.readString(file)).doesNotContain("Mine");
    }

    @Test
    void aFeatureFileThatIsThereIsLeftAlone() throws IOException {
        Path file = old();
        Files.writeString(folder.resolve("first.conf"), "first { value = 1 }\n");

        assertThat(FeatureFiles.moveOut(file, List.of("first", "second"))).containsExactly("second.conf");

        assertThat(Files.readString(folder.resolve("first.conf"))).isEqualTo("first { value = 1 }\n");
        assertThat(HoconConfig.load(file).getInt("first.value", 0)).isEqualTo(7);
    }

    @Test
    void aReaderFindsAMovedKeyAtThePathItAlwaysHad() throws IOException {
        Path file = old();
        FeatureFiles.moveOut(file, List.of("first", "second"));

        HoconConfig config = HoconConfig.load(file);
        FeatureFiles.readInto(config, folder, List.of("first", "second"));

        assertThat(config.getInt("first.value", 0)).isEqualTo(7);
        assertThat(config.getInt("second.value", 0)).isEqualTo(8);
    }

    @Test
    void aFeatureFileGetsTheKeysAReleaseAddsAndKeepsItsValues() throws IOException {
        Files.writeString(folder.resolve("first.conf"), "first { value = 9 }\n");

        assertThat(FeatureFiles.bringUpToDate(
                        folder, List.of("first"), getClass().getClassLoader()))
                .containsExactly("first.conf");

        HoconConfig merged = HoconConfig.load(folder.resolve("first.conf"));
        assertThat(merged.getInt("first.value", 0)).isEqualTo(9);
        assertThat(merged.getBoolean("first.added", false)).isTrue();
        assertThat(ConfigBackups.of(folder.resolve("first.conf"))).hasSize(1);
    }
}
