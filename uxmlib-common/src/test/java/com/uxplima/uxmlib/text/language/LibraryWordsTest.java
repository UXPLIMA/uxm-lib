package com.uxplima.uxmlib.text.language;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.uxplima.uxmlib.text.message.MessageKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The floor under a plugin's language files: the words the library's own windows ask for.
 *
 * <p>The menu engine draws screens nobody wrote a file for, and it asks the plugin's catalogue for their
 * words. Every plugin but one answered nothing, because nothing told them the obligation existed: a player
 * who pressed escape out of an editor prompt read the literal text {@code gui.input.cancelled} in chat, and
 * the owner found it by playing rather than by any test failing.
 *
 * <p>So the library ships the words for its own keys and they are read underneath the plugin's files. A
 * plugin that writes the key keeps its own line, which is how uxmEssentials keeps the wording it has. A
 * plugin that writes nothing gets words instead of a key, in every language both sides have a file for.
 */
class LibraryWordsTest {

    private static final MessageKey CANCELLED = MessageKey.of("gui.input.cancelled", "gui.input.cancelled");
    private static final MessageKey CONFIRM_YES = MessageKey.of("gui.confirm.yes", "gui.confirm.yes");
    private static final Locale TR = Locale.forLanguageTag("tr");

    @Test
    void aPluginThatWritesNoneOfTheseStillAnswersWords(@TempDir Path folder) throws Exception {
        write(folder, "en", "crate { opened = \"Opened\" }");

        Languages languages = Languages.load(folder, Locale.ENGLISH);

        assertThat(languages.catalog().template(CANCELLED, Locale.ENGLISH))
                .describedAs("the key itself on a player's screen is the defect this closes")
                .isNotEqualTo(CANCELLED.path())
                .contains("cancelled");
        assertThat(languages.catalog().template(CONFIRM_YES, Locale.ENGLISH)).isNotEqualTo(CONFIRM_YES.path());
    }

    @Test
    void theWordsFollowTheLanguageTheViewerReadsIn(@TempDir Path folder) throws Exception {
        write(folder, "en", "crate { opened = \"Opened\" }");
        write(folder, "tr", "crate { opened = \"Acildi\" }");

        Languages languages = Languages.load(folder, Locale.ENGLISH);

        assertThat(languages.catalog().template(CANCELLED, TR))
                .describedAs("a Turkish player reading one English line is half a translation")
                .isNotEqualTo(CANCELLED.path())
                .isNotEqualTo(languages.catalog().template(CANCELLED, Locale.ENGLISH));
    }

    @Test
    void aPluginThatWritesOneOfThemKeepsItsOwnWords(@TempDir Path folder) throws Exception {
        write(folder, "en", "\"gui.input.cancelled\" = \"nothing was written\"");

        Languages languages = Languages.load(folder, Locale.ENGLISH);

        assertThat(languages.catalog().template(CANCELLED, Locale.ENGLISH)).isEqualTo("nothing was written");
    }

    @Test
    void theLibraryLinesAreStyledWithTheRestBecauseTheyAreInTheEntries(@TempDir Path folder) throws Exception {
        write(folder, "en", "crate { opened = \"Opened\" }");

        Languages languages = Languages.load(folder, Locale.ENGLISH);

        assertThat(languages.entries().get(Locale.ENGLISH))
                .describedAs("a line the style pass never sees keeps its role names and reaches a player raw")
                .containsKey("gui.input.cancelled")
                .containsKey("crate.opened");
    }

    @Test
    void theLibraryDoesNotGiveThePluginALanguageItHasNoFileFor(@TempDir Path folder) throws Exception {
        write(folder, "en", "crate { opened = \"Opened\" }");

        Languages languages = Languages.load(folder, Locale.ENGLISH);

        assertThat(languages.locales())
                .describedAs("the language chooser offers what the plugin translated, not what the library ships")
                .containsExactly(Locale.ENGLISH);
    }

    @Test
    void aHalfWrittenTranslationIsStillCountedAgainstThePluginsOwnFile(@TempDir Path folder) throws Exception {
        write(folder, "en", "crate { opened = \"Opened\" }\ncrate { closed = \"Closed\" }");
        write(folder, "tr", "crate { opened = \"Acildi\" }");

        Languages languages = Languages.load(folder, Locale.ENGLISH);

        assertThat(languages.problems())
                .describedAs("the report counts the plugin's lines, so the library's do not inflate it")
                .hasSize(1);
        assertThat(languages.problems().getFirst()).contains("1 of 2");
    }

    @Test
    void theShippedWordsAreThereAtAll() {
        assertThat(LibraryWords.shipped())
                .describedAs("an empty table would make every test above pass for the wrong reason")
                .isNotEmpty()
                .containsKey(Locale.ENGLISH)
                .containsKey(TR);
        assertThat(LibraryWords.shipped().get(Locale.ENGLISH)).containsKey("gui.input.cancelled");
    }

    private static void write(Path folder, String tag, String body) throws Exception {
        Files.writeString(folder.resolve("messages_" + tag + ".conf"), body);
    }
}
