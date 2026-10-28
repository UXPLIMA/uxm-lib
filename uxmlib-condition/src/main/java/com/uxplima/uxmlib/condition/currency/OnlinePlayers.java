package com.uxplima.uxmlib.condition.currency;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Who is here right now.
 *
 * <p>It is one method and an interface, because a currency that reaches for the server directly cannot be
 * tested without one. The shipped implementation is the server itself.
 */
public interface OnlinePlayers {

    Optional<Player> byId(UUID id);

    /** The server. */
    static OnlinePlayers ofServer() {
        return id -> Optional.ofNullable(Bukkit.getPlayer(id));
    }
}
