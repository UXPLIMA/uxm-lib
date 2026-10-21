package com.uxplima.uxmlib.packet.display;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * A plugin asks for the server's hologram packets without naming the internals, and a server that does not have them
 * gets packets that send nothing, so a hologram costs its viewers the text and never costs a plugin its enable.
 */
class ServerHologramPacketsTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("HologramHost");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a server without the internals gets holograms that send nothing")
    void aServerWithoutTheInternalsGetsHologramsThatSendNothing() {
        HologramPackets none = ServerHologramPackets.orNone(plugin, () -> {
            throw new NoClassDefFoundError("net/minecraft/world/entity/Display$TextDisplay");
        });
        World world = server.addSimpleWorld("world");
        Player alice = server.addPlayer("Alice");
        alice.teleport(new Location(world, 1, 64, 1));
        FakeRegionScheduler scheduler = new FakeRegionScheduler();

        assertThat(none.allocateEntityId()).isNotEqualTo(none.allocateEntityId());
        PacketHologram hologram = PacketHologram.show(
                none,
                scheduler,
                new Location(world, 0, 64, 0),
                HologramAppearance.defaults(),
                viewer -> List.of(Component.text("shop")));
        assertThatCode(scheduler::firstFrame).doesNotThrowAnyException();
        assertThatCode(hologram::remove).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a server with the internals gets the packets it built")
    void aServerWithTheInternalsGetsItsOwnPackets() {
        FakeHologramPackets real = new FakeHologramPackets();

        assertThat(ServerHologramPackets.orNone(plugin, () -> real)).isSameAs(real);
    }
}
