package com.uxplima.uxmlib.condition.wallet;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * What an economy plugin wants where the player goes.
 *
 * <p>Three answers are in use: the id, the name, and an offline player. The question is asked about an id
 * rather than about a live {@link Player}, because the money a plugin moves is not always the money of
 * somebody who is connected: an auction pays a seller who is asleep, and a shop refunds an order placed
 * yesterday. An economy that keeps a balance in a table does not care either way.
 *
 * <p>It stays a seam so that a test can watch what the reader passed, and so that the reader itself names
 * no Bukkit type in its own logic.
 */
public interface PlayerArguments {

    /** The player {@code id} stands for, in the shape {@code shape} asks for. */
    Object of(EconomyBinding.Argument shape, UUID id);

    /**
     * The server's answer: the id itself, the name it knows for it, or the player object.
     *
     * <p>A player who is connected is handed over as the live {@link Player}, which is what this asked for
     * before it worked from an id and is what an economy holding a per-session view expects. One who is not
     * is handed the {@link OfflinePlayer}, which every economy of the four shapes accepts. A name the
     * server has never seen answers as the id in text, because a null there would reach the economy as a
     * missing argument rather than as an unknown player.
     */
    static PlayerArguments ofServer() {
        return (shape, id) -> switch (shape) {
            case PLAYER_ID -> id;
            case PLAYER_NAME -> {
                String name = player(id).getName();
                yield name == null ? id.toString() : name;
            }
            case OFFLINE_PLAYER -> player(id);
        };
    }

    private static OfflinePlayer player(UUID id) {
        Player online = Bukkit.getPlayer(id);
        return online != null ? online : Bukkit.getOfflinePlayer(id);
    }
}
