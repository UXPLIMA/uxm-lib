package com.uxplima.uxmlib.gui.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * One menu as a file describes it, before anything is drawn.
 *
 * <p>The shape is the one a whole suite of plugins already ships, so an operator who has laid out one menu
 * has laid out all of them:
 *
 * <pre>{@code
 * title = "@warp.editor.title"
 * rows = 3
 * open-actions = ["sound:ITEM_BOOK_PAGE_TURN 0.7 1.2"]
 *
 * items {
 *   filler   { slots = ["0-26"], material = GRAY_STAINED_GLASS_PANE, name = "", priority = 0 }
 *   teleport {
 *     slot = 0, material = ENDER_PEARL, name = "@warp.editor.teleport.name", priority = 10
 *     view = ["is-server-warp"]
 *     click { left = ["warps:teleport", "close"] }
 *   }
 *   list {
 *     slots = ["0-44"], priority = 5
 *     list { source = "warps:all", template { material = "%warp_icon%", name = "@warp.name" } }
 *   }
 * }
 * }</pre>
 *
 * <p>Reading is separate from drawing. This class turns the file into values and refuses a file that
 * cannot be drawn, so a layout is proved correct with no server running: a slot outside the window, a
 * range the wrong way round, an item that names no slot at all. What an action or a list source means is
 * decided by the plugin that registers it, and never here.
 *
 * <p>A file in this shape is read as it stands. It never goes through {@link MenuConfigMigration}, which
 * belongs to the older mask shape: {@code type} names a paging button here and the migration reads it as
 * the old spelling of {@code material}.
 *
 * <p>A name that starts with {@code @} is a key in a message catalogue, and one that does not is written
 * where it stands. Both are kept as they are read: which of the two it is belongs to the drawing.
 */
public record MenuSpec(String title, int rows, List<String> openActions, List<Item> items) {

    /** The largest chest a server can show. */
    public static final int MAX_ROWS = 6;

    public MenuSpec {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(openActions, "openActions");
        Objects.requireNonNull(items, "items");
        if (rows < 1 || rows > MAX_ROWS) {
            throw new IllegalArgumentException("a menu holds 1 to " + MAX_ROWS + " rows, not " + rows);
        }
        openActions = List.copyOf(openActions);
        items = List.copyOf(items);
    }

    /** How many slots the window holds. */
    public int capacity() {
        return rows * MenuSlots.PER_ROW;
    }

    /** What an item does. A paging button is drawn by the menu itself and carries no click of its own. */
    public enum Kind {
        PLAIN,
        PREVIOUS,
        NEXT
    }

    /** The four sides of a click, each one a list of actions in the order they run. */
    public record Clicks(List<String> left, List<String> right, List<String> shiftLeft, List<String> shiftRight) {

        public Clicks {
            left = List.copyOf(Objects.requireNonNull(left, "left"));
            right = List.copyOf(Objects.requireNonNull(right, "right"));
            shiftLeft = List.copyOf(Objects.requireNonNull(shiftLeft, "shiftLeft"));
            shiftRight = List.copyOf(Objects.requireNonNull(shiftRight, "shiftRight"));
        }

        /** A tile nobody can click. */
        public static Clicks none() {
            return new Clicks(List.of(), List.of(), List.of(), List.of());
        }

        /** Whether every side is empty. */
        public boolean isEmpty() {
            return left.isEmpty() && right.isEmpty() && shiftLeft.isEmpty() && shiftRight.isEmpty();
        }

        static Clicks read(ConfigurationNode node) {
            if (node.virtual()) {
                return none();
            }
            return new Clicks(
                    MenuSpec.strings(node.node("left")),
                    MenuSpec.strings(node.node("right")),
                    MenuSpec.strings(node.node("shift_left")),
                    MenuSpec.strings(node.node("shift_right")));
        }
    }

