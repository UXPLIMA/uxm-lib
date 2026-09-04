package com.uxplima.uxmlib.text.language;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.uxplima.uxmlib.config.ClasspathFiles;
import com.uxplima.uxmlib.config.ConfigException;

/**
 * The language files a plugin holds, found by name in a folder.
 *
 * <p>A file is named {@code messages_<tag>.conf}, where the tag is an IETF language tag: {@code en},
 * {@code tr}, {@code pt-BR}. An underscore is read as a hyphen, so {@code messages_zh_CN.conf} and
 * {@code messages_zh-CN.conf} name the same locale and an operator cannot get it wrong.
 *
 * <p>The set of languages is the set of files. A plugin ships some of them on first run, and that is a
 * default, not a limit: an operator who writes one more file has one more language, with no rebuild and no
 * entry in any list.
 */
public final class LanguageFiles {

    private static final String PREFIX = "messages_";

    private static final String SUFFIX = ".conf";

    private LanguageFiles() {}

    /**
     * Every language file in {@code folder}, mapped from the locale its name carries to the file itself.
     *
     * <p>A folder that is not there holds no language and is not an error: a plugin whose files have not been
     * written yet falls back to the default template of every key, which is what the first start does.
     *
     * @throws IOException when the folder exists and cannot be read, which an operator has to see
     */
    public static Map<Locale, Path> in(Path folder) throws IOException {
        Objects.requireNonNull(folder, "folder");
        if (!Files.isDirectory(folder)) {
            return Map.of();
        }
        Map<Locale, Path> found = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.sorted().forEach(file -> localeOf(file).ifPresent(locale -> found.put(locale, file)));
        } catch (UncheckedIOException unreadable) {
            throw unreadable.getCause();
        }
        return Map.copyOf(found);
    }

    /** The locale a file name carries, or empty when the name is not a language file. */
    public static Optional<Locale> localeOf(Path file) {
        Objects.requireNonNull(file, "file");
        String name = file.getFileName().toString();
        if (!name.startsWith(PREFIX) || !name.endsWith(SUFFIX)) {
            return Optional.empty();
        }
        String tag =
                name.substring(PREFIX.length(), name.length() - SUFFIX.length()).replace('_', '-');
        if (tag.isEmpty()) {
            return Optional.empty();
        }
        Locale locale = Locale.forLanguageTag(tag);
        return locale.getLanguage().isEmpty() ? Optional.empty() : Optional.of(locale);
    }

    /**
     * Write every language file the jar carries into {@code folder}, and never over one that is already
     * there.
     *
     * <p>The files are discovered, not listed, so a plugin gains a language by gaining a file. A file an
     * operator edited is theirs: it is left exactly as it is, whichever language it holds.
     *
     * @param loader the plugin's own class loader, which is what carries its resources
     * @param directory where the files sit in the jar, usually {@code messages}
     * @return the files this call wrote, which is empty on every start after the first
     */
    public static List<Path> extractShipped(ClassLoader loader, String directory, Path folder) {
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(folder, "folder");
        List<Path> written = new ArrayList<>();
        for (String name : ClasspathFiles.list(loader, directory)) {
            Path file = folder.resolve(name);
            if (localeOf(file).isEmpty() || Files.isRegularFile(file)) {
                continue;
            }
            copy(loader, directory + "/" + name, file);
            written.add(file);
        }
        return List.copyOf(written);
    }

    private static void copy(ClassLoader loader, String resource, Path file) {
        try (InputStream shipped = loader.getResourceAsStream(resource)) {
            if (shipped == null) {
                return;
            }
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(shipped, file);
        } catch (IOException unwritable) {
            throw new ConfigException("cannot write the language file " + file, unwritable);
        }
    }

    /** The file name a locale is written under, the one {@link #localeOf} reads back. */
    public static String nameOf(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        String tag = locale.toLanguageTag();
        if (locale.getLanguage().isEmpty() || "und".equals(tag)) {
            throw new IllegalArgumentException("A language file needs a locale with a language: " + locale);
        }
        return PREFIX + tag + SUFFIX;
    }
}
