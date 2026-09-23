package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import com.uxplima.uxmlib.condition.OperandResolver;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;

/**
 * A particle line draws the particle it names, the ones that need data included.
 *
 * <p>A dust, a block, an item, a coloured spell and eighteen more cannot be drawn without data, and the server
 * refuses them with "missing required data class". On 2026-09-23 a probe on a Folia 26.2 server drew all
 * twenty two of them with none and every one threw. {@code [particle] dust 10 0.3} is the line an operator writes
 * first for a coloured puff, and it did nothing but throw. A line may now end in the data, a colour, a block or an
 * item, or a number, and a line without it draws with a default.
 */
class EveryParticleGetsTheDataItNeedsTest {

    private World world;

    private Player player;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        world = mock(World.class);
        player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0.5, 64, 0.5));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("every particle that needs data is drawn with data of the class the server asks for")
    void everyDataParticleGetsItsData() {
        List<String> wrong = new ArrayList<>();
        for (Particle particle : Particle.values()) {
            if (particle.getDataType() == Void.class) {
                continue;
            }
            Object data = drawn("[particle] " + particle.name().toLowerCase(java.util.Locale.ROOT) + " 1 0", particle);
            if (!particle.getDataType().isInstance(data)) {
                wrong.add(particle.name() + " got " + data);
            }
        }

        assertThat(wrong)
                .describedAs("the server refuses each of these as missing its data")
                .isEmpty();
    }

    @Test
    @DisplayName("a dust takes the colour the line ends with")
    void aDustTakesItsColour() {
        Object data = present("[particle] dust 10 0.3 #ff0000", Particle.DUST);

        assertThat(data).isInstanceOf(Particle.DustOptions.class);
        assertThat(((Particle.DustOptions) data).getColor()).isEqualTo(Color.fromRGB(0xff0000));
    }

    @Test
    @DisplayName("a colour transition takes both colours")
    void aTransitionTakesBothColours() {
        Object data =
                present("[particle] dust_color_transition 10 0.3 #ff0000>#0000ff", Particle.DUST_COLOR_TRANSITION);

        Particle.DustTransition transition = (Particle.DustTransition) data;
        assertThat(transition.getColor()).isEqualTo(Color.fromRGB(0xff0000));
        assertThat(transition.getToColor()).isEqualTo(Color.fromRGB(0x0000ff));
    }

    @Test
    @DisplayName("a block particle takes the block the line names")
    void aBlockTakesItsBlock() {
        Object data = present("[particle] block 10 0.3 oak_log", Particle.BLOCK);

        assertThat(((BlockData) data).getMaterial()).isEqualTo(Material.OAK_LOG);
    }

    @Test
    @DisplayName("a particle that needs no data is still drawn with none")
    void aPlainParticleTakesNoData() {
        assertThat(drawn("[particle] heart 6 0.6", Particle.HEART)).isNull();
    }

    @Test
    @DisplayName("data that cannot be read draws nothing, the way an unknown name draws nothing")
    void unreadableDataDrawsNothing() {
        ActionList.parse(List.of("[particle] dust 10 0.3 notacolour")).run(context());

        verify(world, never())
                .spawnParticle(
                        any(Particle.class),
                        any(Location.class),
                        anyInt(),
                        anyDouble(),
                        anyDouble(),
                        anyDouble(),
                        any());
    }

    private Object present(String line, Particle particle) {
        return java.util.Objects.requireNonNull(drawn(line, particle), "no data was drawn");
    }

    private @Nullable Object drawn(String line, Particle particle) {
        World fresh = mock(World.class);
        when(player.getWorld()).thenReturn(fresh);
        ActionList.parse(List.of(line)).run(context());
        ArgumentCaptor<Object> data = ArgumentCaptor.forClass(Object.class);
        verify(fresh)
                .spawnParticle(
                        eq(particle),
                        any(Location.class),
                        anyInt(),
                        anyDouble(),
                        anyDouble(),
                        anyDouble(),
                        data.capture());
        return data.getValue();
    }

    private ActionContext context() {
        return ActionContext.builder(OperandResolver.identity())
                .player(player)
                .target(player)
                .later((delay, what) -> {})
                .build();
    }
}
