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

    @Test
    void writesEveryShippedFileOnceAndNeverOverAnEditedOne(@TempDir Path root) throws Exception {
        Path jar = root.resolve("jar/messages");
        Files.createDirectories(jar);
        Files.writeString(jar.resolve("messages_en.conf"), "shipped english");
        Files.writeString(jar.resolve("messages_tr.conf"), "shipped turkish");
        Files.writeString(jar.resolve("notes.txt"), "not a language");
        Path folder = root.resolve("plugin/messages");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("messages_tr.conf"), "the operator wrote this");

        java.util.List<Path> written =
                LanguageFiles.extractShipped(loaderOver(root.resolve("jar")), "messages", folder);

        assertThat(written).containsExactly(folder.resolve("messages_en.conf"));
        assertThat(Files.readString(folder.resolve("messages_en.conf"))).isEqualTo("shipped english");
        assertThat(Files.readString(folder.resolve("messages_tr.conf"))).isEqualTo("the operator wrote this");
        assertThat(folder.resolve("notes.txt")).doesNotExist();
    }

    private static ClassLoader loaderOver(Path root) throws Exception {
        return new java.net.URLClassLoader(new java.net.URL[] {root.toUri().toURL()}, null);
    }

    private static void write(Path folder, String name) throws Exception {
        Files.writeString(folder.resolve(name), "join { welcome = \"hi\" }\n");
    }
}
