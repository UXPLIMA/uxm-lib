package com.uxplima.uxmlib.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Where a plugin keeps what it writes for itself, a database file or its logs: a {@code data} folder in the
 * plugin's folder, apart from the files an operator edits.
 *
 * <p>A file an earlier release wrote straight into the plugin's folder moves into {@code data} the first time it is
 * asked for, with the journal files SQLite keeps beside a database, so a server that updates keeps its rows.
 */
public final class DataFiles {

    /** The folder, inside the plugin's folder. */
    public static final String FOLDER = "data";

    /** The files SQLite keeps beside a database while it is open or after a crash. */
    private static final List<String> COMPANIONS = List.of("-wal", "-shm", "-journal");

    private DataFiles() {}

    /**
     * The path of {@code name} under the data folder, after moving an old copy of it from {@code pluginFolder}.
     * An absolute name, or one already under the data folder, is used as it is.
     */
    public static Path resolve(Path pluginFolder, String name) {
        Objects.requireNonNull(pluginFolder, "pluginFolder");
        Objects.requireNonNull(name, "name");
        Path asked = Path.of(name);
        if (asked.isAbsolute()
                || (asked.getNameCount() > 1 && asked.getName(0).toString().equals(FOLDER))) {
            return pluginFolder.resolve(asked);
        }
        Path now = pluginFolder.resolve(FOLDER).resolve(asked);
        Path old = pluginFolder.resolve(asked);
        try {
            if (Files.exists(old) && !Files.exists(now)) {
                Files.createDirectories(Objects.requireNonNull(now.getParent(), "parent"));
                Files.move(old, now);
                for (String companion : COMPANIONS) {
                    Path beside = old.resolveSibling(old.getFileName() + companion);
                    if (Files.exists(beside)) {
                        Files.move(beside, now.resolveSibling(now.getFileName() + companion));
                    }
                }
            }
            Files.createDirectories(Objects.requireNonNull(now.getParent(), "parent"));
        } catch (IOException unmovable) {
            throw new ConfigException("cannot move " + old + " into " + FOLDER, unmovable);
        }
        return now;
    }
}
