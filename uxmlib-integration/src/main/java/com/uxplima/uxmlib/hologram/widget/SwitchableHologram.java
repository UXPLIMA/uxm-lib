package com.uxplima.uxmlib.hologram.widget;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmlib.hologram.HologramLifecycle;
import org.jspecify.annotations.Nullable;

/**
 * A per-player conditional hologram: several overlapping content states live at one location and each viewer
 * sees whichever state's condition passes first for it: the "conditional pages" widget, generalising
 * {@link PagedHologram} from an index to an arbitrary {@link SwitchSelection}. {@link #refresh} re-evaluates a
 * viewer's state from a {@link ViewerContext} (its UUID plus a stat lookup, so a state can switch on a
 * permission tier, a balance or a score) and, when the selected state changed, hides the state the viewer was
 * on and shows the new one, for that viewer only, mirroring the GUI {@code Stateful} item.
 *
 * <p>The first-match logic is the pure {@link SwitchSelection}; the show/hide goes through a
 * {@link StatePresenter} so the widget is unit-testable with no live entity. It implements
 * {@link HologramLifecycle}, so registered with the {@code HologramManager} a viewer's state is hidden and
 * forgotten on quit / world-change with no per-consumer Bukkit listener.
 *
 * @param <T> the state value type (a hologram handle, a content spec, a label)
 */
public final class SwitchableHologram<T> implements HologramLifecycle {

    private final SwitchSelection<T> selection;
    private final StatePresenter<T> presenter;
    private final Map<UUID, T> shownState = new ConcurrentHashMap<>();

    public SwitchableHologram(SwitchSelection<T> selection, StatePresenter<T> presenter) {
        this.selection = Objects.requireNonNull(selection, "selection");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
    }

    /**
     * Re-evaluate which state {@code context}'s viewer is in and apply the delta: hide the state it was on
     * (if any), show the newly selected state (if any). A no-op when the selected state is unchanged.
     */
    public void refresh(ViewerContext context) {
        Objects.requireNonNull(context, "context");
        UUID viewer = context.player();
        @Nullable T previous = shownState.get(viewer);
        @Nullable T selected = selection.select(context).orElse(null);
        if (Objects.equals(previous, selected)) {
            return;
        }
        if (previous != null) {
            presenter.hide(previous, viewer);
        }
        if (selected != null) {
            presenter.show(selected, viewer);
            shownState.put(viewer, selected);
        } else {
            shownState.remove(viewer);
        }
    }

    /** The state {@code viewer} is currently shown, or empty when it matches none. */
    public Optional<T> shownState(UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return Optional.ofNullable(shownState.get(viewer));
    }

    @Override
    public void onQuit(UUID player) {
        reset(player);
    }

    @Override
    public void onWorldChange(UUID player) {
        reset(player);
    }

    @Override
    public void onRespawn(UUID player) {
        reset(player);
    }

    private void reset(UUID player) {
        Objects.requireNonNull(player, "player");
        @Nullable T previous = shownState.remove(player);
        if (previous != null) {
            presenter.hide(previous, player);
        }
    }
}
