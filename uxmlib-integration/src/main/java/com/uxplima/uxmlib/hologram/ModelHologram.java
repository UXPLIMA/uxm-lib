package com.uxplima.uxmlib.hologram;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * A spawned hologram backed by a single non-text {@link Display}: an {@code ItemDisplay} showing an
 * {@code ItemStack} ({@link ItemHologram}) or a {@code BlockDisplay} showing {@code BlockData}
 * ({@link BlockHologram}). It shares every lifecycle a {@link Hologram} has except the text-specific setter:
 * move, transform, mount, per-viewer visibility and removal all behave identically, so a consumer can hold an
 * item or block hologram the same way it holds a text one. Per-viewer visibility uses Paper's native
 * {@code show/hideEntity}, not packets.
 *
 * <p>The mutating calls (a content swap, a move, the {@code show/hide} pair) must run on the entity's region
 * thread (Folia), so route them through your scheduler. The content-swap method is the only type-specific
 * difference and lives on each implementation ({@link ItemHologram#setItem} / {@link BlockHologram#setBlock}).
 */
public interface ModelHologram {

    /**
     * Move the hologram to {@code to}, interpolating over {@code interpolationTicks} so current viewers see
     * smooth motion instead of a jump (native {@code setTeleportDuration}). Pass 0 for an instant move.
     */
    void moveTo(Location to, int interpolationTicks);

    /** Re-apply a scale/rotation {@link Transform} to the live entity (no re-spawn). */
    void setTransform(Transform transform);

    /**
     * Mount the hologram on {@code target} as a passenger so it rides exactly with it (native
     * {@code addPassenger}). Exact-mount only, for an above-the-head offset, follow with a scheduler task.
     * Returns whether the mount succeeded.
     */
    boolean attachTo(org.bukkit.entity.Entity target);

    /** Make this hologram visible only to explicitly shown players (native per-viewer visibility). */
    void restrictToViewers();

    /** Show the hologram to {@code viewer} via {@code plugin} (only meaningful after restriction). */
    void show(Plugin plugin, Player viewer);

    /** Hide the hologram from {@code viewer} via {@code plugin}. */
    void hide(Plugin plugin, Player viewer);

    /** Whether {@code viewer} is in the allowed-viewer set. */
    boolean isVisibleTo(Player viewer);

    /**
     * Drop {@code viewer} from the tracked allowed-viewer set without sending any packet. Called when a player
     * quits or changes world so the per-UUID viewer cache does not leak or go stale; the next
     * {@link #show(Plugin, Player)} re-establishes visibility cleanly. A no-op if the UUID is not tracked.
     */
    void forgetViewer(java.util.UUID viewer);

    /** Despawn the backing display entity. Safe to call more than once. */
    void remove();

    /** The backing display entity. */
    Display entity();
}
