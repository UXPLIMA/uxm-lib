package com.uxplima.uxmlib.gui.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.spongepowered.configurate.ConfigurationNode;

/**
 * Upgrades a menu config tree from older key names to the current ones, in place. A menu config saved by an
 * earlier release used a few different field names; rather than make an operator rename them by hand on
 * upgrade, {@link MenuConfig#load} runs the tree through here first, so an old file loads unchanged in
 * behaviour. Each rename only fires when the legacy key is present and the current key is not, so a file
 * already on the current names (or one that has both) is left untouched.
 */
final class MenuConfigMigration {

    // Legacy -> current at the menu root. Insertion order is the rename order, which does not matter here
    // because every pair targets a distinct current key.
    private static final Map<String, String> ROOT_RENAMES = rootRenames();

    // Legacy -> current inside one item/state entry (and so applied to every entry under `items`).
    private static final Map<String, String> ITEM_RENAMES = itemRenames();

    private MenuConfigMigration() {}

    static void apply(ConfigurationNode menu) {
        renameKeys(menu, ROOT_RENAMES);
        ConfigurationNode items = menu.node("items");
        for (ConfigurationNode item : items.childrenMap().values()) {
            migrateItem(item);
        }
    }

    private static void migrateItem(ConfigurationNode item) {
        renameKeys(item, ITEM_RENAMES);
        // A state variant carries the same icon fields as a top-level item, so apply the item renames there too.
        for (ConfigurationNode state : item.node("states").childrenMap().values()) {
            renameKeys(state, ITEM_RENAMES);
        }
    }

    private static void renameKeys(ConfigurationNode parent, Map<String, String> renames) {
        for (Map.Entry<String, String> rename : renames.entrySet()) {
            ConfigurationNode legacy = parent.node(rename.getKey());
            ConfigurationNode current = parent.node(rename.getValue());
            // Only move when the old key is set and the new one is not, so a user already on the current names
            // (or one who set both) keeps their value rather than having it clobbered.
            if (!legacy.virtual() && current.virtual()) {
                current.from(legacy); // deep-copies scalars, lists, and subtrees alike
            }
            legacy.raw(null); // drop the legacy key so the upgraded tree carries only current names
        }
    }

    private static Map<String, String> rootRenames() {
        Map<String, String> renames = new LinkedHashMap<>();
        renames.put("slots", "mask");
        renames.put("layout", "mask");
        renames.put("title-text", "title");
        renames.put("size", "rows");
        return renames;
    }

    private static Map<String, String> itemRenames() {
        Map<String, String> renames = new LinkedHashMap<>();
        renames.put("material-type", "material");
        renames.put("type", "material");
        renames.put("display-name", "name");
        return renames;
    }
}
