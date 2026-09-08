package com.uxplima.uxmlib.hud.team;

import java.util.UUID;

/**
 * The owner of the main scoreboard's non-player entries.
 *
 * <p>{@code NametagRegistry} owns the team of a <em>player</em>, and it settled the question of two plugins
 * writing one player's name. It could not settle the other half. A cosmetic that spawns a mob, a hologram that
 * rides an armour stand and a display that follows somebody all need a team, because a collision rule is a
 * property of a team and of nothing else, and a {@code NametagContribution} carries a prefix, a suffix and a
 * colour and cannot carry one. So uxmCosmetics created {@code uxmcosmetics} by hand and put entity ids in it,
 * and that is one plugin claiming a slot on an object the whole server shares. This is the missing half.
 *
 * <p>A team here is keyed by what it does, never by who asked. Two plugins that both want "pushes nobody" get
 * the same team and neither learns about the other. The registry keeps a team per distinct {@link TeamOptions},
 * remembers which plugin put each entry there, and hands the entry back untouched when the plugin leaves.
 *
 * <p><strong>A player is refused.</strong> Their team belongs to the nametag registry, and an implementation
 * that took one would be the collision this exists to prevent. So is an entry another team already holds: it is
 * left where it is with one line in the log, because a team somebody else created is theirs.
 */
public interface EntryTeams extends AutoCloseable {

    /**
     * Put {@code entry} on the team that does what {@code options} says, on behalf of {@code plugin}.
     *
     * <p>Idempotent: an entry already on that team stays where it is. An entry that is on a different team of
     * ours moves. An entry on a team we did not create is left alone.
     */
    void join(String plugin, UUID entry, TeamOptions options);

    /** Take {@code entry} off whichever of our teams holds it. Silent when it is on none. */
    void leave(String plugin, UUID entry);

    /** Take every entry {@code plugin} put on a team off again, which is what a plugin disabling should do. */
    void leaveAll(String plugin);

    /** Release every team this registry created. */
    @Override
    void close();
}
