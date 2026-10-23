package com.uxplima.uxmlib.storage.testing;

import java.nio.file.Path;
import java.util.Objects;

import com.uxplima.uxmlib.storage.sql.Backend;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.StorageSettings;

/**
 * An empty database on one backend, described the way an operator's configuration describes one.
 *
 * @param settings where it is, with the table prefix the test asked for
 * @param dataFolder the folder a SQLite file is resolved against
 */
public record RealDatabase(StorageSettings settings, Path dataFolder) {

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
}
