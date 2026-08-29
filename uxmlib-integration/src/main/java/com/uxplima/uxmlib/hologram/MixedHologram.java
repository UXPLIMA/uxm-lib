package com.uxplima.uxmlib.hologram;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * A spawned mixed-line hologram: a column of native {@code Display} entities (one per {@link HologramLine}),
 * managed as a single unit. Removal and per-viewer visibility fan out across every part, so a multi-type
 * hologram shows, hides, and despawns atomically. Like a {@link DisplayHologram}, visibility uses Paper's
 * {@code show/hideEntity} over a tracked viewer set: no packets.
 *
 * <p>The mutating calls must run on the parts' region thread (Folia); the tracked viewer set is concurrent
 * so a quit-driven {@link #forgetViewer(UUID)} from another thread is safe.
 */
public final class MixedHologram {

    private final List<Display> parts;
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();

    MixedHologram(List<Display> parts) {
        Objects.requireNonNull(parts, "parts");
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("a mixed hologram needs at least one part");
        }
        this.parts = List.copyOf(parts);
    }

    /** The backing display entities, top-to-bottom in line order. */
    public List<Display> parts() {
        return parts;
    }

    /** Make every part visible only to explicitly shown players (native per-viewer visibility). */
    public void restrictToViewers() {
        for (Display part : parts) {
            part.setVisibleByDefault(false);
        }
    }

    /** Show every part to {@code viewer} via {@code plugin} (only meaningful after restriction). */
    public void show(Plugin plugin, Player viewer) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(viewer, "viewer");
        viewers.add(viewer.getUniqueId());
        for (Display part : parts) {
            viewer.showEntity(plugin, part);
        }
    }

    /** Hide every part from {@code viewer} via {@code plugin}. */
    public void hide(Plugin plugin, Player viewer) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(viewer, "viewer");
        viewers.remove(viewer.getUniqueId());
        for (Display part : parts) {
            viewer.hideEntity(plugin, part);
        }
    }

    /** Whether {@code viewer} is in the allowed-viewer set. */
    public boolean isVisibleTo(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return viewers.contains(viewer.getUniqueId());
    }

    /** Drop {@code viewer} from the tracked allowed-viewer set without sending any packet. */
    public void forgetViewer(UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        viewers.remove(viewer);
    }

    /** Despawn every part. Safe to call more than once. */
    public void remove() {
        for (Display part : parts) {
            part.remove();
        }
    }
}