    /** How one row of a computed list is drawn. The plugin fills the tokens; the file lays them out. */
    public record Template(String material, @Nullable String name, List<String> lore, Clicks click) {

        public Template {
            Objects.requireNonNull(material, "material");
            lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
            Objects.requireNonNull(click, "click");
        }
    }

    /**
     * A list the plugin computes, drawn into the slots of the item that carries it.
     *
     * @param source the name the plugin registered it under, written {@code <plugin>:<name>}
     */
    public record Listing(String source, Template template) {

        public Listing {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(template, "template");
            if (source.isBlank()) {
                throw new IllegalArgumentException("a list names no source");
            }
        }
    }

    /**
     * One item of the menu.
     *
     * @param priority which item wins a slot that two of them ask for. The highest is drawn last, so a
     *     button on a backdrop is written by giving the backdrop the lower number.
     * @param view the conditions under which the item is drawn at all. An item whose conditions do not
     *     hold leaves its slots empty rather than drawing a greyed out tile.
     */
    public record Item(
            String id,
            List<Integer> slots,
            int priority,
            Kind kind,
            String material,
            @Nullable String name,
            List<String> lore,
            List<String> view,
            Clicks click,
            @Nullable Listing list) {

        public Item {
            Objects.requireNonNull(id, "id");
            slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(material, "material");
            lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
            view = List.copyOf(Objects.requireNonNull(view, "view"));
            Objects.requireNonNull(click, "click");
        }
    }

    /**
     * Read a whole menu.
     *
     * @throws IllegalArgumentException when the file describes a menu that cannot be drawn
     */
    public static MenuSpec read(ConfigurationNode node) {
        Objects.requireNonNull(node, "node");

        String title = node.node("title").getString("");
        if (title.isEmpty()) {
            throw new IllegalArgumentException("a menu names no title");
        }
        int rows = node.node("rows").getInt(MAX_ROWS);
        int capacity = rows * MenuSlots.PER_ROW;

        List<Item> items = new ArrayList<>();
        for (var entry : node.node("items").childrenMap().entrySet()) {
            items.add(item(String.valueOf(entry.getKey()), entry.getValue(), capacity));
        }
        items.sort(Comparator.comparingInt(Item::priority));
        return new MenuSpec(title, rows, strings(node.node("open-actions")), items);
    }

    private static Item item(String id, ConfigurationNode node, int capacity) {
        ConfigurationNode list = node.node("list");
        return new Item(
                id,
                MenuSlots.read(node, id, capacity),
                node.node("priority").getInt(0),
                kind(node.node("type").getString("plain"), id),
                node.node("material").getString("STONE"),
                node.node("name").virtual() ? null : node.node("name").getString(""),
                strings(node.node("lore")),
                strings(node.node("view")),
                Clicks.read(node.node("click")),
                list.virtual() ? null : listing(list));
    }

    private static Listing listing(ConfigurationNode node) {
        ConfigurationNode template = node.node("template");
        return new Listing(
                node.node("source").getString(""),
                new Template(
                        template.node("material").getString("STONE"),
                        template.node("name").virtual()
                                ? null
                                : template.node("name").getString(""),
                        strings(template.node("lore")),
                        Clicks.read(template.node("click"))));
    }

    private static Kind kind(String written, String id) {
        return switch (written.trim().toLowerCase(Locale.ROOT)) {
            case "plain" -> Kind.PLAIN;
            case "previous" -> Kind.PREVIOUS;
            case "next" -> Kind.NEXT;
            default -> throw new IllegalArgumentException(
                    id + " has the type '" + written + "'. It has to be plain, previous or next.");
        };
    }

    /** The strings of a list node, and an empty list when the file names none. */
    static List<String> strings(ConfigurationNode node) {
        if (node.virtual()) {
            return List.of();
        }
        List<String> read = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String value = child.getString();
            if (value != null) {
                read.add(value);
            }
        }
        return List.copyOf(read);
    }
}
