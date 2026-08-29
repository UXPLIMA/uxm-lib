package com.uxplima.uxmlib.packet.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.scores.PlayerTeam;

/**
 * The server internals uxmLib uses that are not spelled the same way on every supported Minecraft line.
 *
 * <p>The packet modules are compiled against one line's Mojang-mapped server and are expected to run on all
 * of them, which works because the overwhelming majority of what they touch has not moved. This interface
 * carries the exceptions. Keeping it this small is the point: every method here is a place the build can no
 * longer check for us, so anything that can be written against a shared API: a registry lookup instead of a
 * per-line constant, say, belongs in the shared code rather than in another method on this type.
 *
 * <p>One implementation exists per supported line, each compiled against that line's own dev bundle, so both
 * sides of every seam stay compiler-verified. {@code ServerCompats} in {@code uxmlib-packet} picks the one
 * matching the running server.
 */
public interface ServerCompat {

    /**
     * The next free entity id from the counter the server itself spawns real entities from, so an id handed
     * out here can never collide with one.
     *
     * @param level the level the id is meant for; lines that allocate from a single server-wide counter
     *     ignore it, lines that track per-level ids use it to skip ids already in play
     */
    int nextEntityId(ServerLevel level);

    /**
     * Paint {@code team} in the vanilla colour named {@code vanillaColorName}, which is the name of a constant
     * on whichever enum this line keeps team colours in.
     *
     * @throws IllegalArgumentException if this line has no colour by that name
     */
    void applyTeamColor(PlayerTeam team, String vanillaColorName);
}
