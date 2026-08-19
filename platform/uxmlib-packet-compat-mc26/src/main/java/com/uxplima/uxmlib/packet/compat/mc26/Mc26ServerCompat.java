package com.uxplima.uxmlib.packet.compat.mc26;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmlib.packet.compat.ServerCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

/** The {@link ServerCompat} implementation for the 26.x server line. */
public final class Mc26ServerCompat implements ServerCompat {

    @Override
    public int nextEntityId(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        // The counter moved onto the level here. It is still backed by one static shared by every world, but
        // asking the level lets the server skip ids that world already has in play.
        return level.getNextEntityId();
    }

    @Override
    public void applyTeamColor(PlayerTeam team, String vanillaColorName) {
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(vanillaColorName, "vanillaColorName");
        // Team colours became their own enum, wrapped in an Optional because a team may have none. The
        // constant names line up one-for-one with the older ChatFormatting colours.
        team.setColor(Optional.of(TeamColor.valueOf(vanillaColorName)));
    }
}
