package com.uxplima.uxmlib.gui.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.Gui;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.item.GuiAction;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.gui.item.RenderContext;
import com.uxplima.uxmlib.gui.style.MenuTitles;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.text.Text;
import org.jspecify.annotations.Nullable;

/**
 * Draws what {@link MenuSpec} read, for one viewer.
 *
 * <p>{@code MenuSpec} turns a file into values and proves the layout can be drawn. This class is the other
 * half: it builds the window, writes the tiles, wires the four sides of a click, and fills a computed list
 * from the source the plugin registered. Sixteen plugins share one shape of menu file, so they share this
 * drawing too.
 *
 * <p>The library decides no look. Every word comes from the file, and a word the file writes as
 * {@code @some.key} is handed to the {@link Words} the plugin gave, which is the only thing that knows the
 * catalogue. A menu with a computed list is drawn as a paged window over the slots the list names.
 *
 * <p>Nothing here reaches for a scheduler. Drawing runs on the thread that asked for it, so the caller keeps
 * the menu safe on Folia, and a list source answers from what the plugin already holds.
 */
public final class MenuDraw {

    /**
     * Turns what a file writes into what a player reads.
     *
     * <p>This is the whole of the look. A key such as {@code @shop.title} is looked up here, the values of
     * a row are written in here, and the colours of the server are painted here. The library does none of
     * the three, so sixteen plugins keep one drawing and each keeps its own catalogue and its own theme.
     */
    @FunctionalInterface
    public interface Words {

        /**
         * The finished line a player reads.
         *
         * @param values what a row of a computed list offers, each key spelled as the file spells it
         */
        Component text(Player viewer, String written, Map<String, String> values);

        /**
         * A line the menu acts on rather than shows: a material, an action, the name of another menu. The
         * values of the row are written in, and nothing is painted.
         */
        default String line(Player viewer, String written, Map<String, String> values) {
            return fill(written, values);
        }
    }

    /** Words that write the values in and read the rest as MiniMessage, with no catalogue and no theme. */
    public static Words asWritten() {
        return (viewer, written, values) -> Text.mini(fill(written, values));
    }

    private final MenuActions actions;
    private final MenuConditions conditions;
    private final MenuLists lists;
    private final Words words;
    private final MenuActionRunner.Opener opener;

    public MenuDraw(
            MenuActions actions,
            MenuConditions conditions,
            MenuLists lists,
            Words words,
            MenuActionRunner.Opener opener) {
        this.actions = Objects.requireNonNull(actions, "actions");
        this.conditions = Objects.requireNonNull(conditions, "conditions");
        this.lists = Objects.requireNonNull(lists, "lists");
        this.words = Objects.requireNonNull(words, "words");
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    /**
     * Build the window this viewer sees.
     *
     * @throws IllegalArgumentException when the file names a material no server has
     * @throws IllegalStateException when the file names a list nobody registered
     */
    public Gui draw(MenuSpec spec, Player viewer) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(viewer, "viewer");

        MenuSpec.Item listed = listed(spec);
        // The client draws a chest title from a fixed origin and offers no alignment, so a title sits in
        // the middle only when leading spaces put it there. That is arithmetic the client forces, not a
        // look, so every menu of every plugin gets it here rather than sixteen times by hand.
        Component title = MenuTitles.centre(words.text(viewer, spec.title(), Map.of()));
        PaginatedGui pages = listed == null
                ? null
                : Guis.paginated()
                        .title(title)
                        .rows(spec.rows())
                        .contentSlots(listed.slots())
                        .build();
        Gui gui = pages == null ? Guis.gui().title(title).rows(spec.rows()).build() : pages;

        // The items arrive sorted by priority, so the tile with the highest number is written last and wins
        // the slot it shares.
        for (MenuSpec.Item item : spec.items()) {
            if (!isShown(item, viewer, gui)) {
                continue;
            }
            if (item.list() != null) {
                fill(pages, item, viewer);
                continue;
            }
            GuiItem tile = tile(item, viewer, pages);
            for (int slot : item.slots()) {
                gui.set(slot, tile);
            }
        }
        if (pages != null) {
            pages.render();
        }
        return gui;
    }

    /** Draw the window, open it, and run what the file does on opening. */
    public Gui open(MenuSpec spec, Player viewer) {
        Gui gui = draw(spec, viewer);
        gui.open(viewer);
        new MenuActionRunner(actions, opener, line -> words.line(viewer, line, Map.of()))
                .run(viewer, MenuAction.readAll(spec.openActions(), actions::knows));
        return gui;
    }

    /** The one item that carries a computed list, or null when the menu has none. */
    private static MenuSpec.@Nullable Item listed(MenuSpec spec) {
        for (MenuSpec.Item item : spec.items()) {
            if (item.list() != null) {
                return item;
            }
        }
        return null;
    }

    /**
     * Whether every condition of the item holds. An item that fails one leaves its slots as they are, so a
     * backdrop below it stays visible and no greyed out tile is drawn.
     */
    private boolean isShown(MenuSpec.Item item, Player viewer, Gui gui) {
        if (item.view().isEmpty()) {
            return true;
        }
        RenderContext context = new RenderContext(viewer, gui, item.slots().get(0));
        for (String written : item.view()) {
            Predicate<RenderContext> condition = conditions.parse(written);
            if (!condition.test(context)) {
                return false;
            }
        }
        return true;
    }

