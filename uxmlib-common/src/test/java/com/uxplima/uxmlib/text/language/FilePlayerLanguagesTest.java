package com.uxplima.uxmlib.text.language;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A plugin that stands alone still remembers what a player chose. */
class FilePlayerLanguagesTest {

    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final Locale EN = Locale.ENGLISH;
    private static final UUID WHO = UUID.randomUUID();

    @Test
    void aChoiceSurvivesARestart(@TempDir Path dir) {
        Path file = dir.resolve("languages.conf");
        FilePlayerLanguages first = FilePlayerLanguages.loadedFrom(file);
        first.choose(WHO, TR);
        first.rememberClient(WHO, EN);
        first.save();

        FilePlayerLanguages second = FilePlayerLanguages.loadedFrom(file);

        assertThat(second.chosen(WHO)).contains(TR);
        assertThat(second.lastClient(WHO)).contains(EN);
    }

    @Test
    void forgettingAChoiceKeepsWhatTheClientReported(@TempDir Path dir) {
        Path file = dir.resolve("languages.conf");
        FilePlayerLanguages store = FilePlayerLanguages.loadedFrom(file);
        store.choose(WHO, TR);
        store.rememberClient(WHO, EN);

        store.forget(WHO);
        store.save();

        FilePlayerLanguages reloaded = FilePlayerLanguages.loadedFrom(file);
        assertThat(reloaded.chosen(WHO)).isEmpty();
        assertThat(reloaded.lastClient(WHO)).contains(EN);
    }

    @Test
    void aFileThatIsNotThereIsAnEmptyStore(@TempDir Path dir) {
        FilePlayerLanguages store = FilePlayerLanguages.loadedFrom(dir.resolve("absent.conf"));

        assertThat(store.chosen(WHO)).isEmpty();
        assertThat(store.lastClient(WHO)).isEmpty();
    }

    @Test
    void anEntryThatIsNotAUuidIsSkippedRatherThanFailingTheLoad(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("languages.conf");
        Files.writeString(
                file, "players {\n  \"not-a-uuid\" { chosen = \"tr\" }\n  \"" + WHO + "\" { chosen = \"tr\" }\n}\n");

        FilePlayerLanguages store = FilePlayerLanguages.loadedFrom(file);

        assertThat(store.chosen(WHO)).contains(TR);
    }

    @Test
    void savingWritesNothingUntilSomethingChanges(@TempDir Path dir) {
        Path file = dir.resolve("languages.conf");
        FilePlayerLanguages store = FilePlayerLanguages.loadedFrom(file);

        store.save();

        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void aStoreThatHasNotChangedSinceItsSaveIsNotDirty(@TempDir Path dir) {
        FilePlayerLanguages store = FilePlayerLanguages.loadedFrom(dir.resolve("languages.conf"));
        assertThat(store.dirty()).isFalse();

        store.choose(WHO, TR);
        assertThat(store.dirty()).isTrue();

        store.save();
        assertThat(store.dirty()).isFalse();
    }
}
