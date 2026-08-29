package com.uxplima.uxmlib.hologram.follow;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmlib.hologram.Hologram;

/**
 * A third-person-only nameplate: a follow hologram shown to nearby players but never to its own wearer, the
 * way a name-tag floats over a player for everyone else but not for them. It restricts the hologram to
 * explicit viewers (native {@code setVisibleByDefault(false)}) at construction, then {@link #refreshViewers}
 * shows it to the current nearby players minus the wearer and hides it from anyone who has left that set:
 * all through Paper's per-viewer {@code show/hideEntity}, no packets.
 *
 * <p>Pair it with {@link HologramFollow} to keep the nameplate above the wearer's head: follow for the
 * movement, and call {@link #refreshViewers} each tick from your {@code Scheduler} with the nearby players.
 * The {@code show/hide} calls must run on the hologram's region thread (Folia).
 */
public final class SelfHiddenNameplate {

    private final Hologram hologram;
    private final UUID wearer;
    // Keep each viewer's Player handle, not just its id: hiding from someone who has left the candidate set
    // needs a Player to pass to the native hideEntity, and they are by definition no longer in the candidates.
    private final Map<UUID, Player> currentViewers = new HashMap<>();

    /**
     * @param hologram the nameplate hologram; it is restricted to explicit viewers here
     * @param wearer the player the nameplate rides: it is never shown to them
     */
    public SelfHiddenNameplate(Hologram hologram, Player wearer) {
        this.hologram = Objects.requireNonNull(hologram, "hologram");
        this.wearer = Objects.requireNonNull(wearer, "wearer").getUniqueId();
        hologram.restrictToViewers();
    }

    /**
     * Reconcile who sees the nameplate: show it to every candidate that is not the wearer and is not already
     * a viewer, and hide it from any current viewer no longer among the candidates. Idempotent, so it is
     * safe to call every tick with the nearby players.
     */
    public void refreshViewers(Plugin plugin, Collection<? extends Player> candidates) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(candidates, "candidates");
        Set<UUID> wanted = new HashSet<>();
        for (Player candidate : candidates) {
            if (!candidate.getUniqueId().equals(wearer)) {
                addViewer(plugin, candidate, wanted);
            }
        }
        dropDepartedViewers(plugin, wanted);
    }

    private void addViewer(Plugin plugin, Player candidate, Set<UUID> wanted) {
        UUID id = candidate.getUniqueId();
        wanted.add(id);
        if (currentViewers.putIfAbsent(id, candidate) == null) {
            hologram.show(plugin, candidate);
        }
    }

    private void dropDepartedViewers(Plugin plugin, Set<UUID> wanted) {
        Iterator<Map.Entry<UUID, Player>> it = currentViewers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Player> viewer = it.next();
            if (!wanted.contains(viewer.getKey())) {
                hologram.hide(plugin, viewer.getValue());
                it.remove();
            }
        }
    }
}
