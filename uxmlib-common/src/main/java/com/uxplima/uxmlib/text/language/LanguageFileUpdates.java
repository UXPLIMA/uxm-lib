package com.uxplima.uxmlib.text.language;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.uxmlib.config.ClasspathFiles;
import com.uxplima.uxmlib.config.ConfigException;

/**
 * Bring the language files on the disk up to the ones the jar ships, without taking a word from the operator.
 *
 * <p>A language file is written once, on the first start. A line a later release corrects stayed wrong on every
 * server that had the old one, because the operator's file wins over the jar, although the operator never wrote
 * that line. So every file is written with a copy beside it, under {@code .defaults/}, of the text it was written
 * from. On the next start a line whose words still equal that copy is a line nobody touched, and it takes the
 * words the jar ships now. A line the operator changed differs from the copy and is theirs.
 *
 * <p>Only a line is ever rewritten, in place: the comments, the order, the blank lines and the line endings of the
 * file stay as they are. A value written across several lines is never touched. A key the operator does not have
 * is not written either: {@link Languages#load(Path, java.util.Locale, ClassLoader, String)} already reads the
 * jar's file underneath theirs, so a new line reaches a player without changing the file.
 *
 * <p>A file written before this existed has no copy to compare against, so nothing in it can be told apart from
 * an edit. It changes nothing on that start and keeps a copy from then on.
 */
public final class LanguageFileUpdates {

    /** Where the copies live, beside the files they belong to. */
    public static final String BASELINE_FOLDER = ".defaults";

    private static final String KEY = "(\"(?:[^\"\\\\]|\\\\.)*\"|[A-Za-z0-9_\\-.]+)";
    private static final String TAIL = "\\s*,?\\s*(?:#.*|//.*)?";
    private static final Pattern OPEN = Pattern.compile("^\\s*" + KEY + "\\s*[=:]?\\s*\\{" + TAIL + "$");
    private static final Pattern CLOSE = Pattern.compile("^\\s*\\}" + TAIL + "$");
    private static final Pattern LEAF = Pattern.compile(
            "^(\\s*" + KEY + "\\s*[=:]\\s*)(\"(?!\"\")(?:[^\"\\\\]|\\\\.)*\"|\\[[^\\[\\]]*\\])(" + TAIL + ")$");
    private static final String TRIPLE_QUOTE = "\"\"\"";

    private LanguageFileUpdates() {}

    /**
     * What one start changed.
     *
     * @param written the files the folder did not have, written whole
     * @param updated every corrected line, as {@code <file>: <key path>}
     */
    public record Report(List<Path> written, List<String> updated) {

        public Report {
            written = List.copyOf(written);
            updated = List.copyOf(updated);
        }
    }

    /**
     * Write every language file the jar carries that the folder lacks, correct every line nobody touched, and keep
     * the copy the next release compares against.
     *
     * @param loader the plugin's own class loader, which is what carries its resources
     * @param directory where the files sit in the jar, usually {@code messages}
     * @param folder where the files sit on the disk
     */
    public static Report bringUpToDate(ClassLoader loader, String directory, Path folder) {
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(folder, "folder");
        List<Path> written = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        for (String name : ClasspathFiles.list(loader, directory)) {
            Path file = folder.resolve(name);
            if (LanguageFiles.localeOf(file).isEmpty()) {
                continue;
            }
            Optional<String> shipped = read(loader, directory + "/" + name);
            if (shipped.isEmpty()) {
                continue;
            }
            try {
                Path baseline = folder.resolve(BASELINE_FOLDER).resolve(name);
                if (!Files.isRegularFile(file)) {
                    writeText(file, shipped.get());
                    written.add(file);
                } else if (Files.isRegularFile(baseline)) {
                    for (String key :
                            correct(file, Files.readString(baseline, StandardCharsets.UTF_8), shipped.get())) {
                        updated.add(name + ": " + key);
                    }
                }
                if (!Files.isRegularFile(baseline)
                        || !Files.readString(baseline, StandardCharsets.UTF_8).equals(shipped.get())) {
                    writeText(baseline, shipped.get());
                }
            } catch (IOException unwritable) {
                throw new ConfigException("cannot bring the language file " + file + " up to date", unwritable);
            }
        }
        return new Report(written, updated);
    }

