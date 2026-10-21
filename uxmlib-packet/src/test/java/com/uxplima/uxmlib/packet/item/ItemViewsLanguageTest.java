package com.uxplima.uxmlib.packet.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

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
 * An item is drawn in the viewer's language on its way to the client, so a viewer who picks another language
 * must be sent their items again: nothing on the server changed, and without a resend the client keeps the old
 * words until the slot happens to change.
 */
class ItemViewsLanguageTest {

    private ServerMock server;
    private Plugin plugin;
    private final Threads scheduler = new Threads();
    private final Map<UUID, String> languages = new HashMap<>();
    private ItemViews views;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("ItemViewsHost");
        views = new ItemViews(plugin, scheduler, new FakeItemPackets(), (viewer, real) -> real)
                .followLanguage(player -> languages.get(player.getUniqueId()), Duration.ofSeconds(1));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aLanguageChangeResendsTheInventory() {
        views.install();
        Resends alice = join("Alice", "en");
        scheduler.tick();

        languages.put(alice.getUniqueId(), "tr");
        scheduler.tick();

        assertThat(alice.resends).isEqualTo(1);
        assertThat(views.languageOf(alice.getUniqueId())).contains("tr");
    }

    @Test
    void anUnchangedLanguageResendsNothing() {
        views.install();
        Resends alice = join("Alice", "en");
        scheduler.tick();
        scheduler.tick();

        assertThat(alice.resends).isZero();
    }

    @Test
    void theSnapshotIsReadableOffThread() throws Exception {
        views.install();
        Resends alice = join("Alice", "en");

        Optional<String> read = CompletableFuture.supplyAsync(() -> views.languageOf(alice.getUniqueId()))
                .get();

        assertThat(read).contains("en");
    }

    @Test
    void aPlayerAlreadyOnlineAtInstallHasASnapshot() {
        Resends alice = new Resends(server, "Alice");
        languages.put(alice.getUniqueId(), "en");
        server.addPlayer(alice);

        views.install();

        assertThat(views.languageOf(alice.getUniqueId())).contains("en");
    }

    @Test
    void aQuitForgetsTheSnapshot() {
        views.install();
        Resends alice = join("Alice", "en");

        alice.disconnect();

        assertThat(views.languageOf(alice.getUniqueId())).isEmpty();
    }

    @Test
    void uninstallStopsTheWatch() {
        views.install();
        Resends alice = join("Alice", "en");

        views.uninstall();

        assertThat(scheduler.watchCancelled).isTrue();
        assertThat(views.languageOf(alice.getUniqueId())).isEmpty();
    }

    private Resends join(String name, String language) {
        Resends player = new Resends(server, name);
        languages.put(player.getUniqueId(), language);
        server.addPlayer(player);
        return player;
    }

    /** A player that counts how often its inventory was sent again. */
    private static final class Resends extends PlayerMock {

        int resends;

        Resends(ServerMock server, String name) {
            super(server, name, UUID.randomUUID());
        }

        @Override
        public void updateInventory() {
            resends++;
        }
    }

    /** Runs a hop onto a player's thread at once and holds the global timer for {@link #tick()}. */
    private static final class Threads implements Scheduler {

        private @Nullable Consumer<TaskHandle> timer;
        boolean watchCancelled;

        private final TaskHandle handle = new TaskHandle() {
            @Override
            public void cancel() {
                watchCancelled = true;
            }

            @Override
            public boolean isCancelled() {
                return watchCancelled;
            }
        };

        void tick() {
            Consumer<TaskHandle> task = timer;
            if (task != null) {
                task.accept(handle);
            }
        }

        @Override
        public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            timer = task;
            return handle;
        }

        @Override
        public TaskHandle entity(Entity entity, Runnable task) {
            task.run();
            return handle;
        }

        @Override
        public TaskHandle entityLater(Entity entity, Duration delay, Runnable task) {
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
