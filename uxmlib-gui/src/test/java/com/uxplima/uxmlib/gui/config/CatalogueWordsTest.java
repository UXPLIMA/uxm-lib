package com.uxplima.uxmlib.gui.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Locale;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalogLoader;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/** What a menu file writes, turned into what a player reads. */
class CatalogueWordsTest {

    private static final String CATALOGUE =
            """
            menu {
              lore { description = "About", details = "Facts" }
              tile {
                title = "English"
                crumb = "Language"
                description = "Read the server in this language."
                action = "Click to read in it."
                state { label = "State", value = "On" }
              }
            }
            """;

    private PlayerMock viewer;
    private CatalogueWords words;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
        Messages messages = new Messages(
                MessageCatalogLoader.fromNodes(Map.of(Locale.ENGLISH, parse(CATALOGUE)), Locale.ENGLISH),
                LocaleSource.ofDefault(Locale.ENGLISH));
        words = new CatalogueWords(messages, new Styler(Theme.defaults()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a tile line draws the blocks the catalogue holds")
    void aTileDrawsItsBlocks() {
        String drawn = plain(words.text(viewer, "tile:5 @menu.tile state", Map.of()));

        assertThat(drawn)
                .contains("English")
                .contains("Language")
                .contains("State")
                .contains("On");
        assertThat(drawn).contains("Click to read in it.");
    }

    @Test
    @DisplayName("a block the line takes out is not drawn")
    void aBlockCanBeLeftOut() {
        assertThat(plain(words.text(viewer, "tile:5 @menu.tile -action", Map.of())))
                .doesNotContain("Click to read in it.")
                .contains("English");
    }

    @Test
    @DisplayName("a line that names a key reads the catalogue, and one that does not is written as it stands")
    void aKeyIsReadAndAWordIsWritten() {
        assertThat(plain(words.text(viewer, "@menu.tile.title", Map.of()))).isEqualTo("English");
        assertThat(plain(words.text(viewer, "Ready", Map.of()))).isEqualTo("Ready");
    }

    @Test
    @DisplayName("a key nobody translated shows the key, so an operator sees what to write")
    void anUnknownKeyShowsItself() {
        assertThat(plain(words.text(viewer, "@menu.nothing", Map.of()))).isEqualTo("menu.nothing");
    }

    @Test
    @DisplayName("a value of a row is written into a line and never repaints it")
    void aValueGoesInAsAPlaceholder() {
        assertThat(words.line(viewer, "lang:choose <tag>", Map.of("tag", "tr"))).isEqualTo("lang:choose tr");
    }

    private static String plain(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }

    private static org.spongepowered.configurate.ConfigurationNode parse(String hocon) throws Exception {
        return HoconConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(hocon)))
                .build()
                .load();
    }
}
