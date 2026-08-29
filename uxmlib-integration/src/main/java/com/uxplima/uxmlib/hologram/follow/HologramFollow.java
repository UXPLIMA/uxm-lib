package com.uxplima.uxmlib.hologram.follow;

import java.time.Duration;
import java.util.Objects;

import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import com.uxplima.uxmlib.hologram.Hologram;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;

/**
 * Makes a hologram follow an entity at an offset: an above-the-head nameplate that {@code attachTo}
 * cannot do (a passenger rides at the exact mount point, with no offset). It teleports the hologram to
 * {@code target.getLocation().add(offset)} each tick through the {@link Scheduler}, using a short
 * interpolation so the motion is smooth. The task self-cancels when the target is removed.
 */
public final class HologramFollow {

    private final Scheduler scheduler;

    public HologramFollow(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Follow {@code target} at {@code offset}, updating every {@code period}. Returns a stop handle. */
    public TaskHandle follow(Hologram hologram, Entity target, Vector offset, Duration period) {
        Objects.requireNonNull(hologram, "hologram");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(offset, "offset");
        Objects.requireNonNull(period, "period");
        int ticks = Math.max(1, (int) (period.toMillis() / 50L));
        return scheduler.entityTimer(target, Duration.ZERO, period, handle -> {
            if (target.isValid()) {
                hologram.moveTo(target.getLocation().add(offset), ticks);
            } else {
                handle.cancel();
            }
        });
    }
}
