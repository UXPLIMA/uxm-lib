package com.uxplima.uxmlib.storage.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

import com.uxplima.uxmlib.storage.sql.Backend;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.StorageSettings;

/**
 * An empty database on one backend, described the way an operator's configuration describes one.
 *
 * @param settings where it is, with the table prefix the test asked for
 * @param dataFolder the folder a SQLite file is resolved against
 */
public record RealDatabase(StorageSettings settings, Path dataFolder) implements AutoCloseable {

    public RealDatabase {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(dataFolder, "dataFolder");
    }

    /** The backend this database runs on. */
    public Backend backend() {
        return settings.backend();
    }

    /** The same database under another table prefix. */
    public RealDatabase withTablePrefix(String tablePrefix) {
        return new RealDatabase(settings.withTablePrefix(tablePrefix), dataFolder);
    }

    /** Open a pool on it, as a plugin would. The caller closes it. */
    public Database open() {
        return settings.open(dataFolder, "real-" + settings.backend().id());
    }

    @Override
    public String toString() {
        return settings.backend().id();
    }

    /**
     * Delete the folder it was handed, with whatever file it holds. A test run closes the database it handed
     * an {@link EveryBackend} test once the test is over; a test that asked {@link RealDatabases#fresh} itself
     * closes it.
     */
    @Override
    public void close() {
        if (!Files.exists(dataFolder)) {
            return;
        }
        try (Stream<Path> inside = Files.walk(dataFolder)) {
            for (Path path : inside.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("could not delete " + dataFolder, failure);
        }
    }
}
