package com.uxplima.uxmlib.text.language;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A language is named in the words of the people who read it. */
class LanguageNamesTest {

    @Test
    @DisplayName("a language is named in its own language, and it starts with a capital")
    void alanguageNamesItself() {
        assertThat(LanguageNames.of(Locale.ENGLISH)).isEqualTo("English");
        assertThat(LanguageNames.of(Locale.forLanguageTag("tr"))).isEqualTo("Türkçe");
        assertThat(LanguageNames.of(Locale.GERMAN)).isEqualTo("Deutsch");
    }

    @Test
    @DisplayName("a language with a country carries the country, so two of them read apart")
    void acountryIsKept() {
        assertThat(LanguageNames.of(Locale.forLanguageTag("pt-BR"))).contains("Brasil");
    }

    @Test
    @DisplayName("a tag the runtime has no name for is written as the tag, with a capital")
    void anUnknownTagNamesItself() {
        assertThat(LanguageNames.of(Locale.forLanguageTag("qya"))).isEqualTo("Qya");
    }
}
