package com.uxplima.uxmlib.config;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Function;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Crash-safe file writes for {@link HoconConfig}: render to a sibling temp file, then swap it into place
 * with an atomic rename, so a process killed mid-write leaves the previous config intact instead of a
 * truncated one.
 */
final class AtomicFiles {

    private static final System.Logger LOG = System.getLogger(AtomicFiles.class.getName());

    private AtomicFiles() {}

    /** Render {@code node} to a temp file built by {@code loaderFor}, then atomic-rename it onto {@code file}. */
    static void save(Path file, Function<Path, HoconConfigurationLoader> loaderFor, ConfigurationNode node) {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            loaderFor.apply(temp).save(node);
            moveIntoPlace(temp, file);
        } catch (IOException failure) {
            deleteQuietly(temp);
            throw new ConfigException("failed to save config", failure);
        }
    }

    private static void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException noAtomic) {
            // Some filesystems (e.g. certain network mounts) cannot rename atomically; fall back to a plain
            // replace: still far safer than overwriting the live file in place.
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanupFailure) {
            LOG.log(System.Logger.Level.DEBUG, "could not remove temp config file " + path, cleanupFailure);
        }
    }
}
