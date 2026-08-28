package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.text.message.LocaleSource;
import org.junit.jupiter.api.Test;

/**
 * Covers the {@link CommandMessages} seam: the built-in defaults still say exactly the English the command
 * layer said before the seam existed (so adopting it is optional), an implementation receives the values and
 * the sender's locale rather than a finished sentence, and the registry carries the chosen implementation.
 */
class CommandMessagesTest {

    private static final CommandMessages EN = CommandMessages.english();
    private static final Locale TR = Locale.forLanguageTag("tr");

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void theDefaultsSayWhatTheCommandLayerAlwaysSaid() {
        assertThat(plain(EN.playerOnly(Locale.ENGLISH))).isEqualTo("Only a player can run this command.");
        assertThat(plain(EN.invalidValue(Locale.ENGLISH, "amount", "abc", "not a number")))
                .isEqualTo("Invalid value 'abc' for <amount>: not a number");
        assertThat(plain(EN.invalidValue(Locale.ENGLISH, "target", "Steve", "")))
                .isEqualTo("Invalid value 'Steve' for <target>");
        assertThat(plain(EN.notOneOf(Locale.ENGLISH, "mode", "banana", List.of("survival", "creative"))))
                .isEqualTo("Invalid value 'banana' for <mode>: expected one of survival, creative");
        assertThat(plain(EN.invalidArgument(Locale.ENGLISH, ""))).isEqualTo("Invalid argument.");
        assertThat(plain(EN.invalidArgument(Locale.ENGLISH, "no value for --page")))
                .isEqualTo("no value for --page");
        assertThat(plain(EN.internalError(Locale.ENGLISH)))
                .isEqualTo("An internal error occurred while running this command.");
        assertThat(plain(EN.onCooldown(Locale.ENGLISH, Duration.ofSeconds(20))))
                .isEqualTo("You must wait 20s before using this again.");
        assertThat(plain(EN.helpHeader(Locale.ENGLISH, "town", 2, 3))).isEqualTo("/town help (2/3)");
        assertThat(plain(EN.helpFillHint(Locale.ENGLISH))).isEqualTo("Click to fill in this command");
        assertThat(plain(EN.helpPageHint(Locale.ENGLISH, 4))).isEqualTo("Page 4");
    }

    @Test
    void aRefusalIsStillRedAndAHelpHeaderStillYellow() {
        assertThat(EN.playerOnly(Locale.ENGLISH).color()).isEqualTo(NamedTextColor.RED);
        assertThat(EN.internalError(Locale.ENGLISH).color()).isEqualTo(NamedTextColor.RED);
        assertThat(EN.helpHeader(Locale.ENGLISH, "town", 1, 1).color()).isEqualTo(NamedTextColor.YELLOW);
    }

    /** Turkish puts the words in a different order, which is the whole reason the values travel apart. */
    private static final class TurkishMessages implements CommandMessages {
        @Override
        public Component playerOnly(Locale locale) {
            return locale.getLanguage().equals("tr")
                    ? Component.text("Bu komutu yalnızca bir oyuncu kullanabilir.")
                    : CommandMessages.super.playerOnly(locale);
        }

        @Override
        public Component notOneOf(Locale locale, String argument, String input, List<String> allowed) {
            String list = String.join(", ", allowed);
            return Component.text("<" + argument + "> şunlardan biri olmalı: " + list + " ('" + input + "' değil)");
        }
    }

    @Test
    void anImplementationOrdersTheWordsItsOwnWay() {
        CommandMessages messages = new TurkishMessages();

        assertThat(plain(messages.notOneOf(TR, "mode", "banana", List.of("survival", "creative"))))
                .isEqualTo("<mode> şunlardan biri olmalı: survival, creative ('banana' değil)");
    }

    @Test
    void anImplementationSeesTheLocaleOfTheSenderItAnswers() {
        CommandMessages messages = new TurkishMessages();

        assertThat(plain(messages.playerOnly(TR))).isEqualTo("Bu komutu yalnızca bir oyuncu kullanabilir.");
        assertThat(plain(messages.playerOnly(Locale.ENGLISH))).isEqualTo("Only a player can run this command.");
    }

    @Test
    void everyLineNotOverriddenKeepsItsDefault() {
        CommandMessages messages = new TurkishMessages();

        assertThat(plain(messages.internalError(TR)))
                .isEqualTo("An internal error occurred while running this command.");
        assertThat(plain(messages.helpPageHint(TR, 2))).isEqualTo("Page 2");
    }

    @Test
    void theDefaultNotOneOfIsWordedByInvalidValueSoOverridingThatOneIsEnough() {
        CommandMessages messages = new CommandMessages() {
            @Override
            public Component invalidValue(Locale locale, String argument, String input, String reason) {
                return Component.text(argument + '=' + input + " reddedildi: " + reason);
            }
        };

        assertThat(plain(messages.notOneOf(TR, "mode", "banana", List.of("survival"))))
                .isEqualTo("mode=banana reddedildi: expected one of survival");
    }

    @Test
    void aRegistryAnswersInEnglishUntilItIsGivenSomethingElse() {
        ParamResolvers resolvers = ParamResolvers.withDefaults();

        assertThat(plain(resolvers.messages().playerOnly(Locale.ENGLISH)))
                .isEqualTo("Only a player can run this command.");
        assertThat(resolvers.locales().defaultLocale()).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void aRegistryCarriesTheMessagesAndLocaleSourceItWasGiven() {
        CommandMessages messages = new TurkishMessages();
        ParamResolvers resolvers = ParamResolvers.withDefaults().messages(messages).locales(LocaleSource.ofDefault(TR));

        assertThat(resolvers.messages()).isSameAs(messages);
        assertThat(resolvers.locales().defaultLocale()).isEqualTo(TR);
    }
}
