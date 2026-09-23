package com.uxplima.uxmlib.particle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * A particle an operator names in a file is read with the data the server needs for it.
 *
 * <p>{@link ParticleOptions#of} takes only a particle that needs no data and throws for the rest, and a server throws
 * for any of them drawn without data: on 2026-09-23 a probe on a Folia 26.2 server drew all twenty two with none and
 * every one refused with "missing required data class". A plugin that reads a particle name from its configuration
 * and hands it to either one fails the moment an operator writes {@code dust}. {@link ParticleOptions#read} takes the
 * word written beside the name: a colour, two colours for a transition, a block or an item, or a number, and a blank
 * word draws with a default.
 */
class AWrittenParticleCarriesItsDataTest {

    private final Location at = new Location(null, 0.5, 64, 0.5);

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("every particle read with nothing written carries data of the class the server asks for")
    void everyParticleCarriesItsData() {
        List<String> wrong = new ArrayList<>();
        for (Particle particle : Particle.values()) {
            Optional<ParticleOptions> read = ParticleOptions.read(particle, "", at);
            if (read.isEmpty()) {
                wrong.add(particle.name() + " could not be read");
                continue;
            }
            Object data = read.get().data();
            boolean fits = particle.getDataType() == Void.class
                    ? data == null
                    : particle.getDataType().isInstance(data);
            if (!fits || read.get().particle() != particle) {
                wrong.add(particle.name() + " carried " + data);
            }
        }

        assertThat(wrong).describedAs("the server refuses each of these").isEmpty();
    }

    @Test
    @DisplayName("a dust takes its colour, a transition both of its colours, a block its block")
    void theWordIsRead() {
        Particle.DustOptions dust = (Particle.DustOptions)
                ParticleOptions.read(Particle.DUST, "#ff0000", at).orElseThrow().data();
        Particle.DustTransition fade =
                (Particle.DustTransition) ParticleOptions.read(Particle.DUST_COLOR_TRANSITION, "#ff0000>#0000ff", at)
                        .orElseThrow()
                        .data();
        BlockData block = (BlockData) ParticleOptions.read(Particle.BLOCK, "oak_log", at)
                .orElseThrow()
                .data();

        assertThat(java.util.Objects.requireNonNull(dust).getColor()).isEqualTo(Color.fromRGB(0xff0000));
        assertThat(java.util.Objects.requireNonNull(fade).getToColor()).isEqualTo(Color.fromRGB(0x0000ff));
        assertThat(java.util.Objects.requireNonNull(block).getMaterial()).isEqualTo(Material.OAK_LOG);
    }

    @Test
    @DisplayName("a word that cannot be read is no data, and nothing is drawn")
    void anUnreadableWordReadsAsNothing() {
        assertThat(ParticleOptions.read(Particle.DUST, "notacolour", at)).isEmpty();
        assertThat(ParticleOptions.read(Particle.BLOCK, "diamond_sword", at)).isEmpty();
        assertThat(ParticleOptions.read(Particle.SHRIEK, "soon", at)).isEmpty();
    }

    @Test
    @DisplayName("a particle that needs no data ignores the word and draws plain")
    void aPlainParticleIsPlain() {
        assertThat(ParticleOptions.read(Particle.HEART, "#ff0000", at)).contains(ParticleOptions.of(Particle.HEART));
    }

    @Test
    @DisplayName("a name is read the way an operator writes it")
    void aNameIsReadAsWritten() {
        assertThat(ParticleOptions.named("happy_villager")).contains(Particle.HAPPY_VILLAGER);
        assertThat(ParticleOptions.named(" Dust ")).contains(Particle.DUST);
        assertThat(ParticleOptions.named("sparkles")).isEmpty();
    }

    /**
     * A setting that names a particle may carry its data after the name, so {@code hit-particle = "dust #ff0000"} is
     * one value an operator writes in one place.
     */
    @Test
    @DisplayName("a name and its data written as one value are read as one particle")
    void aNameAndItsDataAreOneValue() {
        ParticleOptions dust = ParticleOptions.parse("dust #ff0000", at).orElseThrow();
        ParticleOptions heart = ParticleOptions.parse(" HEART ", at).orElseThrow();

        assertThat(dust.particle()).isEqualTo(Particle.DUST);
        assertThat(((Particle.DustOptions) java.util.Objects.requireNonNull(dust.data())).getColor())
                .isEqualTo(Color.fromRGB(0xff0000));
        assertThat(heart).isEqualTo(ParticleOptions.of(Particle.HEART));
        assertThat(ParticleOptions.parse("sparkles", at)).isEmpty();
        assertThat(ParticleOptions.parse("dust notacolour", at)).isEmpty();
        assertThat(ParticleOptions.parse("", at)).isEmpty();
    }
}
