package com.uxplima.uxmlib.gui.style;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.uxmlib.config.HoconConfig;

/**
 * The sounds a menu plays, read from a host's own configuration file and read again when the host reloads.
 *
 * <p>A menu engine given no sounds plays none, so a host that wants its menus to sound hands the engine one of these.
 * The file names each sound under {@code base}, by convention {@code menu.sounds}, the way {@link MenuSounds#from}
 * reads it, and an operator who edits it and reloads hears the change on the next click.
 */
public final class MenuSoundsFile implements Supplier<MenuSounds> {

    private final Path file;
    private final String base;
    private volatile MenuSounds current;

    private MenuSoundsFile(Path file, String base, MenuSounds current) {
        this.file = file;
        this.base = base;
        this.current = current;
    }

    /** The sounds {@code file} names under {@code base}, read now. */
    public static MenuSoundsFile read(Path file, String base) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(base, "base");
        return new MenuSoundsFile(file, base, MenuSounds.from(HoconConfig.load(file), base));
    }

    /** Read the file again. Call it from the host's own reload. */
    public void reload() {
        current = MenuSounds.from(HoconConfig.load(file), base);
    }

    @Override
    public MenuSounds get() {
        return current;
    }
}
