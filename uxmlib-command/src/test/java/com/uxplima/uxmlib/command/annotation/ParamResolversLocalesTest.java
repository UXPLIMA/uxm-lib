package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;

import net.kyori.adventure.audience.Audience;

import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.Test;

class ParamResolversLocalesTest {

    private static final Locale TURKISH = Locale.forLanguageTag("tr");

    /** A plugin's own answer: everybody chose Turkish, whatever their client reads. */
    private static final LocaleSource CHOSE_TURKISH = new LocaleSource() {
        @Override
        public Locale defaultLocale() {
            return Locale.ENGLISH;
        }

        @Override
        public Locale localeOf(Audience viewer) {
            return TURKISH;
        }
    };

    private static Messages catalogue(LocaleSource locales) {
        return new Messages(new MessageCatalog(Map.of(Locale.ENGLISH, Map.of()), Locale.ENGLISH), locales);
    }

    @Test
    void aRegistryWithACatalogueAnswersInTheLanguageThePluginKnows() {
        ParamResolvers resolvers =
                ParamResolvers.empty().messages(CommandMessages.fromCatalogue(catalogue(CHOSE_TURKISH)));

        assertThat(resolvers.locales().localeOf(Audience.empty())).isEqualTo(TURKISH);
        assertThat(resolvers.locales().defaultLocale()).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void aSourceSetByHandStillWins() {
        LocaleSource german = LocaleSource.ofDefault(Locale.GERMAN);
        ParamResolvers resolvers = ParamResolvers.empty()
                .locales(german)
                .messages(CommandMessages.fromCatalogue(catalogue(CHOSE_TURKISH)));

        assertThat(resolvers.locales()).isSameAs(german);
    }

    @Test
    void withoutACatalogueTheClientIsRead() {
        assertThat(ParamResolvers.empty().locales().localeOf(Audience.empty())).isEqualTo(Locale.ENGLISH);
    }
}
