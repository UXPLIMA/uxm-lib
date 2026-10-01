package com.uxplima.uxmlib.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * The upgrade operations on a live config tree (schema migration and defaults merge) kept out of
 * {@code HoconConfig} so that class stays within its size cap. Each takes the live root and a save
 * callback; the caller (HoconConfig) holds the lock and supplies them.
 */
final class ConfigUpgrade {

    private ConfigUpgrade() {}

    /** Replay only newer migration steps, save once if the version changed, return the resulting version. */
    static int migrate(CommentedConfigurationNode live, ConfigMigration migration, Runnable save) {
        int from = migration.versionOf(live);
        int to = migration.apply(live);
        if (to != from) {
            save.run();
        }
        return to;
    }

    /**
     * Additively merge {@code defaults} into {@code live}, save once if anything was added; return whether.
     *
     * <p>{@code keep} is run before the save and only when something was added: it is where the file as it
     * was is copied aside. The merge is careful, and it still renders the whole document again from the
     * tree, so an operator's own alignment and a comment in an unusual place can move. A copy costs nothing
     * and it is the difference between a bad merge being an annoyance and being a loss.
     */
    static boolean mergeDefaults(
            CommentedConfigurationNode live, ConfigurationNode defaults, Runnable keep, Runnable save) {
        int before = ConfigDefaults.nodeCount(live);
        fillAbsent(live, defaults);
        if (ConfigDefaults.nodeCount(live) != before) {
            keep.run();
            save.run();
            return true;
        }
        return false;
    }

    /**
     * Deep-merge an included tree into {@code live} with the same base-wins semantics as a defaults merge:
     * keys already present in {@code live} keep their value, the included tree fills the gaps, but purely
     * in memory, with no save. Returns whether the included tree contributed anything new.
     */
    static boolean include(CommentedConfigurationNode live, ConfigurationNode included) {
        int before = ConfigDefaults.nodeCount(live);
        fillAbsent(live, included);
        return ConfigDefaults.nodeCount(live) != before;
    }

    /**
     * Add every key of {@code source} that {@code target} does not have, descending where both hold a map. A key
     * {@code target} has keeps its value whatever it is: an empty string, an empty list and an empty map are values
     * an operator wrote. Configurate's own {@code mergeFrom} fills an empty value as though the key were absent, so a
     * message cleared to silence it and a list emptied to turn it off came back on the next start.
     */
    private static void fillAbsent(ConfigurationNode target, ConfigurationNode source) {
        for (var child : source.childrenMap().entrySet()) {
            ConfigurationNode existing = target.node(child.getKey());
            ConfigurationNode shipped = child.getValue();
            if (existing.virtual()) {
                existing.from(shipped);
            } else if (existing.isMap() && shipped.isMap()) {
                fillAbsent(existing, shipped);
            }
        }
    }
}
