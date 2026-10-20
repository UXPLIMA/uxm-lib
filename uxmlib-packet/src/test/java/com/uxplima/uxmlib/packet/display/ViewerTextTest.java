package com.uxplima.uxmlib.packet.display;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.junit.jupiter.api.Test;

/**
 * One shared display, and each viewer reads it in their own words. The server starts tracking an entity as it is
 * added to the world, and a copy sent at that moment reaches a client that has no entity yet and is dropped, so
 * the viewer's text goes out after the client has the entity, and again whenever the caller asks.
 */
class ViewerTextTest {

    private record Sent(Player viewer, int entityId, Component text) {}

    private final List<Sent> sent = new ArrayList<>();
    private final List<Runnable> later = new ArrayList<>();
    private final List<Duration> delays = new ArrayList<>();

    private final DisplayTextPackets packets = new DisplayTextPackets() {
        @Override
        public Object textOverride(int entityId, Component text) {
            return new Object[] {entityId, text};
        }

        @Override
        public void send(Player viewer, Object packet) {
            Object[] parts = (Object[]) packet;
            sent.add(new Sent(viewer, (Integer) parts[0], (Component) parts[1]));
        }
    };

    private static final TaskHandle DONE = new TaskHandle() {
        @Override
        public void cancel() {}

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    private final ViewerText viewerText = new ViewerText(recordingScheduler(), packets);

    private Scheduler recordingScheduler() {
        return new Scheduler() {
            @Override
            public TaskHandle entityLater(Entity entity, Duration delay, Runnable task) {
                delays.add(delay);
                later.add(task);
                return DONE;
            }

            @Override
            public TaskHandle entity(Entity entity, Runnable task) {
                task.run();
                return DONE;
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
            public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
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
            public TaskHandle regionTimer(
                    Location location, Duration delay, Duration period, Consumer<TaskHandle> task) {
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
        };
    }

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    private static TextDisplay display(int id, Set<Player> trackedBy) {
        TextDisplay display = mock(TextDisplay.class);
        when(display.getEntityId()).thenReturn(id);
        when(display.getTrackedBy()).thenReturn(trackedBy);
        when(display.isValid()).thenReturn(true);
        return display;
    }

    @Test
    void aViewerWhoStartsTrackingIsSentTheirTextOnceTheClientHasTheEntity() {
        Player ayse = player("Ayse");
        TextDisplay display = display(7, Set.of());
        viewerText.bind(display, viewer -> Component.text("Merhaba " + viewer.getName()));

        viewerText.onTrack(new PlayerTrackEntityEvent(ayse, display));

        assertThat(sent).isEmpty();
        assertThat(delays).singleElement().satisfies(delay -> assertThat(delay).isPositive());
        later.forEach(Runnable::run);
        assertThat(sent).containsExactly(new Sent(ayse, 7, Component.text("Merhaba Ayse")));
    }

    @Test
    void anUnboundEntityIsLeftAlone() {
        viewerText.onTrack(new PlayerTrackEntityEvent(player("Alex"), display(9, Set.of())));

        assertThat(later).isEmpty();
        assertThat(sent).isEmpty();
    }

    @Test
    void aDisplayUnboundBeforeTheDelayEndsSendsNothing() {
        Player alex = player("Alex");
        TextDisplay display = display(7, Set.of());
        viewerText.bind(display, viewer -> Component.text("x"));
        viewerText.onTrack(new PlayerTrackEntityEvent(alex, display));

        viewerText.unbind(display);
        later.forEach(Runnable::run);

        assertThat(sent).isEmpty();
    }

    /** A viewer who changed language, or a line that changed, is redrawn for whoever already sees it. */
    @Test
    void refreshSendsEveryBoundDisplayTheViewerAlreadyTracks() {
        Player ayse = player("Ayse");
        TextDisplay seen = display(7, Set.of(ayse));
        TextDisplay elsewhere = display(8, Set.of());
        viewerText.bind(seen, viewer -> Component.text("bir"));
        viewerText.bind(elsewhere, viewer -> Component.text("iki"));
        sent.clear();

        viewerText.refresh(ayse);

        assertThat(sent).containsExactly(new Sent(ayse, 7, Component.text("bir")));
    }

    @Test
    void bindingADisplayAlreadyInViewSendsItsTrackersTheirTextAtOnce() {
        Player ayse = player("Ayse");

        viewerText.bind(display(7, Set.of(ayse)), viewer -> Component.text("Merhaba"));

        assertThat(sent).containsExactly(new Sent(ayse, 7, Component.text("Merhaba")));
    }

    /** A server with no internals to build packets from, a test server for one, binds and tracks without failing. */
    @Test
    void theLiveSenderBuildsNothingWhereTheServerHasNoInternals() {
        Player ayse = player("Ayse");
        TextDisplay display = display(7, Set.of(ayse));
        ViewerText live = ViewerText.live(recordingScheduler());

        live.bind(display, viewer -> Component.text("Merhaba"));
        live.onTrack(new PlayerTrackEntityEvent(ayse, display));
        later.forEach(Runnable::run);
        live.refresh(ayse);

        assertThat(later).hasSize(1);
    }
}
