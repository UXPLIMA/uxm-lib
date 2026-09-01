package com.uxplima.uxmlib.text.style;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Where a theme is read from: one file for the server, and a plugin's own file on top of it.
 *
 * <p>A server runs a suite of our plugins and expects one look. Sixteen copies of the same file is not that:
 * it is sixteen chances to drift and sixteen files to edit to change one colour. So the theme lives once, at
 * {@link #shared(Path)}, and every plugin reads it.
 *
 * <p>A plugin's own file stays supported and wins key by key, because a server that wants one plugin to read
 * differently should not have to give up the shared file to get it.
 */
public final class ThemeFiles {

    /** The folder of the shared file, beside the plugins that read it. */
    private static final String FOLDER = "uxmTheme";

    private static final String FILE = "theme.conf";

    private ThemeFiles() {}

    /** The shared file, worked out from the data folder of any plugin that reads it. */
    public static Path shared(Path dataFolder) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Path plugins = dataFolder.toAbsolutePath().getParent();
        Path root = plugins != null ? plugins : dataFolder;
        return root.resolve(FOLDER).resolve(FILE);
    }

    /**
     * The theme in {@code shared}, with {@code own} applied on top of it.
     *
     * <p>A file that is not there is not an error: a server with neither file gets the shipped look, and a
     * server with only the shared file gets it everywhere.
     *
     * @throws ConfigurateException when a file exists and cannot be read, which an operator has to see
     * @throws IllegalArgumentException when a file holds something that is not a colour
     */
    public static Theme load(Path shared, Path own) throws ConfigurateException {
        Objects.requireNonNull(shared, "shared");
        Objects.requireNonNull(own, "own");
        ConfigurationNode merged = read(own);
        merged.mergeFrom(read(shared));
        return merged.empty() ? Theme.defaults() : Theme.from(merged);
    }

    private static ConfigurationNode read(Path file) throws ConfigurateException {
        if (!Files.isRegularFile(file)) {
            return CommentedConfigurationNode.root();
        }
        return HoconConfigurationLoader.builder().path(file).build().load();
    }
}
