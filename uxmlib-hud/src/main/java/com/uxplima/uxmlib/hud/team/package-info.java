/**
 * The owner of the main scoreboard's non-player entries.
 *
 * <p>The sibling {@code nametag} package owns the team of a player. This one owns everything else that has to
 * be on a team: a mob a cosmetic spawned, an armour stand under a hologram, a display that follows somebody.
 * The reason those need a team at all is the collision rule, which is a property of a team and of nothing else,
 * and which a nametag contribution cannot carry.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmlib.hud.team;
