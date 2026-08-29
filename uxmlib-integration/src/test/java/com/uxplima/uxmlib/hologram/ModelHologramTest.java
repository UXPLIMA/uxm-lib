package com.uxplima.uxmlib.hologram;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import org.junit.jupiter.api.Test;

/**
 * The {@link ModelHologram} side of the {@link HologramManager}: an item or block hologram is tracked, counted
 * alongside the text holograms, despawned by {@link HologramManager#removeAll()}, and dropped from a viewer's
 * cache on {@link HologramManager#invalidateViewer(UUID)}. MockBukkit cannot spawn an {@code ItemDisplay} or
 * {@code BlockDisplay}, so the manager's fan-out is exercised against a recording double exactly as the text
 * side's viewer logic is: the live content swap is covered by the plugin's render path. {@link ItemHologram}
 * and {@link BlockHologram} inherit their visibility/move/remove behaviour unchanged from the already-tested
 * {@link DisplayHologram} shape via {@link AbstractModelHologram}.
 */
class ModelHologramTest {

    @Test
    void tracksAndRemovesAModelHologram() {
        HologramManager manager = new HologramManager();
        RecordingModelHologram hologram = new RecordingModelHologram();

        manager.track(hologram);
        assertThat(manager.count()).isEqualTo(1);

        manager.remove(hologram);
        assertThat(manager.count()).isZero();
        assertThat(hologram.removed).isEqualTo(1);
    }

    @Test
    void removeAllDespawnsTextAndModelHologramsTogether() {
        HologramManager manager = new HologramManager();
        RecordingHologram text = new RecordingHologram();
        RecordingModelHologram model = new RecordingModelHologram();
        manager.track(text);
        manager.track(model);

        manager.removeAll();

        assertThat(manager.count()).isZero();
        assertThat(text.removed).isEqualTo(1);
        assertThat(model.removed).isEqualTo(1);
    }

    @Test
    void invalidateViewerDropsTheViewerFromAModelHologram() {
        HologramManager manager = new HologramManager();
        RecordingModelHologram model = new RecordingModelHologram();
        manager.track(model);
        UUID viewer = new UUID(0, 7);

        manager.invalidateViewer(viewer);

        assertThat(model.forgotten).containsExactly(viewer);
    }

    /** A {@link ModelHologram} with no Bukkit backing that records the calls the manager makes to it. */
    private static final class RecordingModelHologram implements ModelHologram {
        int removed;
        final List<UUID> forgotten = new ArrayList<>();

        @Override
        public void moveTo(Location to, int interpolationTicks) {}

        @Override
        public void setTransform(Transform transform) {}

        @Override
        public boolean attachTo(Entity target) {
            return false;
        }

        @Override
        public void restrictToViewers() {}

        @Override
        public void show(Plugin plugin, Player viewer) {}

        @Override
        public void hide(Plugin plugin, Player viewer) {}

        @Override
        public boolean isVisibleTo(Player viewer) {
            return false;
        }

        @Override
        public void forgetViewer(UUID viewer) {
            forgotten.add(viewer);
        }

        @Override
        public void remove() {
            removed++;
        }

        @Override
        public Display entity() {
            throw new UnsupportedOperationException("no entity in the recording double");
        }
    }
}
