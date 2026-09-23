package com.uxplima.uxmlib.hologram;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The shared {@link Display} lifecycle behind {@link ItemHologram} and {@link BlockHologram}: move, transform,
 * mount, per-viewer visibility and removal over a single backing entity, identical to {@link DisplayHologram}'s
 * but for the text-only setter. The two subclasses add only their type-specific content-swap, so the move and
 * visibility logic is written and tested once here.
 *
 * @param <D> the backing display type ({@code ItemDisplay} or {@code BlockDisplay})
 */
abstract class AbstractModelHologram<D extends Display> implements ModelHologram {

    final D display;
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();

    AbstractModelHologram(D display) {
        this.display = Objects.requireNonNull(display, "display");
    }

    @Override
    public final void moveTo(Location to, int interpolationTicks) {
        Objects.requireNonNull(to, "to");
        if (interpolationTicks < 0) {
            throw new IllegalArgumentException("interpolationTicks must be >= 0");
        }
        display.setTeleportDuration(interpolationTicks);
        // teleportAsync and never teleport: Folia refuses the in-place move for every entity, a display on its
        // own region's thread included. On Paper the two do the same thing.
        java.util.concurrent.CompletableFuture<Boolean> unused = display.teleportAsync(to)
                .exceptionally(failure -> {
                    System.getLogger(getClass().getName())
                            .log(System.Logger.Level.WARNING, "A hologram could not be moved to " + to, failure);
                    return false;
                });
    }

    @Override
    public final void setTransform(Transform transform) {
        Objects.requireNonNull(transform, "transform");
        display.setTransformation(transform.toBukkit());
    }

    @Override
    public final boolean attachTo(org.bukkit.entity.Entity target) {
        Objects.requireNonNull(target, "target");
        return target.addPassenger(display);
    }

    @Override
    public final void restrictToViewers() {
        display.setVisibleByDefault(false);
    }

    @Override
    public final void show(Plugin plugin, Player viewer) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(viewer, "viewer");
        viewers.add(viewer.getUniqueId());
        viewer.showEntity(plugin, display);
    }

    @Override
    public final void hide(Plugin plugin, Player viewer) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(viewer, "viewer");
        viewers.remove(viewer.getUniqueId());
        viewer.hideEntity(plugin, display);
    }

    @Override
    public final boolean isVisibleTo(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return viewers.contains(viewer.getUniqueId());
    }

    @Override
    public final void forgetViewer(UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        viewers.remove(viewer);
    }

    @Override
    public final void remove() {
        display.remove();
    }

    @Override
    public final D entity() {
        return display;
    }
}
