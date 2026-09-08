package com.uxplima.uxmlib.hud.team;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The other half of the shared scoreboard: the entries that are not players.
 *
 * <p>uxmCosmetics created {@code uxmcosmetics} by hand and put entity ids in it, because a collision rule is a
 * property of a team and a nametag contribution cannot carry one. That was one plugin claiming a slot on an
 * object the whole server shares, which is the exact thing {@code SharedNametags} was written to stop on the
 * player side.
 */
class ScoreboardEntryTeamsTest {

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

    private ScoreboardEntryTeams teams() {
        return new ScoreboardEntryTeams(board, Logger.getAnonymousLogger());
    }

    @Test
    @DisplayName("an entry lands on a team that carries the options it asked for")
    void anEntryJoinsATeamWithItsOptions() {
        UUID entry = UUID.randomUUID();

        teams().join("uxmCosmetics", entry, TeamOptions.noCollision());

        Team team = board.getEntryTeam(entry.toString());
        assertThat(team).isNotNull();
        assertThat(team.getOption(Team.Option.COLLISION_RULE)).isEqualTo(Team.OptionStatus.NEVER);
        assertThat(team.getName()).startsWith(ScoreboardEntryTeams.TEAM_PREFIX);
    }

    /**
     * The design rests on this. Every plugin relocates its own copy of uxmLib, so two plugins hold two of this
     * class and neither can read the other's fields. A counter would have both call their first team
     * {@code uxm-entry-1} while meaning different things, and the second write would change the first one's
     * collision rule without a word. A name derived from the options cannot disagree with itself.
     */
    @Test
    @DisplayName("two registries that want the same behaviour land on one team, the way two relocated copies do")
    void twoRegistriesShareOneTeam() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        teams().join("uxmCosmetics", first, TeamOptions.noCollision());
        teams().join("uxmMinions", second, TeamOptions.noCollision());

        assertThat(board.getEntryTeam(first.toString()).getName())
                .isEqualTo(board.getEntryTeam(second.toString()).getName());
    }

    @Test
    @DisplayName("two different sets of options never land on one team")
    void differentOptionsGetDifferentTeams() {
        TeamOptions pushes = new TeamOptions(Team.OptionStatus.ALWAYS, Team.OptionStatus.ALWAYS, false);

        assertThat(ScoreboardEntryTeams.teamName(TeamOptions.noCollision()))
                .isNotEqualTo(ScoreboardEntryTeams.teamName(pushes));
    }

    @Test
    @DisplayName("a player is refused: their team belongs to the nametag registry")
    void aPlayerIsRefused() {
        PlayerMock player = server.addPlayer("Ada");

        teams().join("uxmCosmetics", player.getUniqueId(), TeamOptions.noCollision());

        assertThat(board.getEntryTeam(player.getUniqueId().toString())).isNull();
        assertThat(board.getTeams()).isEmpty();
    }

    @Test
    @DisplayName("an entry another plugin's team already holds is left exactly where it is")
    void aForeignEntryIsLeftAlone() {
        UUID entry = UUID.randomUUID();
        Team theirs = board.registerNewTeam("someoneelse");
        theirs.addEntry(entry.toString());

        teams().join("uxmCosmetics", entry, TeamOptions.noCollision());

        assertThat(board.getEntryTeam(entry.toString()).getName()).isEqualTo("someoneelse");
        assertThat(board.getTeam(ScoreboardEntryTeams.teamName(TeamOptions.noCollision())))
                .isNull();
    }

    @Test
    @DisplayName("leaving takes the entry off and leaves the team standing")
    void leavingTakesTheEntryOff() {
        UUID entry = UUID.randomUUID();
        ScoreboardEntryTeams teams = teams();
        teams.join("uxmCosmetics", entry, TeamOptions.noCollision());

        teams.leave("uxmCosmetics", entry);

        assertThat(board.getEntryTeam(entry.toString())).isNull();
    }

    @Test
    @DisplayName("leaveAll takes back what one plugin put there and nothing another plugin did")
    void leaveAllIsPerPlugin() {
        UUID mine = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        ScoreboardEntryTeams teams = teams();
        teams.join("uxmCosmetics", mine, TeamOptions.noCollision());
        teams.join("uxmMinions", theirs, TeamOptions.noCollision());

        teams.leaveAll("uxmCosmetics");

        assertThat(board.getEntryTeam(mine.toString())).isNull();
        assertThat(board.getEntryTeam(theirs.toString())).isNotNull();
    }

    /**
     * The team is shared by name with every other relocated copy on the server, so unregistering it on the way
     * out would take another plugin's entities off their collision rule and start pushing players around a
     * server this plugin had just left.
     */
    @Test
    @DisplayName("closing takes our entries off and never unregisters the shared team")
    void closingLeavesTheTeamStanding() {
        UUID mine = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        ScoreboardEntryTeams closing = teams();
        closing.join("uxmCosmetics", mine, TeamOptions.noCollision());
        teams().join("uxmMinions", theirs, TeamOptions.noCollision());

        closing.close();

        assertThat(board.getEntryTeam(mine.toString())).isNull();
        assertThat(board.getEntryTeam(theirs.toString()))
                .as("another copy's entry must survive this copy disabling")
                .isNotNull();
    }

    @Test
    @DisplayName("joining twice is the same as joining once")
    void joiningIsIdempotent() {
        UUID entry = UUID.randomUUID();
        ScoreboardEntryTeams teams = teams();

        teams.join("uxmCosmetics", entry, TeamOptions.noCollision());
        teams.join("uxmCosmetics", entry, TeamOptions.noCollision());

        Team team = board.getEntryTeam(entry.toString());
        assertThat(team.getEntries()).containsExactly(entry.toString());
    }

    @Test
    @DisplayName("the options are written again on every join, so a plugin that changed them is corrected")
    void theOptionsAreRewrittenOnEveryJoin() {
        UUID entry = UUID.randomUUID();
        ScoreboardEntryTeams teams = teams();
        teams.join("uxmCosmetics", entry, TeamOptions.noCollision());
        board.getEntryTeam(entry.toString()).setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);

        teams.join("uxmCosmetics", entry, TeamOptions.noCollision());

        assertThat(board.getEntryTeam(entry.toString()).getOption(Team.Option.COLLISION_RULE))
                .isEqualTo(Team.OptionStatus.NEVER);
    }
}
