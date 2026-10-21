package com.uxplima.uxmlib.packet.display;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;

import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * A click on a packet hologram's box is an entity the server never held, so it arrives as an unknown entity. The
 * router answers it with the hologram that owns the id, once per click, on the clicking player's own thread.
 */
class PacketHologramClicksTest {

    private ServerMock server;
    private Player alice;
    private PacketHologram hologram;
    private int boxId;
    private final EntityHops hops = new EntityHops();
    private final List<String> clicks = new ArrayList<>();
    private PacketHologramClicks router;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        World world = server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        alice.teleport(new Location(world, 1, 64, 1));
        FakeHologramPackets packets = new FakeHologramPackets();
        FakeRegionScheduler frames = new FakeRegionScheduler();
        hologram = PacketHologram.show(
                packets,
                frames,
                new Location(world, 0, 64, 0),
                HologramAppearance.defaults(),
                viewer -> List.of(Component.text("join")));
        hologram.setClickBox(1.0f, 1.0f);
        frames.firstFrame();
        boxId = packets.sends.stream()
                .map(FakeHologramPackets.Sent::packet)
                .flatMap(packet -> ((FakeHologramPackets.Bundle) packet).packets().stream())
                .filter(packet -> packet instanceof FakeHologramPackets.InteractionSize)
                .map(packet -> ((FakeHologramPackets.InteractionSize) packet).entityId())
                .findFirst()
                .orElseThrow();
        router = new PacketHologramClicks(hops);
        router.register(hologram, (player, attack) -> clicks.add(player.getName() + (attack ? " left" : " right")));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aClickOnTheBoxReachesTheHandler() {
        router.onUseUnknownEntity(click(boxId, false, EquipmentSlot.HAND));
        hops.runAll();

        assertThat(clicks).containsExactly("Alice right");
    }

    @Test
    void aClickOnAnotherIdReachesNothing() {
        router.onUseUnknownEntity(click(boxId + 1000, false, EquipmentSlot.HAND));
        hops.runAll();

        assertThat(clicks).isEmpty();
    }

    @Test
    void theHandlerRunsOnThePlayersThread() {
        router.onUseUnknownEntity(click(boxId, true, EquipmentSlot.HAND));

        assertThat(clicks).isEmpty();
        assertThat(hops.entities).containsExactly(alice);
        hops.runAll();
        assertThat(clicks).containsExactly("Alice left");
    }

    @Test
    void oneClickFiresOnce() {
        router.onUseUnknownEntity(click(boxId, false, EquipmentSlot.HAND));
        router.onUseUnknownEntity(click(boxId, false, EquipmentSlot.OFF_HAND));
        hops.runAll();

        assertThat(clicks).containsExactly("Alice right");
    }

    @Test
    void anUnregisteredHologramIsNotAnswered() {
        router.unregister(hologram);
        router.onUseUnknownEntity(click(boxId, false, EquipmentSlot.HAND));
        hops.runAll();

        assertThat(clicks).isEmpty();
    }

    private PlayerUseUnknownEntityEvent click(int entityId, boolean attack, EquipmentSlot hand) {
        return new PlayerUseUnknownEntityEvent(alice, entityId, attack, hand, new Vector());
    }

    /** Records each hop onto an entity's thread and runs them only when asked, so a test sees the hop itself. */
    private static final class EntityHops implements Scheduler {

        final List<Entity> entities = new ArrayList<>();
        private final List<Runnable> tasks = new ArrayList<>();

        void runAll() {
            tasks.forEach(Runnable::run);
            tasks.clear();
        }

        @Override
        public TaskHandle entity(Entity entity, Runnable task) {
            entities.add(entity);
            tasks.add(task);
            return done();
        }

        private static TaskHandle done() {
            return new TaskHandle() {
                @Override
                public void cancel() {}

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
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
