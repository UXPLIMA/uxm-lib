package com.uxplima.uxmlib.packet.display;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The probe answers what a viewer's client holds, from the packets it was sent and nothing else, so a test of
 * a change can compare that against what the viewer should see now.
 */
class HologramProbeTest {

    private ServerMock server;
    private World world;
    private FakeHologramPackets packets;
    private FakeRegionScheduler scheduler;
    private HologramProbe probe;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        packets = new FakeHologramPackets();
        scheduler = new FakeRegionScheduler();
        probe = new HologramProbe(packets);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aViewerHoldsWhatTheyWereLastSent() {
        Player alice = playerAt(1, 64, 1);
        PacketHologram hologram = PacketHologram.show(
                packets,
                scheduler,
                new Location(world, 0, 64, 0),
                HologramAppearance.defaults(),
                viewer -> List.of(Component.text("top"), Component.text("bottom")));
        scheduler.firstFrame();

        hologram.setText(viewer -> List.of(Component.text("new top"), Component.text("bottom")));
        scheduler.tick();

        assertThat(probe.heldBy(alice)).containsExactly(Component.text("new top"), Component.text("bottom"));
    }

    @Test
    void aViewerWhoWalkedAwayHoldsNothing() {
        PlayerMock alice = playerAt(1, 64, 1);
        PacketHologram.show(
                packets,
                scheduler,
                new Location(world, 0, 64, 0),
                HologramAppearance.defaults(),
                viewer -> List.of(Component.text("shop")));
        scheduler.firstFrame();

        alice.setLocation(new Location(world, 500, 64, 500));
        scheduler.tick();

        assertThat(probe.heldBy(alice)).isEmpty();
    }

    private PlayerMock playerAt(double x, double y, double z) {
        PlayerMock player = server.addPlayer("Alice");
        player.setLocation(new Location(world, x, y, z));
        return player;
    }
}
