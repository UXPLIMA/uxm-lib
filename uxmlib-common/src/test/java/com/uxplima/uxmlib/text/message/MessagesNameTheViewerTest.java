package com.uxplima.uxmlib.text.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * {@code <player>} in a catalogue line names the player it is drawn for, without a caller having to pass it. A
 * greeting once reached a player as the literal {@code <player>} because the one call site that drew it forgot
 * the argument, and nothing between the file and the screen noticed.
 */
class MessagesNameTheViewerTest {

    private static final MessageKey GREETING = MessageKey.of("join.greeting", "Hello <player>");

    private ServerMock server;
    private PlayerMock alice;
    private Messages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        alice = server.addPlayer("Alice");
        alice.setLocale(Locale.ENGLISH);
        MessageCatalog catalog =
                new MessageCatalog(Map.of(Locale.ENGLISH, Map.of("join.greeting", "Hello <player>")), Locale.ENGLISH);
        messages = new Messages(catalog, LocaleSource.ofDefault(Locale.ENGLISH));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aRenderedKeyNamesTheViewer() {
        assertThat(Text.plain(messages.render(alice, GREETING))).isEqualTo("Hello Alice");
    }

    @Test
    void aSentKeyNamesTheViewer() {
        messages.send(alice, GREETING);

        assertThat(Text.plain(alice.nextComponentMessage())).isEqualTo("Hello Alice");
    }

    @Test
    void anOperatorLineNamesTheViewer() {
        assertThat(Text.plain(messages.renderLine(alice, "Welcome back, <player>")))
                .isEqualTo("Welcome back, Alice");
    }

    @Test
    void aLineNamingACatalogueEntryNamesTheViewer() {
        assertThat(Text.plain(messages.renderLine(alice, "@join.greeting"))).isEqualTo("Hello Alice");
    }

    @Test
    void aCallersOwnPlayerWins() {
        assertThat(Text.plain(messages.render(alice, GREETING, Placeholder.unparsed("player", "Bob"))))
                .isEqualTo("Hello Bob");
    }

    @Test
    void aNameIsNeverReadAsMarkup() {
        PlayerMock tricky = server.addPlayer("x<red>y");
        tricky.setLocale(Locale.ENGLISH);

        assertThat(Text.plain(messages.render(tricky, GREETING))).isEqualTo("Hello x<red>y");
    }
}
