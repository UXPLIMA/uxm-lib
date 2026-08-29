package com.uxplima.uxmlib.gui.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.InteractionModifier;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiAction;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Builds a {@link SimpleGui} from a HOCON/Configurate node, so a server owner can lay out and re-skin a
 * menu in a config file while code keeps the behaviour. The node carries a {@code title}, a {@code rows}
 * count, an optional {@code locks} list (interaction modifiers the menu lets through), a {@code mask} (a
 * list of nine-character rows), and an {@code items} table whose keys are the mask characters; each item
 * gives a {@code material}, optional MiniMessage {@code name} and {@code lore}, and an optional
 * {@code action} resolved from a {@link MenuActions} registry.
 *
 * <pre>{@code
 * title = "<gold>Shop"
 * rows = 3
 * mask = [ "XXXXXXXXX", "X   C   X", "XXXXXXXXX" ]
 * items {
 *   X { material = "GRAY_STAINED_GLASS_PANE", name = " " }
 *   C { material = "BARRIER", name = "<red>Close", action = "close" }
 * }
 * }</pre>
 *
 * <p>An item may instead declare an ordered {@code states} map keyed by a state name; each state carries an
 * icon spec and a named {@code condition} resolved from a {@link MenuConditions} registry. The first state
 * whose condition passes for the viewer renders (this maps onto a {@link GuiItem.Stateful} item), so the
 * same slot can look and behave differently per player: pass the conditions registry to
 * {@link #load(ConfigurationNode, MenuActions, MenuConditions)}.
 *
 * <pre>{@code
 * items {
 *   S {
 *     states {
 *       online  { condition = "is-online",  material = "LIME_DYE", name = "<green>Online" }
 *       offline { condition = "always",     material = "GRAY_DYE", name = "<gray>Offline" }
 *     }
 *   }
 * }
 * }</pre>
 */
public final class MenuConfig {

    private MenuConfig() {}

    /** Build a menu from {@code node}, wiring item actions through {@code actions}; no multi-state items. */
    public static SimpleGui load(ConfigurationNode node, MenuActions actions) {
        return load(node, actions, new MenuConditions());
    }

    /**
     * Build a menu from {@code node}, wiring click behaviour through {@code actions} and state conditions
     * through {@code conditions}. An item that declares a {@code states} map renders the first state whose
     * named condition passes for the viewer; a {@code locks} list lets the named interactions through.
     */
    public static SimpleGui load(ConfigurationNode node, MenuActions actions, MenuConditions conditions) {
        return load(node, actions, conditions, null);
    }

    /**
     * As {@link #load(ConfigurationNode, MenuActions, MenuConditions)}, but with an i18n {@link MessageCatalog}
     * so an icon may draw its name/lore from a lang file by key (a {@code name-key}/{@code lore-key} field)
     * rather than inline its display text: translation then lives in the lang file, the layout in the menu
     * config. Keyed text resolves against the catalog's default locale at build time.
     *
     * <p>Before reading, the node is run through {@link #migrate} so a config written with older key names is
     * upgraded in place to the current ones, so an upgrade needs no hand-editing.
     */
    public static SimpleGui load(
            ConfigurationNode node, MenuActions actions, MenuConditions conditions, @Nullable MessageCatalog catalog) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(conditions, "conditions");
        migrate(node);
        Component title = Text.mini(node.node("title").getString(""));
        int rows = node.node("rows").getInt(determineRows(node));
        SimpleGui gui = Guis.gui().title(title).rows(rows).build();
        applyLocks(gui, node.node("locks"));
        Locale locale = catalog == null ? Locale.getDefault() : catalog.defaultLocale();
        Map<Character, GuiItem> legend = readLegend(node.node("items"), actions, conditions, catalog, locale);
        gui.filler().pattern(readMask(node.node("mask")), legend);
        return gui;
    }

    /**
     * Rewrite known legacy key names in {@code node} to their current names, in place, so a config saved
     * against an older release loads without hand-editing. Idempotent: a node already on the current names
     * is unchanged. Exposed so a caller can migrate a tree once and persist it before reading.
     */
    public static void migrate(ConfigurationNode node) {
        Objects.requireNonNull(node, "node");
        MenuConfigMigration.apply(node);
    }

    private static void applyLocks(SimpleGui gui, ConfigurationNode locksNode) {
        for (String raw : readStringList(locksNode)) {
            gui.allow(parseModifier(raw));
        }
    }

    private static InteractionModifier parseModifier(String raw) {
        try {
            return InteractionModifier.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("unknown interaction lock in menu config: " + raw, unknown);
        }
    }

    private static int determineRows(ConfigurationNode node) {
        int maskRows = node.node("mask").childrenList().size();
        return Math.min(6, Math.max(1, maskRows));
    }

    private static List<String> readMask(ConfigurationNode maskNode) {
        List<String> mask = new ArrayList<>();
        for (ConfigurationNode row : maskNode.childrenList()) {
            mask.add(row.getString(""));
        }
        return mask;
    }

    private static Map<Character, GuiItem> readLegend(
            ConfigurationNode itemsNode,
            MenuActions actions,
            MenuConditions conditions,
            @Nullable MessageCatalog catalog,
            Locale locale) {
        Map<Character, GuiItem> legend = new HashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                itemsNode.childrenMap().entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (key.isEmpty()) {
                continue;
            }
            legend.put(key.charAt(0), readItem(entry.getValue(), actions, conditions, catalog, locale));
        }
        return legend;
    }

    private static GuiItem readItem(
            ConfigurationNode itemNode,
            MenuActions actions,
            MenuConditions conditions,
            @Nullable MessageCatalog catalog,
            Locale locale) {
        ConfigurationNode statesNode = itemNode.node("states");
        if (!statesNode.empty()) {
            return readStateful(itemNode, statesNode, actions, conditions, catalog, locale);
        }
        ItemStack icon = IconOptions.read(itemNode).toItem(catalog, locale);
        GuiAction action = resolveAction(itemNode, actions);
        return action == GuiAction.None.INSTANCE ? GuiItem.display(icon) : new GuiItem.Static(icon, action);
    }

    /**
     * A stateful item's states each overlay their differences on a base icon spec declared alongside them, so
     * a variant repeats only the fields it changes (see {@link IconOptions#combine}). The base is the item
     * node's own icon fields (everything except the {@code states} subtree).
     */
    private static GuiItem readStateful(
            ConfigurationNode itemNode,
            ConfigurationNode statesNode,
            MenuActions actions,
            MenuConditions conditions,
            @Nullable MessageCatalog catalog,
            Locale locale) {
        IconOptions base = IconOptions.read(itemNode);
        List<MenuConditions.NamedState> states = new ArrayList<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                statesNode.childrenMap().entrySet()) {
            String name = String.valueOf(entry.getKey());
            ConfigurationNode stateNode = entry.getValue();
            String conditionName = stateNode.node("condition").getString("always");
            ItemStack icon =
                    IconOptions.combine(base, IconOptions.read(stateNode)).toItem(catalog, locale);
            states.add(new MenuConditions.NamedState(
                    name, conditions.require(conditionName), icon, resolveAction(stateNode, actions)));
        }
        return MenuConditions.statefulOf(states);
    }

    private static GuiAction resolveAction(ConfigurationNode node, MenuActions actions) {
        String actionName = node.node("action").getString();
        if (actionName == null) {
            return GuiAction.None.INSTANCE;
        }
        java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> action = actions.get(actionName);
        if (action == null) {
            throw new IllegalArgumentException("unknown menu action: " + actionName);
        }
        return new GuiAction.Run(action);
    }

    private static List<String> readStringList(ConfigurationNode node) {
        try {
            List<String> values = node.getList(String.class);
            return values == null ? List.of() : values;
        } catch (SerializationException notAList) {
            // A scalar where a list is expected is simply treated as absent.
            return List.of();
        }
    }
}
