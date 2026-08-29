package com.uxplima.uxmlib.hologram;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import org.jspecify.annotations.Nullable;

/**
 * A hologram whose text is computed per viewer: each player sees a line rendered just for them by a
 * {@code Function<Player, Component>} (their name, balance, distance, …). Call {@link #setText} once with the
 * renderer, then {@link #update(Plugin, Player)} when a viewer should (re)compute, or {@link #updateAll()}
 * each tick from your {@code Scheduler} to refresh everyone.
 *
 * <h2>Why one entity per viewer</h2>
 * A single shared {@code TextDisplay} carries one text value for all players, so without packets you cannot
 * show two players different text off the same entity. The native, packet-free way to get genuinely
 * per-viewer text is therefore one private, viewer-restricted {@code TextDisplay} per player: this class
 * spawns one on a viewer's first update and shows it only to them. The trade-off is N entities for N
 * viewers: fine for the handful of players near a hologram, but not for a server-wide broadcast (use a
 * shared {@link DisplayHologram} there). Spawning and the {@code show/hide} calls must run on the region
 * thread (Folia); schedule {@link #updateAll()} through the library {@code Scheduler}.
 */
public final class PerViewerHologram {

    private final Function<Location, TextDisplay> spawner;
    private final Location anchor;
    private final Map<UUID, Player> viewers = new ConcurrentHashMap<>();
    private final Map<UUID, TextDisplay> entities = new ConcurrentHashMap<>();
    private @Nullable Function<Player, Component> render;

    /**
     * @param spawner spawns a fresh viewer-restricted {@code TextDisplay} at the given location (the
     *     production spawner restricts visibility and stamps the marker; tests pass a recording one)
     * @param anchor the location every viewer's private entity is spawned at
     */
    public PerViewerHologram(Function<Location, TextDisplay> spawner, Location anchor) {
        this.spawner = Objects.requireNonNull(spawner, "spawner");
        this.anchor = Objects.requireNonNull(anchor, "anchor").clone();
    }

    /** Set the per-viewer text renderer. Must be called before any {@link #update(Plugin, Player)}. */
    public void setText(Function<Player, Component> render) {
        this.render = Objects.requireNonNull(render, "render");
    }

    /**
     * Compute {@code viewer}'s text and apply it to their private entity, spawning and showing that entity to
     * them on first call. Re-uses the existing entity on later calls (a refresh, not a re-spawn).
     */
    public void update(Plugin plugin, Player viewer) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(viewer, "viewer");
        Function<Player, Component> renderer = requireRenderer();
        UUID id = viewer.getUniqueId();
        TextDisplay entity = entities.get(id);
        if (entity == null) {
            entity = spawner.apply(anchor);
            entities.put(id, entity);
            viewers.put(id, viewer);
            viewer.showEntity(plugin, entity);
        }
        entity.text(renderer.apply(viewer));
    }

    /** Recompute the text for every active viewer. Cheap to call every tick from a scheduler. */
    public void updateAll() {
        Function<Player, Component> renderer = requireRenderer();
        for (Map.Entry<UUID, Player> viewer : viewers.entrySet()) {
            TextDisplay entity = entities.get(viewer.getKey());
            if (entity != null) {
                entity.text(renderer.apply(viewer.getValue()));
            }
        }
    }

    /** Despawn {@code viewer}'s private entity and stop tracking them. */
    public void removeViewer(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        forget(viewer.getUniqueId());
    }

    /** Drop a viewer by id (for a quit/world-change), despawning their entity. A no-op if untracked. */
    public void forgetViewer(UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        forget(viewer);
    }

    private void forget(UUID id) {
        viewers.remove(id);
        TextDisplay entity = entities.remove(id);
        if (entity != null) {
            entity.remove();
        }
    }

    /** How many viewers currently have a private entity. */
    public int viewerCount() {
        return viewers.size();
    }

    /** Despawn every viewer's private entity. Safe to call more than once. */
    public void remove() {
        for (TextDisplay entity : entities.values()) {
            entity.remove();
        }
        entities.clear();
        viewers.clear();
    }

    private Function<Player, Component> requireRenderer() {
        Function<Player, Component> renderer = render;
        if (renderer == null) {
            throw new IllegalStateException("setText(Function) must be called before update");
        }
        return renderer;
    }
}
