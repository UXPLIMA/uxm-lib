package com.uxplima.uxmlib.text.language;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every key the library asks its host for has a line in every language the library ships.
 *
 * <p>A key with no words behind it is not a compile error and not a test failure anywhere else: it is a
 * player reading {@code gui.input.cancelled} off their screen. The keys are named in two modules and the
 * words live in one folder, so nothing but this holds the two together.
 *
 * <p>The keys are read out of the source rather than listed here, because a list is the thing that is
 * forgotten when a key is added. Adding a window to the engine and no line to these files fails here, in
 * words, naming the key and the file.
 */
class EveryLibraryKeyHasWordsTest {

    /** Where the engine's own keys are written, as this module sees its siblings. */
    private static final List<Path> SOURCES = List.of(
            Path.of("..", "uxmlib-gui", "src", "main", "java"), Path.of("..", "uxmlib-menu", "src", "main", "java"));

    /** Any {@code "gui.something"} literal, which is the shape every one of these keys is written in. */
    private static final Pattern KEY = Pattern.compile("\"(gui\\.[a-z0-9.-]+)\"");

    /**
     * The two names the dialog buttons carried before they were made generic. They are asked for only when the
     * current name answers nothing, so shipping words for them here would mean the fallback could never fire
     * and a catalogue still written against the old pair would silently lose its wording.
     */
    private static final Set<String> NOT_OURS_TO_ANSWER = Set.of("gui.input.dialog-submit", "gui.input.dialog-cancel");

    @Test
    void everyKeyTheEngineAsksForHasALineInEveryShippedLanguage() throws IOException {
        Set<String> asked = askedKeys();

        assertThat(asked)
                .describedAs("no key was found, so this test would pass with the words deleted")
                .hasSizeGreaterThan(20);

        Map<Locale, Map<String, String>> shipped = LibraryWords.shipped();
        assertThat(shipped)
                .describedAs("the library ships English and Turkish at the floor")
                .hasSizeGreaterThan(1);

        Set<String> missing = new TreeSet<>();
        for (var language : shipped.entrySet()) {
            for (String key : asked) {
                if (!language.getValue().containsKey(key)) {
                    missing.add(LanguageFiles.nameOf(language.getKey()) + " has no line for " + key);
                }
            }
        }
        assertThat(missing).isEmpty();
    }

    @Test
    void theShippedLanguagesAllHoldTheSameKeys() {
        Map<Locale, Map<String, String>> shipped = LibraryWords.shipped();
        Set<String> english =
                new TreeSet<>(shipped.getOrDefault(Locale.ENGLISH, Map.of()).keySet());
        assertThat(english)
                .describedAs("English is the language every other one is written against")
                .isNotEmpty();

        for (var language : shipped.entrySet()) {
            assertThat(new TreeSet<>(language.getValue().keySet()))
                    .describedAs("%s", LanguageFiles.nameOf(language.getKey()))
                    .isEqualTo(english);
        }
    }

    private static Set<String> askedKeys() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        for (Path source : SOURCES) {
            assertThat(source)
                    .describedAs("the modules moved, so this guard reads nothing")
                    .isDirectory();
            try (Stream<Path> files = Files.walk(source)) {
                for (Path file :
                        files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    Matcher matcher = KEY.matcher(Files.readString(file));
                    while (matcher.find()) {
                        keys.add(matcher.group(1));
                    }
                }
            }
        }
        keys.removeAll(NOT_OURS_TO_ANSWER);
        return keys;
    }
}
