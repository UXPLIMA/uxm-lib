package com.uxplima.uxmlib.gui.style;

import java.util.Objects;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import com.uxplima.uxmlib.config.HoconConfig;

/**
 * The three sounds a menu plays: opening, a click that acts, and a click that is refused.
 *
 * <p>A refusal answers with a low note rather than with silence, because a button that does nothing and says
 * nothing reads as a broken menu. The shipped volumes sit between 0.5 and 0.7 — loud enough to feel, quiet
 * enough to live with.
 *
 * <p>A sound is named by its vanilla key ({@code item.book.page_turn}) rather than by a Bukkit constant,
 * because the key is what the client plays and it does not move with the server software. A name the client
 * does not know plays nothing, which is why the shipped values are the tested ones. An empty name in the file
 * is how an operator turns one sound off.
 */
public record MenuSounds(Sound open, Sound click, Sound denied) {

    private static final String OPEN_KEY = "item.book.page_turn";
    private static final String CLICK_KEY = "block.note_block.pling";
    private static final String DENIED_KEY = "block.note_block.bass";

    public MenuSounds {
        Objects.requireNonNull(open, "open");
        Objects.requireNonNull(click, "click");
        Objects.requireNonNull(denied, "denied");
    }

    /** The shipped set, used when the file says nothing. */
    public static MenuSounds defaults() {
        return new MenuSounds(
                sound(OPEN_KEY, 0.7f, 1.2f), sound(CLICK_KEY, 0.6f, 1.5f), sound(DENIED_KEY, 0.6f, 0.9f));
    }

    /**
     * The set in {@code config} under {@code base} — {@code "menu.sounds"} by convention. Each sound keeps
     * its shipped value until the file names another one.
     */
    public static MenuSounds from(HoconConfig config, String base) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(base, "base");
        return new MenuSounds(
                read(config, base + ".open", OPEN_KEY, 0.7f, 1.2f),
                read(config, base + ".click", CLICK_KEY, 0.6f, 1.5f),
                read(config, base + ".denied", DENIED_KEY, 0.6f, 0.9f));
    }

    private static Sound read(HoconConfig config, String path, String key, float volume, float pitch) {
        String name = config.getString(path + ".name", key);
        if (name.isBlank()) {
            // An empty name is how an operator turns one sound off. A volume of zero plays nothing.
            return sound(key, 0f, pitch);
        }
        float configured = (float) config.getDouble(path + ".volume", volume);
        return sound(name, configured, (float) config.getDouble(path + ".pitch", pitch));
    }

    private static Sound sound(String key, float volume, float pitch) {
        return Sound.sound(Key.key(key), Sound.Source.MASTER, volume, pitch);
    }
}
