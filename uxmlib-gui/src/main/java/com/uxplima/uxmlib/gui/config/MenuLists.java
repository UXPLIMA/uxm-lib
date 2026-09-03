package com.uxplima.uxmlib.gui.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * The lists a plugin computes, under the names its menu files write.
 *
 * <p>A menu file lays out a list and never fills it:
 *
 * <pre>{@code
 * rows { slots = ["0-44"], list { source = "auction:open", template { material = STONE, name = "@tile.name" } } }
 * }</pre>
 *
 * <p>The plugin registers the source under the same name. What the rows are, and in which order, is the
 * plugin's business: a page of database rows, the warps a player may use, the items a shop sells. The file
 * decides where they sit and how they read.
 *
 * <p>A row carries the values, never the look. It gives a token for each thing the template can write, and
 * an icon when the row is an item that already exists, such as the item a listing sells. The template still
 * writes the name and the lore over that icon, so the operator keeps the words.
 */
public final class MenuLists {

    /**
     * One line of a computed list.
     *
     * @param icon the item to draw, or null to draw the material the template names
     * @param tokens what the template may write, each key spelled as the file spells it, such as
     *     {@code %price%}
     */
    public record Row(@Nullable ItemStack icon, Map<String, String> tokens) {

        public Row {
            tokens = Map.copyOf(Objects.requireNonNull(tokens, "tokens"));
        }

        /** A row the template draws from its own material. */
        public static Row of(Map<String, String> tokens) {
            return new Row(null, tokens);
        }

        /** A row that draws an item the plugin already holds. */
        public static Row of(ItemStack icon, Map<String, String> tokens) {
            return new Row(Objects.requireNonNull(icon, "icon"), tokens);
        }
    }

    /** Where the rows of one list come from. */
    @FunctionalInterface
    public interface Source {

        /**
         * The rows to draw for this viewer, in the order they are drawn.
         *
         * <p>This runs on the thread that draws the menu, which is the thread of the click or of the
         * command. It must not wait on a database. A plugin that reads a table holds the answer it read
         * before it opened the menu.
         */
        List<Row> rows(Player viewer);
    }

    private final Map<String, Source> sources = new LinkedHashMap<>();

    /** Register a source under the name the menu files write, {@code <plugin>:<name>}. */
    public MenuLists register(String name, Source source) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(source, "source");
        sources.put(name, source);
        return this;
    }

    /** Whether a source is registered under this name. */
    public boolean knows(String name) {
        return sources.containsKey(name);
    }

    /** The source under this name, or null when nobody registered one. */
    public @Nullable Source source(String name) {
        return sources.get(name);
    }
}
