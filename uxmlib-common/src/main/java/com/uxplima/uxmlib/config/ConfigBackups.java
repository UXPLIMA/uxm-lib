package com.uxplima.uxmlib.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Copies of a configuration file taken before it is written again, kept in a {@code backups} folder beside it.
 *
 * <p>Each copy is named for the file and the moment it was taken, {@code backups/config-<yyyy-MM-dd-HHmmss>.conf},
 * so the folder sorts oldest first, and the last {@link #KEPT} copies of each file are kept. An older
 * {@code <name>.bak} beside the file, which earlier releases wrote, moves into the folder the first time.
 */
public final class ConfigBackups {

    /** The folder the copies go in, beside the file. */
    public static final String FOLDER = "backups";

    /** How many copies of one file are kept. */
    public static final int KEPT = 5;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    private ConfigBackups() {}

    /**
     * Copy {@code file} into the backups folder beside it and drop the oldest copies past {@link #KEPT}. A file
     * that is not there has nothing to keep.
     *
     * @return the copy, or the file itself when there was nothing to copy
     */
    public static Path keep(Path file) {
        return keep(file, Clock.system(ZoneId.systemDefault()));
    }

    static Path keep(Path file, Clock clock) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(clock, "clock");
        if (!Files.isRegularFile(file)) {
            return file;
        }
        Path folder = folder(file);
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        try {
            Files.createDirectories(folder);
            moveOldCopy(file, folder, stem, extension, clock);
            Path copy = folder.resolve(stem + "-" + LocalDateTime.now(clock).format(STAMP) + extension);
            Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
            prune(folder, stem, extension);
            return copy;
        } catch (IOException unwritable) {
            throw new ConfigException("cannot keep a copy of " + file, unwritable);
        }
    }

    /** The copies of {@code file} there are now, oldest first. */
    public static List<Path> of(Path file) {
        Objects.requireNonNull(file, "file");
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return copies(folder(file), dot > 0 ? name.substring(0, dot) : name, dot > 0 ? name.substring(dot) : "");
    }

    private static Path folder(Path file) {
        Path parent = file.toAbsolutePath().getParent();
        return parent == null ? Path.of(FOLDER) : parent.resolve(FOLDER);
    }

    /** The {@code <name>.bak} an earlier release kept beside the file joins the others, stamped with its own time. */
    private static void moveOldCopy(Path file, Path folder, String stem, String extension, Clock clock)
            throws IOException {
        Path old = file.resolveSibling(file.getFileName() + ".bak");
        if (!Files.isRegularFile(old)) {
            return;
        }
        LocalDateTime written =
                LocalDateTime.ofInstant(Files.getLastModifiedTime(old).toInstant(), clock.getZone());
        Files.move(
                old,
                folder.resolve(stem + "-" + written.format(STAMP) + extension),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static void prune(Path folder, String stem, String extension) throws IOException {
        List<Path> copies = copies(folder, stem, extension);
        for (int index = 0; index < copies.size() - KEPT; index++) {
            Files.deleteIfExists(copies.get(index));
        }
    }

    private static List<Path> copies(Path folder, String stem, String extension) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        String pattern = java.util.regex.Pattern.quote(stem) + "-\\d{4}-\\d{2}-\\d{2}-\\d{6}"
                + java.util.regex.Pattern.quote(extension);
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(each -> each.getFileName().toString().matches(pattern))
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            throw new ConfigException("cannot list " + folder, unreadable);
        }
    }
}
