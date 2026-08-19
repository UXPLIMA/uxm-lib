package com.uxplima.uxmlib.packet.compat.mc1_21;

import java.util.Objects;

import com.uxplima.uxmlib.packet.compat.ServerCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;

/** The {@link ServerCompat} implementation for the 1.21.x server line. */
public final class Mc1_21ServerCompat implements ServerCompat {

    @Override
    public int nextEntityId(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        // 1.21.x allocates from a single static counter on Entity, shared by every world, so the level a
        // caller has in hand tells this line nothing it does not already know.
        return Entity.nextEntityId();
    }

    @Override
    public void applyTeamColor(PlayerTeam team, String vanillaColorName) {
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(vanillaColorName, "vanillaColorName");
        // On this line a team's colour is a ChatFormatting, the enum that also carries the non-colour styles.
        team.setColor(ChatFormatting.valueOf(vanillaColorName));
    }
}
