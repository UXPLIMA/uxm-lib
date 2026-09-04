package com.uxplima.uxmlib.text.language;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What a player may choose, and what happens when they do. The wording of the command is not ours. */
class LanguageChoicesTest {

    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final UUID WHO = UUID.randomUUID();

    @Test
    void theChoicesAreTheLanguagesThatHaveAFile(@TempDir Path folder) throws Exception {
        LanguageChoices choices = choicesIn(folder, "en", "tr", "pt-BR");

        assertThat(choices.available()).containsExactly(Locale.ENGLISH, BR, TR);
    }

    @Test
    void aServerWithNoFileStillOffersItsDefault(@TempDir Path folder) throws Exception {
        LanguageChoices choices = choicesIn(folder);

        assertThat(choices.available()).containsExactly(Locale.ENGLISH);
    }

    @Test
    void aTagIsReadWithEitherSeparatorAndAnyCase(@TempDir Path folder) throws Exception {
        LanguageChoices choices = choicesIn(folder, "en", "pt-BR");

        assertThat(choices.parse("pt_br")).contains(BR);
        assertThat(choices.parse("PT-BR")).contains(BR);
    }

    @Test
    void aLanguageWithOneCountryIsReachedByItsLanguageAlone(@TempDir Path folder) throws Exception {
        LanguageChoices choices = choicesIn(folder, "en", "pt-BR");

        assertThat(choices.parse("pt")).contains(BR);
    }

    @Test
    void aLanguageNobodyWroteAFileForIsNotAChoice(@TempDir Path folder) throws Exception {
        LanguageChoices choices = choicesIn(folder, "en", "tr");

        assertThat(choices.parse("de")).isEmpty();
        assertThat(choices.parse("nonsense")).isEmpty();
    }

    @Test
    void theSuggestionsAreTheTagsThatStartWithWhatWasTyped(@TempDir Path folder) throws Exception {
        LanguageChoices choices = choicesIn(folder, "en", "tr", "pt-BR");

        assertThat(choices.suggestions("")).containsExactly("en", "pt-BR", "tr");
        assertThat(choices.suggestions("p")).containsExactly("pt-BR");
        assertThat(choices.suggestions("z")).isEmpty();
    }

    @Test
    void choosingIsRememberedAndResettingForgetsIt(@TempDir Path folder) throws Exception {
        PlayerLanguages store = PlayerLanguages.inMemory();
        LanguageChoices choices = choicesIn(folder, store, "en", "tr");

        choices.choose(WHO, TR);
        assertThat(store.chosen(WHO)).contains(TR);

        choices.reset(WHO);
        assertThat(store.chosen(WHO)).isEmpty();
    }

    private static LanguageChoices choicesIn(Path folder, String... tags) throws Exception {
        return choicesIn(folder, PlayerLanguages.inMemory(), tags);
    }

    private static LanguageChoices choicesIn(Path folder, PlayerLanguages store, String... tags) throws Exception {
        Files.createDirectories(folder);
        for (String tag : tags) {
            Files.writeString(folder.resolve("messages_" + tag + ".conf"), "join { welcome = \"hi\" }\n");
        }
        LanguageSettings settings = LanguageSettings.following(Locale.ENGLISH);
        return new LanguageChoices(
                Languages.load(folder, Locale.ENGLISH), new LanguageResolver(settings, store), settings);
    }
}
