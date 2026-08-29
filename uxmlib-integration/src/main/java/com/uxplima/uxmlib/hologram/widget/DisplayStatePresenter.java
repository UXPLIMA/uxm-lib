package com.uxplima.uxmlib.hologram.widget;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmlib.hologram.Hologram;
import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * The production {@link StatePresenter}: each state value maps to one restricted {@link Hologram}, and a
 * {@code show}/{@code hide} resolves the UUID to an online player and runs the native per-viewer call on that
 * state's entity region thread through the {@link Scheduler}: the same Folia-safe pattern the pool's
 * {@code SchedulerViewerSink} uses. A UUID with no online player is skipped; an unknown state value (no
 * mapped hologram) is a no-op rather than a throw, so a {@link SwitchSelection} state can be display-only.
 *
 * @param <T> the state value type the switchable switches between
 */
public final class DisplayStatePresenter<T> implements StatePresenter<T> {

    private final Map<T, Hologram> holograms;
    private final Plugin plugin;
    private final Scheduler scheduler;

    public DisplayStatePresenter(Map<T, Hologram> holograms, Plugin plugin, Scheduler scheduler) {
        Objects.requireNonNull(holograms, "holograms");
        this.holograms = Map.copyOf(holograms);
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void show(T state, UUID viewer) {
        Hologram hologram = holograms.get(Objects.requireNonNull(state, "state"));
        Player player = Bukkit.getPlayer(Objects.requireNonNull(viewer, "viewer"));
        if (hologram != null && player != null) {
            scheduler.entity(hologram.entity(), () -> hologram.show(plugin, player));
        }
    }

    @Override
    public void hide(T state, UUID viewer) {
        Hologram hologram = holograms.get(Objects.requireNonNull(state, "state"));
        Player player = Bukkit.getPlayer(Objects.requireNonNull(viewer, "viewer"));
        if (hologram != null && player != null) {
            scheduler.entity(hologram.entity(), () -> hologram.hide(plugin, player));
        }
    }
}
