package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.Test;

/**
 * Covers the command layer reading its own lines out of a consumer's catalog: a translated line is used,
 * a line the file does not hold falls back to the key's default, and whatever the sender typed is shown
 * rather than parsed.
 */
class CatalogueCommandMessagesTest {

    private static final Locale TR = Locale.forLanguageTag("tr");

    @Test
    void aTranslatedLineComesOutInThatLanguage() {
        CommandMessages messages =
                messagesHolding(Map.of(TR, Map.of("command.player-only", "Bunu sadece bir oyuncu yazabilir.")));

        assertThat(Text.plain(messages.playerOnly(TR))).isEqualTo("Bunu sadece bir oyuncu yazabilir.");
    }

    @Test
    void aLineTheFileDoesNotHoldFallsBackToTheKeyDefault() {
        CommandMessages messages = messagesHolding(Map.of());

        // The default still carries its style tokens: a catalog is styled when it is loaded, and this one
        // was never loaded through a Styler. What matters here is that the sentence is the key's own.
        assertThat(Text.plain(messages.playerOnly(Locale.ENGLISH))).contains("Only a player can run this command.");
    }

    @Test
    void whatTheSenderTypedIsShownAndNeverObeyed() {
        CommandMessages messages =
                messagesHolding(Map.of(Locale.ENGLISH, Map.of("command.invalid-value", "no: <input> for <argument>")));

        String shown = Text.plain(messages.invalidValue(Locale.ENGLISH, "player", "<red>gotcha</red>", ""));

        assertThat(shown).isEqualTo("no: <red>gotcha</red> for player");
    }

    @Test
    void aListOfAllowedValuesIsJoinedForTheReader() {
        CommandMessages messages =
                messagesHolding(Map.of(Locale.ENGLISH, Map.of("command.not-one-of", "<input>: try <allowed>")));

        String shown = Text.plain(messages.notOneOf(Locale.ENGLISH, "mode", "spin", List.of("on", "off")));

        assertThat(shown).isEqualTo("spin: try on, off");
    }

    private static CommandMessages messagesHolding(Map<Locale, Map<String, String>> files) {
        MessageCatalog catalog = new MessageCatalog(files, Locale.ENGLISH);
        return CommandMessages.fromCatalogue(new Messages(catalog, LocaleSource.ofDefault(Locale.ENGLISH)));
    }
}
