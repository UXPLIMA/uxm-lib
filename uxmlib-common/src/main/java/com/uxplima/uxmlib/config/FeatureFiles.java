package com.uxplima.uxmlib.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The large features that keep their settings in a file of their own beside config.conf, so config.conf holds what
 * the whole plugin shares. A feature file holds its block under the same name, so every key keeps the path it had
 * when it lived in config.conf.
 */
public final class FeatureFiles {

    private FeatureFiles() {}

    /** The file a feature block lives in. */
    public static String file(String block) {
        return block + ".conf";
    }

    /**
     * Move each block an earlier release kept in config.conf into its own file, once.
     *
     * <p>A block is moved as text, with the comment above it, so it keeps the words and values the operator wrote.
     * A block whose file is there already stays where it is.
     *
     * @return the files that were written
     */
    public static List<String> moveOut(Path configFile, List<String> blocks) {
        Objects.requireNonNull(configFile, "configFile");
        Objects.requireNonNull(blocks, "blocks");
        if (!Files.isRegularFile(configFile)) {
            return List.of();
        }
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(configFile, StandardCharsets.UTF_8));
            List<String> written = new ArrayList<>();
            for (String block : blocks) {
                Path target = configFile.resolveSibling(file(block));
                if (Files.exists(target)) {
                    continue;
                }
                List<String> moved = cut(lines, block);
                if (!moved.isEmpty()) {
                    Files.writeString(target, String.join("\n", moved) + "\n", StandardCharsets.UTF_8);
                    written.add(file(block));
                }
            }
            if (!written.isEmpty()) {
                Files.writeString(configFile, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
            }
            return List.copyOf(written);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("cannot move the feature blocks out of " + configFile, unreadable);
        }
    }

    /** Take the top-level block named {@code block}, with the comment right above it, out of {@code lines}. */
    private static List<String> cut(List<String> lines, String block) {
        Pattern opens = Pattern.compile(Pattern.quote(block) + "\\s*=?\\s*\\{\\s*");
        int start = -1;
        for (int index = 0; index < lines.size(); index++) {
            if (opens.matcher(lines.get(index)).matches()) {
                start = index;
                break;
            }
        }
        if (start < 0) {
            return List.of();
        }
        int end = start + 1;
        while (end < lines.size() && !lines.get(end).equals("}")) {
            end++;
        }
        if (end == lines.size()) {
            return List.of();
        }
        int from = start;
        while (from > 0 && lines.get(from - 1).startsWith("#")) {
            from--;
        }
        List<String> moved = new ArrayList<>(lines.subList(from, end + 1));
        lines.subList(from, end + 1).clear();
        // The blank line that kept the block apart from the one above goes with it.
        if (from > 0
                && from <= lines.size()
                && lines.get(from - 1).isEmpty()
                && (from == lines.size() || lines.get(from).isEmpty())) {
            lines.remove(from - 1);
        }
        return moved;
    }

    /** Lay each feature file under {@code config}, so a reader finds every key at the path it always had. */
    public static void readInto(HoconConfig config, Path folder, List<String> blocks) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(folder, "folder");
        for (String block : blocks) {
            config.include(folder.resolve(file(block)));
        }
    }

    /**
     * Add the keys this release ships to each feature file an operator already has. Nothing they wrote changes,
     * and a file that gained keys is kept as it was beside it. A catalogue, a map whose entries are the operator's
     * whole, is never written back into a file that has one; a catalogue another file holds is not looked for here.
     *
     * @return the files that gained keys
     */
    public static List<String> bringUpToDate(
            Path folder, List<String> blocks, ClassLoader resources, String... catalogues) {
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(resources, "resources");
        List<String> added = new ArrayList<>();
        for (String block : blocks) {
            Path file = folder.resolve(file(block));
            if (Files.isRegularFile(file) && HoconConfig.load(file).mergeDefaults(file(block), resources, catalogues)) {
                added.add(file(block));
            }
        }
        return List.copyOf(added);
    }
}
