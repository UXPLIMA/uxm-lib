package com.uxplima.uxmlib.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalInt;
import java.util.UUID;

import com.uxplima.uxmlib.condition.OperandResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * What an operand means, on a server that runs no skill plugin and on one that does.
 *
 * <p>The case that matters is the first one: a token nobody answers has to come back as it went in, so a
 * comparison against it refuses rather than throwing and a server running none of these plugins reads
 * exactly what it read before this existed.
 */
final class OperandsTest {

    private ServerMock server;
    private PlayerMock player;

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        Operands.forgetEverything();
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
        Operands.forgetEverything();
    }

    /** A seam that answers one level for one skill and nothing for anything else. */
    private static SkillLevels holding(String skill, int level) {
        return new SkillLevels() {

            @Override
            public boolean active() {
                return true;
            }

            @Override
            public OptionalInt levelOf(UUID who, String asked) {
                return skill.equals(asked) ? OptionalInt.of(level) : OptionalInt.empty();
            }
        };
    }

    @Test
    @DisplayName("with no skill plugin installed nothing is installed and every operand is itself")
    void aBareServerResolvesNothing() {
        assertThat(Operands.active()).isFalse();
        assertThat(Operands.standard().resolve(player, "%mcmmo_level_mining%")).isEqualTo("%mcmmo_level_mining%");
        assertThat(Operands.standard().resolve(player, "10")).isEqualTo("10");
    }

    @Test
    @DisplayName("a skill token resolves to the level the plugin holds")
    void aSkillTokenResolves() {
        Operands.readingSkills(holding("mining", 47));

        assertThat(Operands.active()).isTrue();
        assertThat(Operands.standard().resolve(player, "%mcmmo_level_mining%")).isEqualTo("47");
    }

    @Test
    @DisplayName("a skill the plugin has never heard of comes back as it went in")
    void anUnknownSkillIsLeftAlone() {
        Operands.readingSkills(holding("mining", 47));

        assertThat(Operands.standard().resolve(player, "%mcmmo_level_fishing%")).isEqualTo("%mcmmo_level_fishing%");
    }

    @Test
    @DisplayName("an operand with no player behind it is left alone rather than throwing")
    void noPlayerResolvesNothing() {
        Operands.readingSkills(holding("mining", 47));

        assertThat(Operands.standard().resolve(null, "%mcmmo_level_mining%")).isEqualTo("%mcmmo_level_mining%");
    }

    @Test
    @DisplayName("chained in front of another resolver, everything it does not answer goes on")
    void whatItDoesNotAnswerIsHandedOn() {
        Operands.readingSkills(holding("mining", 47));
        OperandResolver chained = Operands.resolving((who, template) -> "handed on");

        assertThat(chained.resolve(player, "%mcmmo_level_mining%")).isEqualTo("47");
        assertThat(chained.resolve(player, "%player_name%")).isEqualTo("handed on");
    }

    @Test
    @DisplayName("forgetting the seam puts the operand back to itself, which a shutdown does")
    void forgettingPutsItBack() {
        Operands.readingSkills(holding("mining", 47));
        Operands.forgetEverything();

        assertThat(Operands.active()).isFalse();
        assertThat(Operands.standard().resolve(player, "%mcmmo_level_mining%")).isEqualTo("%mcmmo_level_mining%");
    }
}