    private void fill(@Nullable PaginatedGui pages, MenuSpec.Item item, Player viewer) {
        if (pages == null) {
            return;
        }
        MenuSpec.Listing listing = Objects.requireNonNull(item.list());
        MenuLists.Source source = lists.source(listing.source());
        if (source == null) {
            throw new IllegalStateException("no list is registered under '" + listing.source() + "'.");
        }
        // A tile the file wrote under the list, such as a backdrop over the whole window, would pin every
        // slot and leave no room for a row. The list clears its own slots, so only a tile with a higher
        // priority keeps one and the rows flow around it.
        for (int slot : item.slots()) {
            pages.remove(slot);
        }
        pages.clearPageItems();
        for (MenuLists.Row row : source.rows(viewer)) {
            pages.addPageItem(row(listing.template(), row, viewer));
        }
    }

    private GuiItem row(MenuSpec.Template template, MenuLists.Row row, Player viewer) {
        Map<String, String> values = row.tokens();
        UnaryOperator<String> lines = written -> words.line(viewer, written, values);
        ItemStack icon = row.icon() == null
                ? ItemBuilder.of(material(lines.apply(template.material()), "a list row"))
                        .build()
                : row.icon().clone();
        return new GuiItem.Static(
                written(ItemBuilder.from(icon), template.name(), template.lore(), viewer, values),
                click(template.click(), lines));
    }

    private GuiItem tile(MenuSpec.Item item, Player viewer, @Nullable PaginatedGui pages) {
        UnaryOperator<String> lines = written -> words.line(viewer, written, Map.of());
        ItemStack icon = written(
                ItemBuilder.of(material(lines.apply(item.material()), item.id())),
                item.name(),
                item.lore(),
                viewer,
                Map.of());
        return new GuiItem.Static(icon, action(item, pages, lines));
    }

    private GuiAction action(MenuSpec.Item item, @Nullable PaginatedGui pages, UnaryOperator<String> text) {
        return switch (item.kind()) {
            case PLAIN -> click(item.click(), text);
            case PREVIOUS -> turn(pages, false);
            case NEXT -> turn(pages, true);
        };
    }

    /** A paging button in a menu that has no list turns nothing, and says nothing. */
    private static GuiAction turn(@Nullable PaginatedGui pages, boolean forward) {
        if (pages == null) {
            return GuiAction.None.INSTANCE;
        }
        return new GuiAction.Run(event -> {
            if (forward) {
                pages.nextPage();
            } else {
                pages.previousPage();
            }
        });
    }

    /**
     * Wire the four sides of a click. Every line is read once, here, so a verb nobody registered is refused
     * as the menu is drawn and not under a player's cursor.
     */
    private GuiAction click(MenuSpec.Clicks clicks, UnaryOperator<String> text) {
        if (clicks.isEmpty()) {
            return GuiAction.None.INSTANCE;
        }
        List<MenuAction> left = MenuAction.readAll(clicks.left(), actions::knows);
        List<MenuAction> right = MenuAction.readAll(clicks.right(), actions::knows);
        List<MenuAction> shiftLeft = MenuAction.readAll(clicks.shiftLeft(), actions::knows);
        List<MenuAction> shiftRight = MenuAction.readAll(clicks.shiftRight(), actions::knows);
        MenuActionRunner runner = new MenuActionRunner(actions, opener, text);
        return new GuiAction.Run(event -> {
            if (!(event.getWhoClicked() instanceof Player clicker)) {
                return;
            }
            runner.run(clicker, side(event, left, right, shiftLeft, shiftRight));
        });
    }

    private static List<MenuAction> side(
            InventoryClickEvent event,
            List<MenuAction> left,
            List<MenuAction> right,
            List<MenuAction> shiftLeft,
            List<MenuAction> shiftRight) {
        ClickType type = event.getClick();
        if (type.isShiftClick()) {
            return type.isRightClick() ? shiftRight : shiftLeft;
        }
        if (type.isRightClick()) {
            return right;
        }
        return type.isLeftClick() ? left : List.of();
    }

    private ItemStack written(
            ItemBuilder builder, @Nullable String name, List<String> lore, Player viewer, Map<String, String> values) {
        if (name != null) {
            builder.name(words.text(viewer, name, values));
        }
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(words.text(viewer, line, values));
            }
            builder.lore(lines);
        }
        return builder.build();
    }

    private static Material material(String written, String where) {
        Material found = Material.matchMaterial(written);
        if (found == null) {
            throw new IllegalArgumentException(where + " names the material '" + written + "'. No server has it.");
        }
        return found;
    }

    /** Write the values of the row into the line, each token where the file spelled it. */
    static String fill(String line, Map<String, String> tokens) {
        String written = line;
        for (Map.Entry<String, String> token : tokens.entrySet()) {
            written = written.replace(token.getKey(), token.getValue());
        }
        return written;
    }
}
