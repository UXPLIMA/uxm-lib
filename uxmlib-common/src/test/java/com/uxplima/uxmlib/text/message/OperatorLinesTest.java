package com.uxplima.uxmlib.text.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A line a server owner writes in a configuration file may name a catalogue entry instead of carrying its own
 * words. Each viewer reads the entry in their own language, so one item, menu or hologram file serves every
 * language the catalogue has.
 */
class OperatorLinesTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static Messages messages() {
        MessageCatalog catalog = new MessageCatalog(
                Map.of(
                        Locale.GERMAN,
                        Map.of("custom.join", "<accent>Beitreten {server}", "custom.count", "Spieler: <count>"),
                        Locale.ENGLISH,
                        Map.of("custom.join", "<accent>Join {server}", "custom.count", "Players: <count>")),
                Locale.ENGLISH);
        return new Messages(
                catalog,
                LocaleSource.ofDefault(Locale.ENGLISH),
                Map.of(),
                TagResolver.resolver("accent", Tag.styling(NamedTextColor.BLUE)));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void aCatalogLineReadsInTheViewersLanguage() {
        PlayerMock german = server.addPlayer();
        german.setLocale(Locale.GERMAN);
        PlayerMock english = server.addPlayer();
        english.setLocale(Locale.ENGLISH);

        assertThat(plain(messages().renderLine(german, "@custom.join server=skyblock")))
                .isEqualTo("Beitreten skyblock");
        assertThat(plain(messages().renderLine(english, "@custom.join server=skyblock")))
                .isEqualTo("Join skyblock");
    }

    /** An argument fills a brace token and a tag of the same name, so an entry may use either spelling. */
    @Test
    void anArgumentFillsATagAsWellAsABraceToken() {
        PlayerMock english = server.addPlayer();
        english.setLocale(Locale.ENGLISH);

        assertThat(plain(messages().renderLine(english, "@custom.count count=12")))
                .isEqualTo("Players: 12");
    }

    /** The owner's own words are drawn with the same house style the catalogue's are. */
    @Test
    void aLineWithItsOwnWordsIsDrawnInTheHouseStyle() {
        PlayerMock english = server.addPlayer();

        Component drawn = messages().renderLine(english, "<accent>Hello");

        assertThat(plain(drawn)).isEqualTo("Hello");
        assertThat(drawn.toString()).contains("blue");
    }

    @Test
    void aKeyNoCatalogueHoldsIsTheOwnersWords() {
        PlayerMock english = server.addPlayer();

        assertThat(plain(messages().renderLine(english, "@everyone"))).isEqualTo("@everyone");
        assertThat(messages().expandLine(english, "@everyone")).isEqualTo("@everyone");
    }

    @Test
    void expandHandsBackTheEntryForAPipelineOfItsOwn() {
        PlayerMock german = server.addPlayer();
        german.setLocale(Locale.GERMAN);

        assertThat(messages().expandLine(german, "@custom.join server=bedwars")).isEqualTo("<accent>Beitreten bedwars");
        assertThat(messages().isCatalogLine("@custom.join")).isTrue();
        assertThat(messages().isCatalogLine("@custom.missing")).isFalse();
    }

    @Test
    void theCatalogueDeclaresAKeyHeldInAnyLanguage() {
        MessageCatalog catalog = new MessageCatalog(
                Map.of(Locale.GERMAN, Map.of("only.german", "x"), Locale.ENGLISH, Map.of()), Locale.ENGLISH);

        assertThat(catalog.declares("only.german")).isTrue();
        assertThat(catalog.declares("nowhere")).isFalse();
    }

    /** A caller that knows only the language, a tooltip drawn for a locale, reads the same entry. */
    @Test
    void aLineExpandsForALanguageAsWellAsForAViewer() {
        assertThat(messages().expandLine(Locale.GERMAN, "@custom.join server=bedwars"))
                .isEqualTo("<accent>Beitreten bedwars");
        assertThat(messages().expandLine(Locale.ENGLISH, "own words")).isEqualTo("own words");
    }

    /**
     * A caller that knows the language and not the player, such as an item view drawing on a network thread, draws
     * the same line the viewer would read: the entry in that language, its arguments filled, the base style on it,
     * and {@code <player>} filled when a name is given.
     */
    @Test
    void aLineRendersForALanguageAsWellAsForAViewer() {
        Messages messages = messages();

        Component german = messages.renderLine(Locale.GERMAN, "Alex", "@custom.join server=skyblock");
        Component named = messages.renderLine(Locale.ENGLISH, "Alex", "<player> joined");
        Component nameless = messages.renderLine(Locale.ENGLISH, null, "@custom.count count=3");

        assertThat(plain(german)).isEqualTo("Beitreten skyblock");
        assertThat(german.color()).isEqualTo(NamedTextColor.BLUE);
        assertThat(plain(named)).isEqualTo("Alex joined");
        assertThat(plain(nameless)).isEqualTo("Players: 3");
    }
}
