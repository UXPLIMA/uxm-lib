package com.uxplima.uxmlib.hud.nametag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.logging.Logger;

import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The scoreboard half: one team per player, created and named by the sink, carrying the composed prefix,
 * suffix and colour, and a player who already belongs to somebody else's team left exactly as they were.
 */
class ScoreboardNametagSinkTest {

    private ServerMock server;
    private Scoreboard board;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        board = server.getScoreboardManager().getMainScoreboard();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ComposedNametag name() {
        return ComposedNametag.compose(
                List.of(
                        NametagContribution.prefix("clans", 100, Component.text("[Wolves]")),
                        NametagContribution.suffix("level", 100, Component.text("(42)")),
                        NametagContribution.color("glow", 100, NamedTextColor.RED)),
                " ");
    }

    private ScoreboardNametagSink sink() {
        return new ScoreboardNametagSink(board, Logger.getAnonymousLogger());
    }

    @Test
    void applyingPutsTheComposedNameOnATeamItCreatedItself() {
        PlayerMock player = server.addPlayer();

        sink().apply(player.getUniqueId(), player.getName(), name());

        Team team = board.getEntryTeam(player.getName());
        assertThat(team).isNotNull();
        Team found = java.util.Objects.requireNonNull(team);
        assertThat(found.getName()).startsWith(ScoreboardNametagSink.TEAM_PREFIX);
        assertThat(Text.plain(found.prefix())).isEqualTo("[Wolves]");
        assertThat(Text.plain(found.suffix())).isEqualTo("(42)");
        assertThat(found.color()).isEqualTo(NamedTextColor.RED);
    }

    @Test
    void asecondApplyReusesTheSameTeamRatherThanPilingThemUp() {
        PlayerMock player = server.addPlayer();
        ScoreboardNametagSink sink = sink();

        sink.apply(player.getUniqueId(), player.getName(), name());
        sink.apply(player.getUniqueId(), player.getName(), name());

        assertThat(board.getTeams()).hasSize(1);
    }

    @Test
    void clearingUnregistersTheTeamItCreated() {
        PlayerMock player = server.addPlayer();
        ScoreboardNametagSink sink = sink();
        sink.apply(player.getUniqueId(), player.getName(), name());

        sink.clear(player.getUniqueId(), player.getName());

        assertThat(board.getTeams()).isEmpty();
        assertThat(board.getEntryTeam(player.getName())).isNull();
    }

    @Test
    void clearAllHandsBackEveryTeamItCreated() {
        PlayerMock first = server.addPlayer();
        PlayerMock second = server.addPlayer();
        ScoreboardNametagSink sink = sink();
        sink.apply(first.getUniqueId(), first.getName(), name());
        sink.apply(second.getUniqueId(), second.getName(), name());

        sink.clearAll();

        assertThat(board.getTeams()).isEmpty();
    }

    @Test
    void aPlayerOnAnotherPluginsTeamIsLeftExactlyWhereTheyWere() {
        PlayerMock player = server.addPlayer();
        Team theirs = board.registerNewTeam("someone-elses");
        theirs.addEntry(player.getName());
        theirs.prefix(Component.text("[Theirs]"));

        sink().apply(player.getUniqueId(), player.getName(), name());

        // Ours is never created, and theirs is untouched -- taking the entry would have broken their plugin.
        assertThat(board.getTeams()).containsExactly(theirs);
        assertThat(Text.plain(theirs.prefix())).isEqualTo("[Theirs]");
    }
}
