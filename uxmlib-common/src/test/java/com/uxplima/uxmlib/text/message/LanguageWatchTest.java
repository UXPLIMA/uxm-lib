package com.uxplima.uxmlib.text.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Nothing tells every plugin on a server that a player chose a language, so the language is asked again on a timer
 * and a change is reported once, for the caller to redraw what that player already holds or sees.
 */
class LanguageWatchTest {

    private final FakeScheduler scheduler = new FakeScheduler();
    private final Map<UUID, String> language = new HashMap<>();
    private final List<Player> changed = new ArrayList<>();
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private TaskHandle watch(List<Player> online) {
        return LanguageWatch.follow(
                scheduler,
                viewer -> language.get(viewer.getUniqueId()),
                Duration.ofSeconds(1),
                () -> online,
                changed::add);
    }

    @Test
    void aChangeIsReportedOnceAndTheFirstAnswerIsNot() {
        PlayerMock ayse = server.addPlayer("Ayse");
        language.put(ayse.getUniqueId(), "en");
        watch(List.of(ayse));

        scheduler.tick();
        assertThat(changed).isEmpty();

        language.put(ayse.getUniqueId(), "tr");
        scheduler.tick();
        assertThat(changed).containsExactly(ayse);

        scheduler.tick();
        assertThat(changed).containsExactly(ayse);
    }

    @Test
    void aPlayerWithNoAnswerYetIsNotReported() {
        PlayerMock ayse = server.addPlayer("Ayse");
        watch(List.of(ayse));

        scheduler.tick();
        language.put(ayse.getUniqueId(), "tr");
        scheduler.tick();

        assertThat(changed).isEmpty();
    }

    @Test
    void aPlayerWhoLeftStartsOverWhenTheyComeBack() {
        PlayerMock ayse = server.addPlayer("Ayse");
        List<Player> online = new ArrayList<>(List.of(ayse));
        language.put(ayse.getUniqueId(), "en");
        watch(online);
        scheduler.tick();

        online.clear();
        scheduler.tick();
        language.put(ayse.getUniqueId(), "tr");
        online.add(ayse);
        scheduler.tick();

        assertThat(changed).isEmpty();
    }

    @Test
    void theTimerIsHandedBackForTheCallerToCancel() {
        TaskHandle handle = watch(List.of());

        handle.cancel();

        assertThat(scheduler.cancelled).isTrue();
    }

    /** Runs a player's task at once and keeps the global timer for the test to fire. */
    private static final class FakeScheduler implements Scheduler {
        private @Nullable Consumer<TaskHandle> timer;
        private boolean cancelled;

        private final TaskHandle handle = new TaskHandle() {
            @Override
            public void cancel() {
                cancelled = true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        };

        void tick() {
            if (timer != null && !cancelled) {
                timer.accept(handle);
            }
        }

        @Override
        public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            this.timer = task;
            return handle;
        }

        @Override
        public TaskHandle entity(Entity entity, Runnable task) {
            task.run();
            return handle;
        }

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
