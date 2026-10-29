package com.uxplima.uxmlib.condition.currency;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Who the server has seen.
 *
 * <p>An operator types a name and the plugin keeps ids, so one of the two has to be turned into the other.
 * It is a port because a name is a server question, and because a test must be able to answer it without
 * one.
 */
public interface KnownPlayers {

    /** The id of the player with this name, absent when the server has never seen the name. */
    Optional<UUID> byName(String name);

    /**
     * The name of the player with this id, absent when the server has never seen them.
     *
     * <p>A command that an operator wrote takes a name, so the plugin has to turn its id back into one.
     */
    Optional<String> nameOf(UUID id);

    /**
     * The server: an online player answers first, then a player the server already has on disk. It never
     * asks Mojang, because a lookup that waits on the network holds a thread, so a player this server has
     * never seen has no id and no name here.
     */
    static KnownPlayers ofServer() {
        return new KnownPlayers() {
            @Override
            public Optional<UUID> byName(String name) {
                if (name.isBlank()) {
                    return Optional.empty();
                }
                Player online = Bukkit.getPlayerExact(name);
                if (online != null) {
                    return Optional.of(online.getUniqueId());
                }
                return Optional.ofNullable(Bukkit.getOfflinePlayerIfCached(name))
                        .map(OfflinePlayer::getUniqueId);
            }

            @Override
            public Optional<String> nameOf(UUID id) {
                Player online = Bukkit.getPlayer(id);
                return online != null
                        ? Optional.of(online.getName())
                        : Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName());
            }
        };
    }
}
