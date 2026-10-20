package com.uxplima.uxmlib.hologram.widget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.hologram.Hologram;
import com.uxplima.uxmlib.hologram.Transform;
import com.uxplima.uxmlib.hologram.leaderboard.LeaderboardOptions;
import com.uxplima.uxmlib.hologram.leaderboard.LeaderboardRenderer;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.Test;

/**
 * Ties the pure {@link LeaderboardRenderer} to a fake hologram and a captured timer: each tick pulls fresh
 * scores from the supplier, renders them, and writes the joined lines onto the hologram. Re-rendering on the
 * timer and the nearby-gate short-circuit are the behaviours under test; no live world or scheduler is used.
 */
class LeaderboardHologramTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    private static String name(UUID id) {
        return id.equals(A) ? "Alice" : "Bob";
    }

    private static LeaderboardRenderer renderer() {
        return new LeaderboardRenderer(
                LeaderboardOptions.topN(2).format("{place}. {name} {score}"), LeaderboardHologramTest::name);
    }

    private static Map<UUID, Double> scores(double alice, double bob) {
        Map<UUID, Double> scores = new LinkedHashMap<>();
        scores.put(A, alice);
        scores.put(B, bob);
        return scores;
    }

    @Test
    void eachTickRendersTheLatestScoresOntoTheHologram() {
        AtomicReference<Map<UUID, Double>> data = new AtomicReference<>(scores(10, 30));
        FakeScheduler scheduler = new FakeScheduler();
        CapturingHologram holo = new CapturingHologram();
        LeaderboardHologram board =
                new LeaderboardHologram(holo, renderer(), data::get, scheduler, Duration.ofSeconds(1));

        board.start();
        scheduler.tick();
        assertThat(Text.plain(holo.lastText)).contains("1. Bob 30").contains("2. Alice 10");

        // New data on the next tick re-renders.
        data.set(scores(99, 1));
        scheduler.tick();
        assertThat(Text.plain(holo.lastText)).contains("1. Alice 99").contains("2. Bob 1");
    }

    /** A board with a title keeps it: each refresh writes the title and then the rows, not the rows alone. */
    @Test
    void aHeaderSurvivesEveryRefreshAndTheDrawIsReported() {
        FakeScheduler scheduler = new FakeScheduler();
        CapturingHologram holo = new CapturingHologram();
        AtomicReference<Component> drawnRows = new AtomicReference<>(Component.empty());
        LeaderboardHologram board = new LeaderboardHologram(
                        holo, renderer(), () -> scores(10, 30), scheduler, Duration.ofSeconds(1))
                .headedBy(() -> Component.text("Top builders"))
                .afterEachDraw(drawnRows::set);

        board.start();
        scheduler.tick();

        assertThat(Text.plain(holo.lastText)).startsWith("Top builders\n1. Bob 30");
        assertThat(Text.plain(java.util.Objects.requireNonNull(drawnRows.get())))
                .startsWith("1. Bob 30")
                .doesNotContain("Top builders");
    }

    @Test
    void startReturnsAndStopCancelsTheTimer() {
        FakeScheduler scheduler = new FakeScheduler();
        LeaderboardHologram board = new LeaderboardHologram(
                new CapturingHologram(), renderer(), () -> scores(1, 2), scheduler, Duration.ofSeconds(1));

        assertThat(board.isRunning()).isFalse();
        board.start();
        assertThat(board.isRunning()).isTrue();
        board.stop();
        assertThat(board.isRunning()).isFalse();
    }

    @Test
    void startIsIdempotent() {
        FakeScheduler scheduler = new FakeScheduler();
        LeaderboardHologram board = new LeaderboardHologram(
                new CapturingHologram(), renderer(), () -> scores(1, 2), scheduler, Duration.ofSeconds(1));

        board.start();
        TaskHandle first = scheduler.lastHandle;
        board.start(); // second start must not spin up a second timer
        assertThat(scheduler.timerInstalls).isEqualTo(1);
        assertThat(scheduler.lastHandle).isSameAs(first);
    }

    @Test
    void theNearbyGateSkipsReRenderWhenNobodyIsNearby() {
        AtomicReference<Boolean> anyNearby = new AtomicReference<>(false);
        FakeScheduler scheduler = new FakeScheduler();
        CapturingHologram holo = new CapturingHologram();
        LeaderboardHologram board = new LeaderboardHologram(
                holo, renderer(), () -> scores(10, 30), scheduler, Duration.ofSeconds(1), anyNearby::get);

        board.start();
        scheduler.tick();
        assertThat(holo.writes).isZero(); // gate closed: no render

        anyNearby.set(true);
        scheduler.tick();
        assertThat(holo.writes).isEqualTo(1); // gate open: rendered
    }

    /** A hologram that records the text last written to it; the scheduler runs the write inline. */
    private static final class CapturingHologram implements Hologram {
        private final TextDisplay display = mock(TextDisplay.class);
        private Component lastText = Component.empty();
        private int writes;

        @Override
        public void setText(Component text) {
            lastText = text;
            writes++;
        }

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
        public void forgetViewer(UUID viewer) {}

        @Override
        public void remove() {}

        @Override
        public TextDisplay entity() {
            return display;
        }
    }

    /** A scheduler that captures the global timer and runs entity tasks inline so a tick can be fired. */
    private static final class FakeScheduler implements Scheduler {
        private @org.jspecify.annotations.Nullable Consumer<TaskHandle> timer;
        private int timerInstalls;
        private boolean cancelled;
        private TaskHandle lastHandle = noHandle();

        private TaskHandle noHandle() {
            return new TaskHandle() {
                @Override
                public void cancel() {
                    cancelled = true;
                }

                @Override
                public boolean isCancelled() {
                    return cancelled;
                }
            };
        }

        @Override
        public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            this.timer = task;
            this.cancelled = false;
            this.timerInstalls++;
            this.lastHandle = noHandle();
            return lastHandle;
        }

        @Override
        public TaskHandle entity(Entity entity, Runnable task) {
            task.run(); // run the write inline so the test can observe it
            return lastHandle;
        }

        void tick() {
            if (timer != null) {
                timer.accept(lastHandle);
            }
        }

        // Unused members.
        @Override
        public TaskHandle global(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle globalLater(Duration delay, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle region(Location location, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle regionLater(Location location, Duration delay, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle regionTimer(Location location, Duration delay, Duration period, Consumer<TaskHandle> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle entityLater(Entity entity, Duration delay, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle entityTimer(Entity entity, Duration delay, Duration period, Consumer<TaskHandle> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle async(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle asyncLater(Duration delay, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            throw new UnsupportedOperationException();
        }
    }
}
