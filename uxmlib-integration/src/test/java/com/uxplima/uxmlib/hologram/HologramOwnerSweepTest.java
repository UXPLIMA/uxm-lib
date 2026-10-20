package com.uxplima.uxmlib.hologram;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Hologram entities persist, so every plugin that spawns them finds its own leftovers at the next start, and several
 * plugins share one world and one marker. A sweep removes only what this plugin left: a server with two of them
 * loses nothing to the other, and gathers one more copy of each of its own on every restart until it sweeps.
 */
class HologramOwnerSweepTest {

    private ServerMock server;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Entity marked(String owner) {
        ArmorStand entity = world.spawn(new Location(world, 0, 64, 0), ArmorStand.class);
        Markers.stamp(entity);
        if (!owner.isEmpty()) {
            Markers.own(entity, owner);
        }
        return entity;
    }

    @Test
    void aLeftoverOfThisPluginIsAnOrphan() {
        assertThat(HologramManager.isOrphanOf(marked("PluginA"), "PluginA", Set.of()))
                .isTrue();
    }

    @Test
    void anotherPluginsHologramIsNot() {
        assertThat(HologramManager.isOrphanOf(marked("PluginB"), "PluginA", Set.of()))
                .isFalse();
        assertThat(HologramManager.isOrphanOf(marked(""), "PluginA", Set.of())).isFalse();
    }

    @Test
    void aLiveHologramIsNot() {
        Entity live = marked("PluginA");

        assertThat(HologramManager.isOrphanOf(live, "PluginA", Set.of(live.getUniqueId())))
                .isFalse();
    }

    @Test
    void anUnmarkedEntityIsNever() {
        ArmorStand plain = world.spawn(new Location(world, 0, 64, 0), ArmorStand.class);

        assertThat(HologramManager.isOrphanOf(plain, "", Set.of())).isFalse();
    }

    /** A manager with no owner keeps the old reach, leftovers with no owner, and never another plugin's. */
    @Test
    void anOwnerlessManagerSweepsOnlyOwnerlessLeftovers() {
        assertThat(HologramManager.isOrphanOf(marked(""), "", Set.of())).isTrue();
        assertThat(HologramManager.isOrphanOf(marked("PluginA"), "", Set.of())).isFalse();
    }

    /**
     * A chunk that loads a leftover of this plugin loses it there and then, on the chunk's own thread, so a chunk
     * nobody stood in at startup is swept the first time somebody walks into it. Another plugin's is kept.
     */
    @Test
    void aLeftoverIsRemovedAsItsChunkLoads() {
        HologramManager manager = new HologramManager("PluginA");
        Entity mine = marked("PluginA");
        Entity theirs = marked("PluginB");

        manager.sweepIfLeftover(java.util.List.of(mine, theirs));

        assertThat(mine.isValid()).isFalse();
        assertThat(theirs.isValid()).isTrue();
    }

    @Test
    void aManagerWithNoOwnerCannotSweepOnLoad() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new HologramManager().sweepLeftoversAsTheyLoad(MockBukkit.createMockPlugin()))
                .isInstanceOf(IllegalStateException.class);
    }
}
