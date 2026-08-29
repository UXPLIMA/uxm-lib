package com.uxplima.uxmlib.gui.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import com.uxplima.uxmlib.config.HoconConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The three sounds: what they are unconfigured, what a file changes, and how an operator turns one off. */
class MenuSoundsTest {

    @Test
    void theShippedSetIsTheOneTheClientIsKnownToPlay() {
        MenuSounds sounds = MenuSounds.defaults();

        assertThat(sounds.open().name().asString()).isEqualTo("minecraft:item.book.page_turn");
        assertThat(sounds.click().name().asString()).isEqualTo("minecraft:block.note_block.pling");
        assertThat(sounds.denied().name().asString()).isEqualTo("minecraft:block.note_block.bass");
        assertThat(sounds.click().volume()).isEqualTo(0.6f);
    }

    @Test
    void aFileChangesTheSoundsItNamesAndKeepsTheRest(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "menu { sounds { click { name = \"ui.button.click\", volume = 0.2 } } }\n");

        MenuSounds sounds = MenuSounds.from(HoconConfig.load(file), "menu.sounds");

        assertThat(sounds.click().name().asString()).isEqualTo("minecraft:ui.button.click");
        assertThat(sounds.click().volume()).isEqualTo(0.2f);
        assertThat(sounds.open().name().asString()).isEqualTo("minecraft:item.book.page_turn");
    }

    /** An empty name is how a server turns one sound off, and silence has to be exactly silent. */
    @Test
    void anEmptyNameIsSilence(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "menu { sounds { open { name = \"\" } } }\n");

        MenuSounds sounds = MenuSounds.from(HoconConfig.load(file), "menu.sounds");

        assertThat(sounds.open().volume()).isZero();
    }

    @Test
    void aMissingFileLeavesEverySoundAtItsShippedValue(@TempDir Path dir) {
        MenuSounds sounds = MenuSounds.from(HoconConfig.load(dir.resolve("absent.conf")), "menu.sounds");

        assertThat(sounds).isEqualTo(MenuSounds.defaults());
    }
}
