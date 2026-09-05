package com.uxplima.uxmlib.gui.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Every menu of a plugin, read from the files of the operator.
 *
 * <p>A menu is named by its file: {@code menus/house.conf} is {@code house}, and {@code menus/admin/log.conf}
 * is {@code admin/log}. That is what an {@code open:} line in another menu writes, so an operator who adds a
 * file has added a menu and needs nothing from the plugin.
 *
 * <p>A file that cannot be read does not stop the plugin and does not close the menu. The shipped file of the
 * same name is read instead and the reason is reported, because a plugin that refuses to open a window over a
 * missing comma is worse than one that opens the window it was shipped with. A menu with no shipped file and
 * no readable file is simply absent, and the command that opens it says so.
 */
public final class MenuFiles {

    private static final String SUFFIX = ".conf";

    /** Where the shipped copy of a menu comes from, which is the jar of the plugin. */
    @FunctionalInterface
    public interface Shipped {

        /** The shipped file under this name, or null when the plugin ships none. */
        @Nullable InputStream open(String resource);
    }

    /** What is told about a file that could not be read. */
    @FunctionalInterface
    public interface Complaint {
        void about(String menu, Exception why);
    }

    private final Map<String, MenuSpec> menus;

    private MenuFiles(Map<String, MenuSpec> menus) {
        this.menus = Map.copyOf(menus);
    }

    /** No menu at all, which is what a server that deleted the folder has. */
    public static MenuFiles none() {
        return new MenuFiles(Map.of());
    }

    /**
     * Read every {@code .conf} under {@code folder}, and the folders inside it.
     *
     * @param shipped where a file that cannot be read falls back to
     * @param complaint what is told about a file that fell back
     */
    public static MenuFiles load(Path folder, Shipped shipped, Complaint complaint) {
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(shipped, "shipped");
        Objects.requireNonNull(complaint, "complaint");

        Map<String, MenuSpec> read = new LinkedHashMap<>();
        if (!Files.isDirectory(folder)) {
            return new MenuFiles(read);
        }
        try (Stream<Path> files = Files.walk(folder)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(SUFFIX))
                    .sorted()
                    .forEach(file -> read.put(nameOf(folder, file), null));
        } catch (IOException unreadableFolder) {
            complaint.about(folder.toString(), unreadableFolder);
            return new MenuFiles(Map.of());
        }
        Map<String, MenuSpec> menus = new LinkedHashMap<>();
        for (String name : read.keySet()) {
            MenuSpec spec = one(folder.resolve(name + SUFFIX), name, shipped, complaint);
            if (spec != null) {
                menus.put(name, spec);
            }
        }
        return new MenuFiles(menus);
    }

    /** The menu under this name, or nothing when no file of the name could be read. */
    public Optional<MenuSpec> byName(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(menus.get(name));
    }

    /** How many menus were read. */
    public int size() {
        return menus.size();
    }

    private static @Nullable MenuSpec one(Path file, String name, Shipped shipped, Complaint complaint) {
        try {
            return MenuSpec.read(
                    HoconConfigurationLoader.builder().path(file).build().load());
        } catch (ConfigurateException | IllegalArgumentException unreadable) {
            complaint.about(name, unreadable);
            return fallback(name, shipped, complaint);
        }
    }

    private static @Nullable MenuSpec fallback(String name, Shipped shipped, Complaint complaint) {
        try (InputStream stream = shipped.open("menus/" + name + SUFFIX)) {
            if (stream == null) {
                return null;
            }
            Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
            return MenuSpec.read(HoconConfigurationLoader.builder()
                    .source(() -> new BufferedReader(reader))
                    .build()
                    .load());
        } catch (IOException | IllegalArgumentException unreadableShipped) {
            complaint.about(name, unreadableShipped);
            return null;
        }
    }

    /** The name of a file under the folder, with the separator of the file system written as a slash. */
    private static String nameOf(Path folder, Path file) {
        String relative = folder.relativize(file).toString().replace('\\', '/');
        return relative.substring(0, relative.length() - SUFFIX.length());
    }
}
