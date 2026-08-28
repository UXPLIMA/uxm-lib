/**
 * One name per player, composed from every plugin that wants a say in it. A player may belong to exactly one
 * scoreboard team, so plugins that each create their own teams fight over that single slot and the last one
 * to write wins — silently. {@link com.uxplima.uxmlib.hud.nametag.NametagRegistry} ends the fight by owning
 * the team itself: each plugin hands it a {@link com.uxplima.uxmlib.hud.nametag.NametagContribution} and the
 * registry composes them into the one name the player wears, so a prefix, a suffix and a colour from three
 * different plugins coexist instead of overwriting each other.
 *
 * <p>{@link com.uxplima.uxmlib.hud.nametag.ComposedNametag} is the composition itself and is pure — order,
 * separator and colour precedence are decided with no server in sight. Applying it is a
 * {@link com.uxplima.uxmlib.hud.nametag.NametagSink}, whose shipped implementation writes to a scoreboard
 * team; a consumer can supply another.
 */
@NullMarked
package com.uxplima.uxmlib.hud.nametag;

import org.jspecify.annotations.NullMarked;
