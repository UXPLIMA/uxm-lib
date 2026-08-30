package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves that {@code commands.conf} is read and obeyed.
 *
 * <p>The guard matters more than it looks: a configuration file that nothing reads still looks correct in a
 * review, and an operator only finds out that the rename did nothing after they have restarted a live
 * server. This test fails the moment the wiring is lost.
 */
final class ConfiguredCommandsTest {

    @Test
    @DisplayName("a renamed command takes the name and the aliases from the file")
    void renameIsRead(@TempDir Path folder) throws IOException {
        Path file = write(
                folder,
                """
                commands {
                  example {
                    name    = "parla"
                    aliases = ["renk"]
                    enabled = true
                  }
                }
                """);

        ConfiguredCommands.Entry entry = ConfiguredCommands.load(file).entryOf("example", "example");

        assertThat(entry.name()).isEqualTo("parla");
        assertThat(entry.aliases()).containsExactly("renk");
        assertThat(entry.enabled()).isTrue();
    }

    @Test
    @DisplayName("a key the file does not mention keeps the fallback")
    void missingKeyFallsBack(@TempDir Path folder) throws IOException {
        Path file = write(folder, "commands { }\n");

        ConfiguredCommands.Entry entry = ConfiguredCommands.load(file).entryOf("example", "example");

        assertThat(entry.name()).isEqualTo("example");
        assertThat(entry.aliases()).isEmpty();
        assertThat(entry.enabled()).isTrue();
    }

    @Test
    @DisplayName("a file that is not there leaves every command as the handler declares it")
    void aMissingFileIsNotAnError(@TempDir Path folder) {
        ConfiguredCommands.Entry entry =
                ConfiguredCommands.load(folder.resolve("commands.conf")).entryOf("example", "example");

        assertThat(entry.name()).isEqualTo("example");
        assertThat(entry.enabled()).isTrue();
    }

    @Test
    @DisplayName("a command can be turned off entirely")
    void disabledCommandIsReported(@TempDir Path folder) throws IOException {
        Path file = write(folder, "commands { example { enabled = false } }\n");

        assertThat(ConfiguredCommands.load(file).isEnabled("example", "example"))
                .isFalse();
    }

    @Test
    @DisplayName("the replacer rewrites @FromConfig into the @Command the DSL registers")
    void theReplacerCarriesTheFileIntoTheAnnotation(@TempDir Path folder) throws IOException {
        Path file = write(
                folder,
                """
                commands {
                  example {
                    name    = "parla"
                    aliases = ["renk", "isik"]
                  }
                }
                """);

        List<java.lang.annotation.Annotation> replacements = ConfiguredCommands.load(file)
                .replacer()
                .replace(fromConfig("example", "example", "Shine"), Placeholder.class);
        Command command = (Command) replacements.get(0);

        assertThat(command.name()).isEqualTo("parla");
        assertThat(command.aliases()).containsExactly("renk", "isik");
        assertThat(command.description()).isEqualTo("Shine");
    }

    /** A class the replacer is asked about; the replacer reads only the annotation, never the element. */
    @SuppressWarnings("unused")
    private static final class Placeholder {}

    /** The annotation as the DSL would read it off a class, built through the library's own proxy. */
    private static FromConfig fromConfig(String value, String fallbackName, String description) {
        return Replacements.of(
                FromConfig.class, Map.of("value", value, "fallbackName", fallbackName, "description", description));
    }

    private static Path write(Path folder, String contents) throws IOException {
        Path file = folder.resolve("commands.conf");
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        return file;
    }
}
