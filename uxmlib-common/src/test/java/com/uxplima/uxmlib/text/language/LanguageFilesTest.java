package com.uxplima.uxmlib.text.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The language files a plugin has are the files in its folder, not a list in its code. */
class LanguageFilesTest {

    @Test
    void findsEveryFileThatFollowsTheNamingConvention(@TempDir Path folder) throws Exception {
        write(folder, "messages_en.conf");
        write(folder, "messages_tr.conf");
        write(folder, "messages_de.conf");

        Map<Locale, Path> found = LanguageFiles.in(folder);

        assertThat(found).containsOnlyKeys(Locale.ENGLISH, Locale.forLanguageTag("tr"), Locale.GERMAN);
    }

    @Test
    void readsACountryTagWrittenWithEitherSeparator(@TempDir Path folder) throws Exception {
        write(folder, "messages_pt-BR.conf");
        write(folder, "messages_zh_CN.conf");

        Map<Locale, Path> found = LanguageFiles.in(folder);

        assertThat(found).containsOnlyKeys(Locale.forLanguageTag("pt-BR"), Locale.forLanguageTag("zh-CN"));
    }

    @Test
    void ignoresAFileThatIsNotALanguageFile(@TempDir Path folder) throws Exception {
        write(folder, "messages_en.conf");
        write(folder, "config.conf");
        write(folder, "messages.conf");
        write(folder, "messages_en.conf.bak");

        assertThat(LanguageFiles.in(folder)).containsOnlyKeys(Locale.ENGLISH);
    }

    @Test
    void aFolderThatDoesNotExistHoldsNoLanguage(@TempDir Path folder) throws Exception {
        assertThat(LanguageFiles.in(folder.resolve("absent"))).isEmpty();
    }

    @Test
    void namesTheFileOfALocale() {
        assertThat(LanguageFiles.nameOf(Locale.ENGLISH)).isEqualTo("messages_en.conf");
        assertThat(LanguageFiles.nameOf(Locale.forLanguageTag("pt-BR"))).isEqualTo("messages_pt-BR.conf");
    }

    @Test
    void refusesALocaleWithNoLanguage() {
        assertThatThrownBy(() -> LanguageFiles.nameOf(Locale.ROOT)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void write(Path folder, String name) throws Exception {
        Files.writeString(folder.resolve(name), "join { welcome = \"hi\" }\n");
    }
}
