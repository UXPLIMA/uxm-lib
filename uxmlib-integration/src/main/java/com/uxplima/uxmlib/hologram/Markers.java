package com.uxplima.uxmlib.hologram;

import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/**
 * Stamps every hologram-related entity (text/item/block displays and interaction boxes) with a persistent
 * marker so it can be told apart from any other entity in the world. Because these entities persist, this
 * lets {@link HologramManager} sweep up holograms orphaned by a crash on the next startup. The key is
 * created once, never on a hot path.
 */
final class Markers {

    private static final NamespacedKey KEY =
            Objects.requireNonNull(NamespacedKey.fromString("uxmlib:hologram"), "marker key");

    private static final NamespacedKey OWNER =
            Objects.requireNonNull(NamespacedKey.fromString("uxmlib:hologram_owner"), "owner key");

    private Markers() {}

    /** Record which plugin spawned {@code entity}, so a sweep at startup removes only that plugin's leftovers. */
    static void own(Entity entity, String owner) {
        entity.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, owner);
    }

    /** The plugin that spawned {@code entity}, or an empty string for one stamped before owners were recorded. */
    static String ownerOf(Entity entity) {
        String owner = entity.getPersistentDataContainer().get(OWNER, PersistentDataType.STRING);
        return owner == null ? "" : owner;
    }

    /** Tag {@code entity} as part of a uxmLib hologram. */
    static void stamp(Entity entity) {
        entity.getPersistentDataContainer().set(KEY, PersistentDataType.BOOLEAN, true);
    }

    /** Whether {@code entity} carries the uxmLib hologram marker. */
    static boolean isHologram(Entity entity) {
        return entity.getPersistentDataContainer().has(KEY, PersistentDataType.BOOLEAN);
    }
}
