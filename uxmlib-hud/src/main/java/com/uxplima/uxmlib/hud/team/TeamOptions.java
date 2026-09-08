package com.uxplima.uxmlib.hud.team;

import java.util.Objects;

import org.bukkit.scoreboard.Team;

/**
 * What a caller wants a scoreboard team to do, and nothing about who is in it.
 *
 * <p>These three are the reason a plugin reaches for a team at all when it has no name to draw. A collision
 * rule is the one thing vanilla checks before it pushes anybody, so an entity that must not shove a player has
 * to be on a team that says so. Name-tag visibility and friendly invisibility ride along because they are set
 * on the same object and a caller that wants one usually has an opinion about the others.
 *
 * <p>A value class rather than a builder, because it is the key: {@link EntryTeams} keeps one team per distinct
 * set of options and puts every entry that asked for the same three on it. Two plugins that want the same
 * behaviour share one team and neither has to know the other exists.
 */
public record TeamOptions(
        Team.OptionStatus collisionRule, Team.OptionStatus nameTagVisibility, boolean seeFriendlyInvisibles) {

    public TeamOptions {
        Objects.requireNonNull(collisionRule, "collisionRule");
        Objects.requireNonNull(nameTagVisibility, "nameTagVisibility");
    }

    /**
     * The common case: this entry pushes nobody. Its name tag is left on and friendly invisibility is left off,
     * which is what a fresh team already does, so nothing is changed that was not asked for.
     */
    public static TeamOptions noCollision() {
        return new TeamOptions(Team.OptionStatus.NEVER, Team.OptionStatus.ALWAYS, false);
    }

    /** Write these options onto {@code team}. */
    public void applyTo(Team team) {
        Objects.requireNonNull(team, "team");
        team.setOption(Team.Option.COLLISION_RULE, collisionRule);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, nameTagVisibility);
        team.setCanSeeFriendlyInvisibles(seeFriendlyInvisibles);
    }
}
