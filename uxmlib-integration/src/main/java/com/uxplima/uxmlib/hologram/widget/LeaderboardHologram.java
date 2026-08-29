package com.uxplima.uxmlib.hologram.widget;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

import com.uxplima.uxmlib.hologram.Hologram;
import com.uxplima.uxmlib.hologram.leaderboard.LeaderboardRenderer;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.jspecify.annotations.Nullable;

/**
 * A live leaderboard: it wraps a {@link Hologram} and the pure {@link LeaderboardRenderer} and keeps the
 * hologram's text refreshed on a {@link Scheduler} timer from a {@code Supplier<Map<UUID,Double>>}. Each tick
 * pulls the latest scores, renders them to lines, joins them with newlines (one multi-line {@code TextDisplay}
 * renders), and writes that back onto the hologram on the entity's own region thread: the missing "render →
 * live entity → timed refresh" glue over the renderer we already had.
 *
 * <p>An optional nearby gate ({@link BooleanSupplier}) is checked first each tick so an area with no players
 * costs nothing: when it returns {@code false} the tick skips the data pull and the re-render entirely. A
 * consumer supplies a cheap O(players) "any player within range?" check; the widget never reads positions
 * itself, keeping it unit-testable.
 */
public final class LeaderboardHologram {

    private final Hologram hologram;
    private final LeaderboardRenderer renderer;
    private final Supplier<Map<UUID, Double>> dataSource;
    private final Scheduler scheduler;
    private final Duration period;
    private final BooleanSupplier nearbyGate;

    private @Nullable TaskHandle task;

    /** A leaderboard that always re-renders on the timer (no nearby gate). */
    public LeaderboardHologram(
            Hologram hologram,
            LeaderboardRenderer renderer,
            Supplier<Map<UUID, Double>> dataSource,
            Scheduler scheduler,
            Duration period) {
        this(hologram, renderer, dataSource, scheduler, period, () -> true);
    }

    /** A leaderboard gated by {@code nearbyGate}: a tick re-renders only when the gate is open. */
    public LeaderboardHologram(
            Hologram hologram,
            LeaderboardRenderer renderer,
            Supplier<Map<UUID, Double>> dataSource,
            Scheduler scheduler,
            Duration period,
            BooleanSupplier nearbyGate) {
        this.hologram = Objects.requireNonNull(hologram, "hologram");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.period = Objects.requireNonNull(period, "period");
        this.nearbyGate = Objects.requireNonNull(nearbyGate, "nearbyGate");
    }

    /**
     * Start the refresh timer (first refresh after one {@code period}). Idempotent: a second call while the
     * timer is live does nothing, so it cannot spawn a second timer.
     */
    public void start() {
        if (isRunning()) {
            return;
        }
        task = scheduler.globalTimer(period, period, handle -> refresh());
    }

    /** Stop the refresh timer. A no-op if it was not running. */
    public void stop() {
        TaskHandle current = task;
        if (current != null) {
            current.cancel();
            task = null;
        }
    }

    /** Whether the refresh timer is live. */
    public boolean isRunning() {
        TaskHandle current = task;
        return current != null && !current.isCancelled();
    }

    /**
     * One refresh pass: if the nearby gate is open, render the latest scores and write them onto the hologram
     * on its entity region thread. Package-private so a test can fire a single pass; the timer calls this.
     */
    void refresh() {
        if (!nearbyGate.getAsBoolean()) {
            return;
        }
        Component text = render();
        scheduler.entity(hologram.entity(), () -> hologram.setText(text));
    }

    private Component render() {
        List<Component> lines = renderer.render(dataSource.get());
        return Component.join(JoinConfiguration.newlines(), lines);
    }
}
