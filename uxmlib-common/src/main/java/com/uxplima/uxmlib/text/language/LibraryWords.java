package com.uxplima.uxmlib.text.language;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmlib.config.ClasspathFiles;
import com.uxplima.uxmlib.text.message.MessageCatalogLoader;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The words the library's own windows ask for, carried in the jar.
 *
 * <p>The menu engine draws a few screens nobody wrote a file for: the confirm prompt, the page arrows a long
 * list grows, the colour picker, and the acknowledgement a cancelled prompt sends. Each names a key and asks
 * the host's catalogue for it. That is still how it works, and the host still wins; what changes here is what
 * happens when the host has no line. It used to be the key itself, printed at a player: uxmEssentials was the
 * one plugin of the suite that had written these keys, and nothing anywhere said that the other twenty seven
 * owed them. The owner found it by cancelling a prompt in the crate editor and reading
 * {@code gui.input.cancelled} in chat.
 *
 * <p>So the library answers for its own namespace. These files are the floor under the plugin's own language
 * files, never over them: {@link Languages} reads a plugin's {@code messages_<tag>.conf} on top, key by key,
 * so an operator who wants other words writes the key in their own file and a plugin that already ships the
 * line keeps it.
 *
 * <p>Only the {@code gui.*} namespace is here, and only the keys the library itself asks for. Nothing about
 * any game a plugin plays belongs in this folder.
 *
 * <p>The languages are the files, as everywhere else: a language is gained by adding one to
 * {@code uxmlib/messages} and nothing counts them.
 */
public final class LibraryWords {

    /** Where the files sit in the jar. Not a data folder: an operator never sees these, they override them. */
    public static final String DIRECTORY = "uxmlib/messages";

    private static final Map<Locale, Map<String, String>> SHIPPED = read(LibraryWords.class.getClassLoader());

    private LibraryWords() {}

    /**
     * Every language the library ships, flattened to {@code a.b.c -> template}.
     *
     * <p>Read once, when this class is first touched, because a jar does not change while a server runs.
     */
    public static Map<Locale, Map<String, String>> shipped() {
        return SHIPPED;
    }

    /**
     * The same, off any class loader, which is what a test and a shaded jar each need.
     *
     * <p>A file that cannot be parsed is left out rather than thrown over: our own file being broken must not
     * be what stops a server, and the plugin's own line, or the key's built-in default, is still behind it.
     * The build has {@code EveryLibraryKeyHasWordsTest} for the case this hides.
     */
    static Map<Locale, Map<String, String>> read(ClassLoader loader) {
        Objects.requireNonNull(loader, "loader");
        Map<Locale, Map<String, String>> words = new LinkedHashMap<>();
        for (String name : names(loader)) {
            LanguageFiles.localeOf(Path.of(name))
                    .ifPresent(locale ->
                            entries(loader, DIRECTORY + "/" + name).ifPresent(entries -> words.put(locale, entries)));
        }
        return Map.copyOf(words);
    }

    /**
     * The file names in the folder, empty when the class loader cannot list it. A loader that answers a
     * protocol nothing here reads is a server we still have to start: the words fall back one tier and the
     * plugin keeps running, where a throw out of a static initialiser would take the whole plugin down.
     */
    private static List<String> names(ClassLoader loader) {
        try {
            return ClasspathFiles.list(loader, DIRECTORY);
        } catch (RuntimeException unlistable) {
            return List.of();
        }
    }

    private static Optional<Map<String, String>> entries(ClassLoader loader, String resource) {
        try {
            return Optional.of(MessageCatalogLoader.flatten(HoconConfigurationLoader.builder()
                    .source(() -> open(loader, resource))
                    .build()
                    .load()));
        } catch (IOException | IllegalStateException unreadable) {
            return Optional.empty();
        }
    }

    private static BufferedReader open(ClassLoader loader, String resource) throws IOException {
        InputStream stream = loader.getResourceAsStream(resource);
        if (stream == null) {
            throw new IOException("the shipped language file " + resource + " is not in the jar");
        }
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
