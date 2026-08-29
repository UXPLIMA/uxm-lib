package com.uxplima.uxmlib.hologram;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

/**
 * Wraps any {@link Hologram} to fire {@code onShow} / {@code onHide} hooks when a viewer's visibility
 * actually changes: handy for play-a-sound-on-reveal, analytics, or lazily computing a viewer's content.
 * Every other call is delegated unchanged, so an {@code ObservableHologram} is a drop-in for the wrapped
 * one.
 *
 * <p>A hook fires only on a real transition: showing an already-visible viewer, or hiding one who never saw
 * the hologram, fires nothing (the transition is decided from the delegate's tracked viewer set before the
 * change). A hook that throws is isolated: one broken listener never aborts the visibility change or the
 * other hooks. Register hooks before use; the lists are copy-on-write, so adding one mid-flight is safe.
 */
public final class ObservableHologram implements Hologram {

    private final Hologram delegate;
    private final List<Consumer<Player>> showHooks = new CopyOnWriteArrayList<>();
    private final List<Consumer<Player>> hideHooks = new CopyOnWriteArrayList<>();

    private ObservableHologram(Hologram delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /** Wrap {@code delegate} so its visibility changes can be observed. */
    public static ObservableHologram wrapping(Hologram delegate) {
        return new ObservableHologram(delegate);
    }

    /** Register a hook fired when a viewer is newly shown the hologram. Returns {@code this} for chaining. */
    public ObservableHologram onShow(Consumer<Player> hook) {
        showHooks.add(Objects.requireNonNull(hook, "hook"));
        return this;
    }

    /** Register a hook fired when a viewer is newly hidden from the hologram. Returns {@code this}. */
    public ObservableHologram onHide(Consumer<Player> hook) {
        hideHooks.add(Objects.requireNonNull(hook, "hook"));
        return this;
    }

    @Override
    public void show(Plugin plugin, Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        boolean wasVisible = delegate.isVisibleTo(viewer);
        delegate.show(plugin, viewer);
        if (!wasVisible) {
            fire(showHooks, viewer);
        }
    }

    @Override
    public void hide(Plugin plugin, Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        boolean wasVisible = delegate.isVisibleTo(viewer);
        delegate.hide(plugin, viewer);
        if (wasVisible) {
            fire(hideHooks, viewer);
        }
    }

    private static void fire(List<Consumer<Player>> hooks, Player viewer) {
        for (Consumer<Player> hook : hooks) {
            try {
                hook.accept(viewer);
            } catch (RuntimeException failure) {
                // A listener's failure must never abort the visibility change or the remaining hooks; isolate it.
            }
        }
    }

    @Override
    public void setText(Component text) {
        delegate.setText(text);
    }

    @Override
    public void moveTo(Location to, int interpolationTicks) {
        delegate.moveTo(to, interpolationTicks);
    }

    @Override
    public void setTransform(Transform transform) {
        delegate.setTransform(transform);
    }

    @Override
    public boolean attachTo(Entity target) {
        return delegate.attachTo(target);
    }

    @Override
    public void restrictToViewers() {
        delegate.restrictToViewers();
    }

    @Override
    public boolean isVisibleTo(Player viewer) {
        return delegate.isVisibleTo(viewer);
    }

    @Override
    public void forgetViewer(UUID viewer) {
        delegate.forgetViewer(viewer);
    }

    @Override
    public void remove() {
        delegate.remove();
    }

    @Override
    public TextDisplay entity() {
        return delegate.entity();
    }
}
