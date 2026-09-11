package com.uxplima.uxmlib.text.language;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.MessageCatalogLoader;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Every language a plugin holds, loaded from its own folder in one step.
 *
 * <p>This is the replacement for the list of language tags that a plugin used to keep in its code. The
 * languages are the files: an operator who writes one more file has one more language, with no rebuild.
 *
 * <p>Underneath every file sits {@link LibraryWords}: the words the engine's own windows ask for, carried in
 * the jar. A plugin's own line always wins over one of those, and a plugin that writes none of them gets words
 * on those windows rather than the key.
 *
 * <p>What is missing is said, once. A file that translates fewer keys than the default language does is
 * reported with its count, and a file that cannot be parsed is reported by name while every other file still
 * loads. A player never sees the consequence, because the catalog falls back through the default locale to
 * the key's own text, so the report exists for the operator and not for the game.
 */
public final class Languages {

    private final MessageCatalog catalog;

    private final Map<Locale, Map<String, String>> entries;

    private final Set<Locale> fileLocales;

    private final List<String> problems;

    private Languages(
            MessageCatalog catalog,
            Map<Locale, Map<String, String>> entries,
            Set<Locale> fileLocales,
            List<String> problems) {
        this.catalog = catalog;
        this.entries = entries;
        this.fileLocales = fileLocales;
        this.problems = problems;
    }

    /**
     * Read every {@code messages_<tag>.conf} in {@code folder}.
     *
     * @param defaultLocale the language every other one falls back to, and the yardstick the report counts
     *     against
     */
    public static Languages load(Path folder, Locale defaultLocale) {
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(defaultLocale, "defaultLocale");
        Map<Locale, Map<String, String>> entries = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        Map<Locale, Path> files;
        try {
            files = LanguageFiles.in(folder);
        } catch (IOException unreadable) {
            // A folder that cannot be listed leaves every key on its own default, which is a finished
            // message. The operator has to see why, and the server has to keep running.
            Map<Locale, Map<String, String>> floor = beneath(Map.of(), defaultLocale);
            return new Languages(
                    new MessageCatalog(floor, defaultLocale),
                    floor,
                    Set.of(),
                    List.of(folder + " cannot be listed, so no language file was read: " + reasonOf(unreadable)));
        }
        for (var file : files.entrySet()) {
            read(file.getKey(), file.getValue(), entries, problems);
        }
        problems.addAll(missingKeyReport(files, entries, defaultLocale));
        Map<Locale, Map<String, String>> merged = beneath(entries, defaultLocale);
        return new Languages(
                new MessageCatalog(merged, defaultLocale), merged, Set.copyOf(entries.keySet()), List.copyOf(problems));
    }

    private static void read(
            Locale locale, Path file, Map<Locale, Map<String, String>> entries, List<String> problems) {
        try {
            entries.put(
                    locale,
                    flatten(HoconConfigurationLoader.builder()
                            .path(file)
                            .build()
                            .load()));
        } catch (ConfigurateException unreadable) {
            problems.add(file.getFileName() + " cannot be read, so its language falls back: " + reasonOf(unreadable));
        }
    }

    private static String reasonOf(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static List<String> missingKeyReport(
            Map<Locale, Path> files, Map<Locale, Map<String, String>> entries, Locale defaultLocale) {
        Map<String, String> yardstick = entries.get(defaultLocale);
        if (yardstick == null || yardstick.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        entries.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(defaultLocale))
                .sorted(Comparator.comparing(entry -> entry.getKey().toLanguageTag()))
                .forEach(entry -> report(files.get(entry.getKey()), entry.getValue(), yardstick, defaultLocale, lines));
        return lines;
    }

    private static void report(
            @Nullable Path file,
            Map<String, String> translated,
            Map<String, String> yardstick,
            Locale defaultLocale,
            List<String> lines) {
        long missing = yardstick.keySet().stream()
                .filter(key -> !translated.containsKey(key))
                .count();
        if (missing == 0 || file == null) {
            return;
        }
        lines.add(file.getFileName() + " translates " + (yardstick.size() - missing) + " of " + yardstick.size()
                + " lines, so " + missing + " of them fall back to " + defaultLocale.toLanguageTag() + ".");
    }

    /**
     * The plugin's own lines, with the library's own words underneath them.
     *
     * <p>{@link LibraryWords} carries the text of the windows the engine draws itself, and this is where it
     * goes under the plugin's files: same language, plugin's line first, so a plugin or an operator who wrote
     * the key keeps their words and one who wrote nothing gets words rather than the key on a screen.
     *
     * <p>Only a language the plugin itself has a file for, plus the default. A library file for a language
     * nobody here translated would hand a player two lines of one screen in two languages, which is worse
     * than one honest fallback: the catalog already answers such a player in the default language.
     */
    private static Map<Locale, Map<String, String>> beneath(
            Map<Locale, Map<String, String>> own, Locale defaultLocale) {
        Map<Locale, Map<String, String>> shipped = LibraryWords.shipped();
        Set<Locale> locales = new LinkedHashSet<>(own.keySet());
        locales.add(defaultLocale);
        Map<Locale, Map<String, String>> merged = new LinkedHashMap<>();
        for (Locale locale : locales) {
            Map<String, String> lines = new LinkedHashMap<>(shipped.getOrDefault(locale, Map.of()));
            lines.putAll(own.getOrDefault(locale, Map.of()));
            merged.put(locale, Map.copyOf(lines));
        }
        return Map.copyOf(merged);
    }

    private static Map<String, String> flatten(ConfigurationNode node) {
        return MessageCatalogLoader.flatten(node);
    }

    /** The catalog every message is resolved through. */
    public MessageCatalog catalog() {
        return catalog;
    }

    /**
     * Every line of every file, flattened to {@code a.b.c -> template}, for a caller that has to walk the
     * text itself: a style pass reaches a path an operator invented, which no key enum knows.
     *
     * <p>The library's own lines are in here too, underneath the plugin's. They have to be: a style pass is
     * given this map and builds the catalog it returns out of it, so a line missing here is a line that
     * reaches a player with its role names unpainted, or does not reach them at all.
     */
    public Map<Locale, Map<String, String>> entries() {
        return entries;
    }

    /**
     * The languages that have a file, which is the set a player may be offered.
     *
     * <p>The plugin's own files and nothing else. {@link LibraryWords} ships more languages than most plugins
     * translate, and a chooser built from those would offer a language in which only the confirm buttons are
     * translated.
     */
    public Set<Locale> locales() {
        return fileLocales;
    }

    /**
     * What an operator has to see, one line each, empty when every file is complete and readable. Log these
     * at startup: nothing else says that a translation is half written.
     */
    public List<String> problems() {
        return problems;
    }
}