    /** Rewrite each line of {@code file} that still reads as {@code baseline} and reads otherwise in {@code shipped}. */
    private static List<String> correct(Path file, String baseline, String shipped) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        Map<String, Leaf> theirs = leaves(lines);
        Map<String, Leaf> before = leaves(List.of(baseline.split("\n", -1)));
        Map<String, Leaf> now = leaves(List.of(shipped.split("\n", -1)));
        List<String> corrected = new ArrayList<>();
        for (Map.Entry<String, Leaf> line : theirs.entrySet()) {
            Leaf was = before.get(line.getKey());
            Leaf next = now.get(line.getKey());
            Leaf mine = line.getValue();
            if (was == null
                    || next == null
                    || !mine.value().equals(was.value())
                    || next.value().equals(was.value())) {
                continue;
            }
            lines.set(mine.index(), mine.head() + next.value() + mine.tail());
            corrected.add(line.getKey());
        }
        if (!corrected.isEmpty()) {
            writeText(file, String.join("\n", lines));
        }
        return corrected;
    }

    /** Every one-line value of a file, by its full key path, in the order the file holds them. */
    private static Map<String, Leaf> leaves(List<String> lines) {
        Map<String, Leaf> found = new LinkedHashMap<>();
        Deque<List<String>> blocks = new ArrayDeque<>();
        boolean inText = false;
        for (int index = 0; index < lines.size(); index++) {
            String raw = lines.get(index);
            boolean carriageReturn = raw.endsWith("\r");
            String line = carriageReturn ? raw.substring(0, raw.length() - 1) : raw;
            if (count(line, TRIPLE_QUOTE) % 2 == 1) {
                inText = !inText;
                continue;
            }
            if (inText) {
                continue;
            }
            Matcher leaf = LEAF.matcher(line);
            Matcher open = OPEN.matcher(line);
            if (leaf.matches()) {
                String path = path(blocks, segments(leaf.group(2)));
                found.put(
                        path,
                        new Leaf(index, leaf.group(1), leaf.group(3), leaf.group(4) + (carriageReturn ? "\r" : "")));
            } else if (open.matches()) {
                blocks.push(segments(open.group(1)));
            } else if (CLOSE.matcher(line).matches() && !blocks.isEmpty()) {
                blocks.pop();
            }
        }
        return found;
    }

    private static String path(Deque<List<String>> blocks, List<String> key) {
        List<String> all = new ArrayList<>();
        blocks.descendingIterator().forEachRemaining(all::addAll);
        all.addAll(key);
        return String.join(".", all);
    }

    /** A quoted key is one segment, whatever it holds; a bare one splits on its dots. */
    private static List<String> segments(String key) {
        if (key.startsWith("\"")) {
            return List.of(key.substring(1, key.length() - 1));
        }
        return List.of(key.split("\\."));
    }

    private static int count(String line, String needle) {
        int found = 0;
        for (int at = line.indexOf(needle); at >= 0; at = line.indexOf(needle, at + needle.length())) {
            found++;
        }
        return found;
    }

    private static Optional<String> read(ClassLoader loader, String resource) {
        try (InputStream shipped = loader.getResourceAsStream(resource)) {
            return shipped == null
                    ? Optional.empty()
                    : Optional.of(new String(shipped.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            throw new ConfigException("cannot read the shipped language file " + resource, unreadable);
        }
    }

    private static void writeText(Path file, String text) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /** One line holding a one-line value: what stands before the value, the value, and what follows it. */
    private record Leaf(int index, String head, String value, String tail) {}
}
