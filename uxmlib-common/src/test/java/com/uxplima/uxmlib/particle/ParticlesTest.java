package com.uxplima.uxmlib.particle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.util.SpawnedParticle;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Smoke-tests the spawn facade against MockBukkit's recording world: the particle, count and data each
 * options value carries must reach {@code spawnParticle} unchanged, confirming {@link Particles} forwards to
 * the data-carrying overload.
 */
class ParticlesTest {

    private ServerMock server;
    private WorldMock world;
    private Location where;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("test");
        where = new Location(world, 1, 64, 1);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void worldSpawnForwardsParticleCountAndDustData() {
        Particles.spawn(world, where, ParticleOptions.dust(Color.RED, 1.5f), 10);

        assertThat(world.getSpawnedParticles()).singleElement().satisfies(spawned -> {
            assertThat(spawned.particle()).isEqualTo(Particle.DUST);
            assertThat(spawned.count()).isEqualTo(10);
            assertThat(spawned.data()).isInstanceOf(Particle.DustOptions.class);
            assertThat(((Particle.DustOptions) Objects.requireNonNull(spawned.data())).getColor())
                    .isEqualTo(Color.RED);
        });
    }

    @Test
    void worldSpawnForwardsNullDataForAPlainParticle() {
        Particles.spawn(world, where, ParticleOptions.of(Particle.FLAME), 3);

        SpawnedParticle spawned = world.getSpawnedParticles().get(0);
        assertThat(spawned.particle()).isEqualTo(Particle.FLAME);
        assertThat(spawned.count()).isEqualTo(3);
        assertThat(spawned.data()).isNull();
    }

    @Test
    void worldSpawnForwardsOffsetsAndSpeed() {
        Particles.spawn(world, where, ParticleOptions.of(Particle.FLAME), 1, 0.5, 0.25, 0.1, 0.2);

        SpawnedParticle spawned = world.getSpawnedParticles().get(0);
        assertThat(spawned.offsetX()).isEqualTo(0.5);
        assertThat(spawned.offsetY()).isEqualTo(0.25);
        assertThat(spawned.offsetZ()).isEqualTo(0.1);
        // The speed a caller passes is one number, and the server spreads it over all three axes.
        assertThat(spawned.speedX()).isEqualTo(0.2);
        assertThat(spawned.speedY()).isEqualTo(0.2);
        assertThat(spawned.speedZ()).isEqualTo(0.2);
    }

    @Test
    void playerSpawnIsScopedToThatPlayer() {
        PlayerMock player = server.addPlayer();

        Particles.spawn(player, where, ParticleOptions.of(Particle.HEART), 2);

        // A player-scoped spawn reaches that player and nobody else.
        assertThat(world.getSpawnedParticles()).singleElement().satisfies(spawned -> {
            assertThat(spawned.particle()).isEqualTo(Particle.HEART);
            assertThat(spawned.count()).isEqualTo(2);
            assertThat(spawned.receivers()).containsExactly(player);
        });
    }

    @Test
    void negativeCountIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Particles.spawn(world, where, ParticleOptions.of(Particle.FLAME), -1));
    }
}
