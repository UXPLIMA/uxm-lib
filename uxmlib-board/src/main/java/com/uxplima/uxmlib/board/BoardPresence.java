package com.uxplima.uxmlib.board;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Loads a player's scores when they log in, keeps the name they log in under, and drops the scores when they quit.
 *
 * <p>The pre-login event already runs off the main thread, so the reads cost the join nothing. Register it with the
 * plugin's listeners, and call {@link #loadOnline} once on enable for the players already on a server that reloaded
 * the plugin.
 */
public final class BoardPresence implements Listener {

    private final Standings standings;
    private final BoardStore store;

    public BoardPresence(Standings standings, BoardStore store) {
        this.standings = Objects.requireNonNull(standings, "standings");
        this.store = Objects.requireNonNull(store, "store");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            standings.load(event.getUniqueId());
            store.name(event.getUniqueId(), event.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        standings.forget(event.getPlayer().getUniqueId());
    }

    /** Load the players who are already online, on {@code async}. */
    public void loadOnline(Collection<? extends Player> online, Executor async) {
        List<UUID> here = online.stream().map(Player::getUniqueId).toList();
        if (!here.isEmpty()) {
            Objects.requireNonNull(async, "async").execute(() -> here.forEach(standings::load));
        }
    }
}
