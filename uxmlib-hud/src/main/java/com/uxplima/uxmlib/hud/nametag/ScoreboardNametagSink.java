package com.uxplima.uxmlib.hud.nametag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.format.NamedTextColor;

import org.jspecify.annotations.Nullable;

/**
 * Writes a composed name to a scoreboard team the registry owns from end to end: one team per player, created
 * here, named with {@link #TEAM_PREFIX} so its own teams are recognisable, and unregistered again by
 * {@link #clear} and {@link #clearAll}.
 *
 * <p>A team it did not create is left strictly alone. If a player already belongs to somebody else's team,
 * this backs off and says so once rather than moving the player out of it: adding an entry to a team removes
 * it from the one it was in, which would break a third-party plugin that manages its own teams. The
 * consequence is worth stating plainly: on a server where another plugin already owns the teams, this sink
 * shows nothing, and that is the correct outcome.
 */
public final class ScoreboardNametagSink implements NametagSink {

    /** Every team this sink creates is named with this prefix, so its own teams are recognisable. */
    public static final String TEAM_PREFIX = "uxm-";

    private final Scoreboard board;
    private final Logger log;
    private final Map<UUID, String> teamNames = new ConcurrentHashMap<>();
    private final AtomicInteger nextTeam = new AtomicInteger();
    private final Set<UUID> warnedForeign = ConcurrentHashMap.newKeySet();

    public ScoreboardNametagSink(Scoreboard board, Logger log) {
        this.board = Objects.requireNonNull(board, "board");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void apply(UUID player, String entry, ComposedNametag name) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(name, "name");
        Team team = ownTeam(player, entry);
        if (team == null) {
            return;
        }
        team.prefix(name.prefix());
        team.suffix(name.suffix());
        NamedTextColor color = name.color();
        // White is the team default, so "nobody asked for a colour" and "reset the colour" are the same write.
        team.color(color == null ? NamedTextColor.WHITE : color);
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
    }

    @Override
    public void clear(UUID player, String entry) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(entry, "entry");
        String name = teamNames.remove(player);
        if (name == null) {
            return;
        }
        unregister(name);
    }

    @Override
    public void clearAll() {
        List<String> names = new ArrayList<>(teamNames.values());
        teamNames.clear();
        for (String name : names) {
            unregister(name);
        }
    }

    /** This sink's team for {@code player}, created on demand, or {@code null} when another plugin owns it. */
    private @Nullable Team ownTeam(UUID player, String entry) {
        String name = teamNames.get(player);
        Team current = board.getEntryTeam(entry);
        if (current != null && !current.getName().equals(name)) {
            warnForeign(player, entry, current);
            return null;
        }
        if (name == null) {
            name = TEAM_PREFIX + nextTeam.incrementAndGet();
            teamNames.put(player, name);
        }
        Team existing = board.getTeam(name);
        return existing != null ? existing : board.registerNewTeam(name);
    }

    private void warnForeign(UUID player, String entry, Team owner) {
        if (!warnedForeign.add(player)) {
            return; // said once per player: a refresh runs on every contribution and this is not news twice
        }
        log.warning("Leaving " + entry + " alone: they are already on the team '" + owner.getName()
                + "', which uxmLib did not create. Nametag contributions for this player are not shown.");
    }

    private void unregister(String name) {
        Team team = board.getTeam(name);
        if (team != null) {
            team.unregister();
        }
    }
}
