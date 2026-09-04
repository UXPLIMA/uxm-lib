package com.uxplima.uxmlib.text.language;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * <p>What is missing is said, once. A file that translates fewer keys than the default language does is
 * reported with its count, and a file that cannot be parsed is reported by name while every other file still
 * loads. A player never sees the consequence, because the catalog falls back through the default locale to
 * the key's own text, so the report exists for the operator and not for the game.
 */
public final class Languages {

    private final MessageCatalog catalog;

    private final Map<Locale, Map<String, String>> entries;

    private final List<String> problems;

    private Languages(MessageCatalog catalog, Map<Locale, Map<String, String>> entries, List<String> problems) {
        this.catalog = catalog;
        this.entries = entries;
        this.problems = problems;
    }

    /**
     * Read every {@code messages_<tag>.conf} in {@code folder}.
     *
     * @param defaultLocale the language every other one falls back to, and the yardstick the report counts
     *     against
     * @throws IOException when the folder exists and cannot be listed
     */
    public static Languages load(Path folder, Locale defaultLocale) throws IOException {
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(defaultLocale, "defaultLocale");
        Map<Locale, Path> files = LanguageFiles.in(folder);
        Map<Locale, Map<String, String>> entries = new LinkedHashMap<>();
        Map<Locale, ConfigurationNode> trees = new HashMap<>();
        List<String> problems = new ArrayList<>();
        for (var file : files.entrySet()) {
            read(file.getKey(), file.getValue(), trees, entries, problems);
        }
        problems.addAll(missingKeyReport(files, entries, defaultLocale));
        return new Languages(
                MessageCatalogLoader.fromNodes(trees, defaultLocale), Map.copyOf(entries), List.copyOf(problems));
    }

    private static void read(
            Locale locale,
            Path file,
            Map<Locale, ConfigurationNode> trees,
            Map<Locale, Map<String, String>> entries,
            List<String> problems) {
        try {
            ConfigurationNode tree =
                    HoconConfigurationLoader.builder().path(file).build().load();
            trees.put(locale, tree);
            entries.put(locale, flatten(tree));
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

    private static Map<String, String> flatten(ConfigurationNode node) {
        Map<String, String> flat = new LinkedHashMap<>();
        collect(node, "", flat);
        return Map.copyOf(flat);
    }

    private static void collect(ConfigurationNode node, String path, Map<String, String> into) {
        if (node.isMap()) {
            for (var child : node.childrenMap().entrySet()) {
                String childPath = path.isEmpty() ? String.valueOf(child.getKey()) : path + "." + child.getKey();
                collect(child.getValue(), childPath, into);
            }
            return;
        }
        String template = node.getString();
        if (template != null && !path.isEmpty()) {
            into.put(path, template);
        }
    }

    /** The catalog every message is resolved through. */
    public MessageCatalog catalog() {
        return catalog;
    }

    /**
     * Every line of every file, flattened to {@code a.b.c -> template}, for a caller that has to walk the
     * text itself: a style pass reaches a path an operator invented, which no key enum knows.
     */
    public Map<Locale, Map<String, String>> entries() {
        return entries;
    }

    /** The languages that have a file. */
    public Set<Locale> locales() {
        return entries.keySet();
    }

    /**
     * What an operator has to see, one line each, empty when every file is complete and readable. Log these
     * at startup: nothing else says that a translation is half written.
     */
    public List<String> problems() {
        return problems;
    }
}
