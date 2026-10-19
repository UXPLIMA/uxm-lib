package com.uxplima.uxmlib.text.language;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.uxplima.uxmlib.text.message.MessageKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A plugin's languages are loaded from its folder, and what is missing from one is said once. */
class LanguagesTest {

    private static final MessageKey WELCOME = MessageKey.of("join.welcome", "<green>fallback");
    private static final MessageKey BYE = MessageKey.of("quit.bye", "<gray>fallback");
    private static final Locale TR = Locale.forLanguageTag("tr");

    /**
     * A plugin update adds lines, and the file an operator already has was written by the version before it. The
     * jar's own files sit underneath, so a line the operator never had reads the shipped words and not its key, while
     * every line the operator did write still wins. Found live: a new menu line showed a Bedrock player
     * "menu.store.chest.gestures.left".
     */
    @Test
    void aLineAnOlderFileLacksReadsTheShippedWords(@TempDir Path folder, @TempDir Path jar) throws Exception {
        write(folder, "en", "join { welcome = \"Hello from the operator\" }");
        Path shipped = jar.resolve("messages");
        Files.createDirectories(shipped);
        Files.writeString(
                shipped.resolve("messages_en.conf"), "join { welcome = \"Welcome\" }\nquit { bye = \"Shipped bye\" }");

        try (java.net.URLClassLoader loader =
                new java.net.URLClassLoader(new java.net.URL[] {jar.toUri().toURL()}, null)) {
            Languages languages = Languages.load(folder, Locale.ENGLISH, loader, "messages");

            assertThat(languages.catalog().template(WELCOME, Locale.ENGLISH)).isEqualTo("Hello from the operator");
            assertThat(languages.catalog().template(BYE, Locale.ENGLISH)).isEqualTo("Shipped bye");
            assertThat(languages.entries().get(Locale.ENGLISH)).containsEntry("quit.bye", "Shipped bye");
        }
    }

    @Test
    void everyFileInTheFolderBecomesALanguage(@TempDir Path folder) throws Exception {
        write(folder, "en", "join { welcome = \"Welcome\" }\nquit { bye = \"Bye\" }");
        write(folder, "tr", "join { welcome = \"Hos geldin\" }\nquit { bye = \"Gule gule\" }");
        write(folder, "de", "join { welcome = \"Willkommen\" }\nquit { bye = \"Tschuess\" }");

        Languages languages = Languages.load(folder, Locale.ENGLISH);

        assertThat(languages.locales()).containsExactlyInAnyOrder(Locale.ENGLISH, TR, Locale.GERMAN);
        assertThat(languages.catalog().template(WELCOME, Locale.GERMAN)).isEqualTo("Willkommen");
        assertThat(languages.catalog().template(WELCOME, TR)).isEqualTo("Hos geldin");
    }

    @Test
    void aLanguageNobodyWroteAFileForFallsBackToTheDefault(@TempDir Path folder) throws Exception {
        write(folder, "en", "join { welcome = \"Welcome\" }");

        Languages languages = Languages.load(folder, Locale.ENGLISH);

        assertThat(languages.catalog().template(WELCOME, Locale.FRENCH)).isEqualTo("Welcome");
    }

    @Test
    void ahalfWrittenTranslationIsReportedOnceWithItsCount(@TempDir Path folder) throws Exception {
        write(folder, "en", "join { welcome = \"Welcome\" }\nquit { bye = \"Bye\" }");
        write(folder, "de", "join { welcome = \"Willkommen\" }");

        Languages languages = Languages.load(folder, Locale.ENGLISH);

        assertThat(languages.problems()).hasSize(1);
        assertThat(languages.problems().getFirst())
                .contains("messages_de.conf")
                .contains("1")
                .contains("2");
    }

    @Test
    void aCompleteSetOfFilesHasNothingToReport(@TempDir Path folder) throws Exception {
        write(folder, "en", "join { welcome = \"Welcome\" }");
        write(folder, "tr", "join { welcome = \"Hos geldin\" }");

        assertThat(Languages.load(folder, Locale.ENGLISH).problems()).isEmpty();
    }

    @Test
    void aFileThatCannotBeParsedIsReportedAndTheRestStillLoads(@TempDir Path folder) throws Exception {
        write(folder, "en", "join { welcome = \"Welcome\" }");
        write(folder, "de", "join { welcome = \"Willkommen\" ");

        Languages languages = Languages.load(folder, Locale.ENGLISH);

        assertThat(languages.catalog().template(WELCOME, Locale.ENGLISH)).isEqualTo("Welcome");
        assertThat(languages.problems()).hasSize(1);
        assertThat(languages.problems().getFirst()).contains("messages_de.conf");
    }

    @Test
    void anEmptyFolderLeavesEveryKeyOnItsOwnDefault(@TempDir Path folder) throws Exception {
        Languages languages = Languages.load(folder, Locale.ENGLISH);

        assertThat(languages.locales()).isEmpty();
        assertThat(languages.catalog().template(BYE, Locale.ENGLISH)).isEqualTo("<gray>fallback");
    }

    @Test
    void theFlattenedEntriesAreKeptForTheCallerThatStylesThem(@TempDir Path folder) throws Exception {
        write(folder, "en", "join { welcome = \"Welcome\" }");

        Languages languages = Languages.load(folder, Locale.ENGLISH);

        assertThat(languages.entries()).containsKey(Locale.ENGLISH);
        assertThat(languages.entries().get(Locale.ENGLISH)).containsEntry("join.welcome", "Welcome");
    }

    private static void write(Path folder, String tag, String body) throws Exception {
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("messages_" + tag + ".conf"), body + "\n");
    }
}
