package com.uxplima.uxmlib.text.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurateException;

/** One theme for the server, and what a plugin's own file may still say on top of it. */
class ThemeFilesTest {

    @Test
    void theSharedFileSitsBesideThePluginsThatReadIt(@TempDir Path root) {
        Path dataFolder = root.resolve("plugins").resolve("uxmTags");

        assertThat(ThemeFiles.shared(dataFolder))
                .isEqualTo(root.resolve("plugins").resolve("uxmTheme").resolve("theme.conf"));
    }

    @Test
    void neitherFileMeansTheShippedLook(@TempDir Path root) throws ConfigurateException {
        Theme theme = ThemeFiles.load(root.resolve("uxmTheme/theme.conf"), root.resolve("uxmTags/theme.conf"));

        assertThat(theme.hex("accent")).isEqualTo(Theme.defaults().hex("accent"));
    }

    @Test
    void theSharedFileIsReadWhenAPluginHasNoneOfItsOwn(@TempDir Path root) throws IOException, ConfigurateException {
        Path shared = write(root, "shared.conf", "palette { sky = \"#48cae4\" }\nroles { accent = sky }\n");

        Theme theme = ThemeFiles.load(shared, root.resolve("missing.conf"));

        assertThat(theme.hex("accent")).isEqualTo("#48cae4");
    }

    @Test
    void aPluginsOwnFileWinsKeyByKey(@TempDir Path root) throws IOException, ConfigurateException {
        Path shared = write(root, "shared.conf", "roles { accent = \"#48cae4\", value = \"#ffe66d\" }\n");
        Path own = write(root, "own.conf", "roles { accent = \"#ff0000\" }\n");

        Theme theme = ThemeFiles.load(shared, own);

        assertThat(theme.hex("accent")).isEqualTo("#ff0000");
        assertThat(theme.hex("value")).isEqualTo("#ffe66d");
    }

    private static Path write(Path root, String name, String content) throws IOException {
        Path file = root.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
