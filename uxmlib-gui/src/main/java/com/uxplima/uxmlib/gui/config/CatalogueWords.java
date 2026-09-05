package com.uxplima.uxmlib.gui.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.MessageKey;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;

/**
 * What a menu file writes, turned into what a player reads.
 *
 * <p>A menu file names no colour and holds no language. A line that starts with {@code @} is a key of the
 * message catalogue, so the words are translated with the rest and painted from {@code theme.conf}. A line
 * that does not is written as it stands, with the roles of the theme applied to it, so an operator who wants
 * one word of their own still gets the colours of the server.
 *
 * <p>A line that starts with {@code tile:} is a whole tile rather than one line, and {@link MenuTiles} draws
 * it in the six blocks the canon fixes. Everything else is one line of words.
 *
 * <p>A value of a row goes in as a placeholder and never as text. A player who named their item
 * {@code <red>} sees those characters on the tile: they do not repaint it.
 */
public final class CatalogueWords implements MenuDraw.Words {

    /**
     * What a window is about, for the viewer who opened it.
     *
     * <p>A tile of a window that was opened on one thing, a listing or an arena, is about that thing, and no
     * row of a list carries it. A plugin that has such a window gives one of these; a plugin whose windows
     * are about nothing but their rows does not.
     */
    @FunctionalInterface
    public interface Window {

        /** The values of the window this player has open, which is empty when they opened a plain one. */
        Map<String, String> valuesOf(UUID viewer);

        /** A window about nothing but its own rows. */
        static Window none() {
            return viewer -> Map.of();
        }
    }

    private final Messages messages;
    private final Styler styler;
    private final Window window;
    private final MenuTiles tiles;

    /** Words for a plugin whose windows are about nothing but their rows. */
    public CatalogueWords(Messages messages, Styler styler) {
        this(messages, styler, Window.none());
    }

    public CatalogueWords(Messages messages, Styler styler, Window window) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.styler = Objects.requireNonNull(styler, "styler");
        this.window = Objects.requireNonNull(window, "window");
        this.tiles = new MenuTiles(messages, styler);
    }

    @Override
    public Component text(Player viewer, String written, Map<String, String> values) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(written, "written");
        TagResolver[] resolvers = resolvers(all(viewer, values));
        if (MenuTiles.marks(written)) {
            return tiles.lore(viewer, written, resolvers);
        }
        if (written.startsWith("@")) {
            String path = written.substring(1);
            // The path is its own last answer: a key an operator invented and never translated shows the key
            // on the tile rather than an empty line, which is the cue that it needs a line.
            return messages.render(viewer, MessageKey.of(path, path), resolvers);
        }
        return Text.mini(styler.apply(written, messages.localeOf(viewer)), resolvers);
    }

    @Override
    public String line(Player viewer, String written, Map<String, String> values) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(written, "written");
        String filled = written;
        for (Map.Entry<String, String> value : all(viewer, values).entrySet()) {
            filled = filled.replace("<" + value.getKey() + ">", value.getValue());
        }
        return filled;
    }

    /**
     * The values of the window and the values of the row together.
     *
     * <p>A row of a computed list wins where the two name the same thing: a tile of a list is about that row.
     */
    private Map<String, String> all(Player viewer, Map<String, String> values) {
        Map<String, String> opened = window.valuesOf(viewer.getUniqueId());
        if (opened.isEmpty()) {
            return values;
        }
        Map<String, String> merged = new LinkedHashMap<>(opened);
        merged.putAll(values);
        return merged;
    }

    private static TagResolver[] resolvers(Map<String, String> values) {
        TagResolver[] resolvers = new TagResolver[values.size()];
        int at = 0;
        for (Map.Entry<String, String> value : values.entrySet()) {
            resolvers[at++] = Text.placeholder(value.getKey(), value.getValue());
        }
        return resolvers;
    }
}
