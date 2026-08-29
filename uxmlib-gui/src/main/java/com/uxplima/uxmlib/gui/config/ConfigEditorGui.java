package com.uxplima.uxmlib.gui.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.config.HoconConfig;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.input.InputResult;
import com.uxplima.uxmlib.gui.input.InputType;
import com.uxplima.uxmlib.gui.input.PlayerInput;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.text.Text;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * An in-game editor that browses a {@link HoconConfig}'s keys as a paginated menu and lets an admin retype a
 * scalar value. Each key under the current path is an icon: a section opens a child editor one level deeper,
 * a scalar opens a {@link PlayerInput} prompt to type a new value, which {@link ConfigValueEditor} coerces to
 * the key's type, validates, sets, and saves before the menu refreshes. Built on {@link PaginatedGui} so a
 * config with many keys pages cleanly; the type-and-validate logic lives in {@link ConfigValueEditor} so this
 * class stays a thin renderer/controller within its size cap.
 *
 * <p>Open it with {@link #open(Player)}. A reload-aware host should reopen it after an external reload, since
 * the icons snapshot the config at build time.
 */
public final class ConfigEditorGui {

    private final HoconConfig config;
    private final PlayerInput input;
    private final ConfigValueEditor editor;
    private final InputType inputType;
    private final String path;

    /** An editor rooted at the top of {@code config}, capturing values through {@code input}. */
    public ConfigEditorGui(HoconConfig config, PlayerInput input) {
        this(config, input, InputType.CHAT, "");
    }

    /**
     * @param config the config browsed and written
     * @param input the prompt backend used to capture a typed value
     * @param inputType which {@link InputType} the value prompt uses (chat or anvil)
     * @param path the dotted path this editor is rooted at ({@code ""} = the whole config)
     */
    public ConfigEditorGui(HoconConfig config, PlayerInput input, InputType inputType, String path) {
        this.config = Objects.requireNonNull(config, "config");
        this.input = Objects.requireNonNull(input, "input");
        this.editor = new ConfigValueEditor(config);
        this.inputType = Objects.requireNonNull(inputType, "inputType");
        this.path = Objects.requireNonNull(path, "path");
    }

    /** Build the paginated menu of this path's keys and open it for {@code viewer}. */
    public void open(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        build().open(viewer);
    }

    /** Build (but do not open) the paginated menu, for a caller that opens it through a navigator. */
    public PaginatedGui build() {
        Component title = Text.mini(path.isEmpty() ? "<dark_gray>Config" : "<dark_gray>Config: <gray>" + path);
        PaginatedGui gui = Guis.paginated().title(title).rows(6).build();
        for (Map.Entry<Object, ? extends ConfigurationNode> child :
                currentNode().childrenMap().entrySet()) {
            gui.addPageItem(iconFor(viewerKey(String.valueOf(child.getKey())), child.getValue()));
        }
        addNavigation(gui);
        gui.render(); // project the first page so the menu is ready to inspect or open
        return gui;
    }

    // The node this editor is rooted at: the whole config when the path is empty (an empty dotted path does
    // not address the root), else the node at that path.
    private ConfigurationNode currentNode() {
        return path.isEmpty() ? config.root() : config.nodeAt((Object[]) path.split("\\."));
    }

    private void addNavigation(PaginatedGui gui) {
        gui.set(48, GuiItem.button(arrow("<yellow>Previous"), e -> gui.previousPage()));
        gui.set(50, GuiItem.button(arrow("<yellow>Next"), e -> gui.nextPage()));
    }

    private String viewerKey(String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    private GuiItem iconFor(String fullPath, ConfigurationNode node) {
        if (node.isMap()) {
            return GuiItem.button(sectionIcon(fullPath), e -> openChild(e.getWhoClicked(), fullPath));
        }
        return GuiItem.button(scalarIcon(fullPath, node), e -> promptValue(e.getWhoClicked(), fullPath));
    }

    private void openChild(org.bukkit.entity.HumanEntity who, String fullPath) {
        if (who instanceof Player player) {
            new ConfigEditorGui(config, input, inputType, fullPath).open(player);
        }
    }

    private void promptValue(org.bukkit.entity.HumanEntity who, String fullPath) {
        if (!(who instanceof Player player)) {
            return;
        }
        Component prompt = Text.mini("<gray>Enter a new value for <yellow>" + fullPath);
        player.closeInventory();
        input.open(player, inputType, prompt, result -> applyInput(player, fullPath, result));
    }

    private void applyInput(Player player, String fullPath, InputResult result) {
        if (!(result instanceof InputResult.Submitted submitted)) {
            reopen(player);
            return;
        }
        try {
            editor.setAndSave(fullPath, submitted.text());
            player.sendMessage(Text.mini("<green>Set <yellow>" + fullPath + " <green>to <white>" + submitted.text()));
        } catch (IllegalArgumentException invalid) {
            player.sendMessage(Text.mini("<red>" + invalid.getMessage()));
        }
        reopen(player);
    }

    private void reopen(Player player) {
        open(player); // rebuild from the now-updated config so the new value shows
    }

    private static org.bukkit.inventory.ItemStack sectionIcon(String fullPath) {
        return ItemBuilder.of(Material.BOOK)
                .name(Text.mini("<aqua>" + lastSegment(fullPath)))
                .lore(Text.mini("<dark_gray>section: click to open"))
                .build();
    }

    private static org.bukkit.inventory.ItemStack scalarIcon(String fullPath, ConfigurationNode node) {
        String value = String.valueOf(node.raw());
        List<Component> lore = new ArrayList<>();
        lore.add(Text.mini("<gray>= <white>" + value));
        lore.add(Text.mini("<dark_gray>click to edit"));
        return ItemBuilder.of(Material.PAPER)
                .name(Text.mini("<yellow>" + lastSegment(fullPath)))
                .lore(lore)
                .build();
    }

    private static org.bukkit.inventory.ItemStack arrow(String label) {
        return ItemBuilder.of(Material.ARROW).name(Text.mini(label)).build();
    }

    private static String lastSegment(String fullPath) {
        int dot = fullPath.lastIndexOf('.');
        return dot < 0 ? fullPath : fullPath.substring(dot + 1);
    }
}
