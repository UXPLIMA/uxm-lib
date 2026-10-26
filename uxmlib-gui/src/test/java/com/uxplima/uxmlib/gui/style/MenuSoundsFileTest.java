package com.uxplima.uxmlib.gui.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The sounds a menu plays, read from the host's own configuration file and read again on a reload.
 *
 * <p>A host hands the menu engine this file rather than a set of sounds, so an operator who edits the file and
 * reloads hears the change on the next click.
 */
class MenuSoundsFileTest {

    private static final String FILE = """
            menu {
              sounds {
                click  { name = "block.note_block.pling", volume = 0.6, pitch = 1.5 }
                page   { name = "item.book.page_turn", volume = 0.7, pitch = 1.0 }
                denied { name = "", volume = 0.6, pitch = 0.9 }
              }
            }
            """;

    @Test
    void theFileNamesTheSounds(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, FILE, StandardCharsets.UTF_8);

        MenuSounds sounds = MenuSoundsFile.read(file, "menu.sounds").get();

        assertThat(sounds.click().name().asString()).isEqualTo("minecraft:block.note_block.pling");
        assertThat(sounds.click().volume()).isEqualTo(0.6f);
        assertThat(sounds.page().pitch()).isEqualTo(1.0f);
        assertThat(sounds.denied().volume())
                .describedAs("an empty name is a sound turned off")
                .isZero();
    }

    @Test
    void aReloadReadsTheFileAgain(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, FILE, StandardCharsets.UTF_8);
        MenuSoundsFile sounds = MenuSoundsFile.read(file, "menu.sounds");

        Files.writeString(file, FILE.replace("block.note_block.pling", "ui.button.click"), StandardCharsets.UTF_8);
        sounds.reload();

        assertThat(sounds.get().click().name().asString()).isEqualTo("minecraft:ui.button.click");
    }
}
