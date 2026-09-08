package com.uxplima.uxmlib.hud.team;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * {@link EntryTeams} against a real scoreboard.
 *
 * <p><strong>The team is named after what it does.</strong> {@code uxm-e-} plus eight hex digits of the three
 * options, so the name is a pure function of the behaviour asked for. That is what makes this safe without a
 * service registration, and it is the difference between this and {@code SharedNametags}. Every plugin
 * relocates its own copy of uxmLib, so two plugins hold two of this class and neither can see the other's
 * fields; a counter would have both call their first team {@code uxm-entry-1} while meaning different things,
 * and the second one to write would silently change the first one's collision rule. A name derived from the
 * options cannot disagree with itself: two copies that want the same behaviour compute the same name and land
 * on the same team, and two that want different behaviour cannot collide.
 *
 * <p>The options are written on every join, not only at creation, because an operator or another plugin can
 * change them underneath us and the caller asked for a behaviour rather than for a single write.
 *
 * <p>The two refusals are the whole discipline. A {@link Player} is refused because their team belongs to
 * {@code NametagRegistry}, and an entry another team already holds is refused because a team we did not create
 * is not ours to move things out of. Each is said once per entry: a follower is re-anchored on a timer and the
 * same line every tick is not news.
 */
public final class ScoreboardEntryTeams implements EntryTeams {

    /** The prefix every team this class creates carries, so one glance at {@code /team list} says who made it. */
    public static final String TEAM_PREFIX = "uxm-e-";

    private final Scoreboard board;

    private final Logger log;

    /** Which team holds each entry, and which plugin put it there. Ours alone: another copy keeps its own. */
    private final Map<UUID, Held> held = new ConcurrentHashMap<>();

    private final Set<UUID> reported = ConcurrentHashMap.newKeySet();

    public ScoreboardEntryTeams(Scoreboard board, Logger log) {
        this.board = Objects.requireNonNull(board, "board");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * The team name these options are served by. Public because it is the contract: two relocated copies of
     * this class agree on a team only because they agree on this function, and a test that did not assert it
     * would leave the one thing that makes the design work unguarded.
     */
    public static String teamName(TeamOptions options) {
        Objects.requireNonNull(options, "options");
        int key = Objects.hash(
                options.collisionRule().name(), options.nameTagVisibility().name(), options.seeFriendlyInvisibles());
        return TEAM_PREFIX + String.format(Locale.ROOT, "%08x", key);
    }

    @Override
    public void join(String plugin, UUID entry, TeamOptions options) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(options, "options");
        if (isPlayer(entry)) {
            refusePlayer(plugin, entry);
            return;
        }
        String member = entry.toString();
        Team current = board.getEntryTeam(member);
        if (current != null && !ours(current.getName())) {
            refuseForeign(plugin, entry, current);
            return;
        }
        String name = teamName(options);
        Team existing = board.getTeam(name);
        Team team = existing != null ? existing : board.registerNewTeam(name);
        options.applyTo(team);
        if (!team.hasEntry(member)) {
            team.addEntry(member);
        }
        held.put(entry, new Held(plugin, name));
    }

    @Override
    public void leave(String plugin, UUID entry) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(entry, "entry");
        Held was = held.remove(entry);
        if (was == null) {
            return;
        }
        reported.remove(entry);
        Team team = board.getTeam(was.team());
        if (team != null) {
            team.removeEntry(entry.toString());
        }
    }

    @Override
    public void leaveAll(String plugin) {
        Objects.requireNonNull(plugin, "plugin");
        for (Map.Entry<UUID, Held> entry : List.copyOf(held.entrySet())) {
            if (entry.getValue().plugin().equals(plugin)) {
                leave(plugin, entry.getKey());
            }
        }
    }

    /**
     * Take every entry this copy put on a team off again.
     *
     * <p>It does not unregister the team. The team is shared by name with every other relocated copy of this
     * class on the server, so unregistering it on our way out would take another plugin's entities off their
     * collision rule and push players around a server we had just left. An empty team is one line in
     * {@code /team list} and costs nothing.
     */
    @Override
    public void close() {
        for (Map.Entry<UUID, Held> entry : List.copyOf(held.entrySet())) {
            leave(entry.getValue().plugin(), entry.getKey());
        }
        held.clear();
        reported.clear();
    }

    private boolean ours(String team) {
        return team.toLowerCase(Locale.ROOT).startsWith(TEAM_PREFIX);
    }

    private boolean isPlayer(UUID entry) {
        return org.bukkit.Bukkit.getPlayer(entry) != null;
    }

    private void refusePlayer(String plugin, UUID entry) {
        if (!reported.add(entry)) {
            return;
        }
        log.warning("Refusing to put a player on an entry team for " + plugin
                + ": a player's scoreboard team belongs to the nametag registry.");
    }

    private void refuseForeign(String plugin, UUID entry, Team owner) {
        if (!reported.add(entry)) {
            return;
        }
        log.warning("Leaving " + entry + " alone for " + plugin + ": it is already on the team '" + owner.getName()
                + "', which uxmLib did not create.");
    }

    /** Which of our teams an entry is on, and which plugin asked for it. */
    private record Held(String plugin, String team) {

        Held {
            Objects.requireNonNull(plugin, "plugin");
            Objects.requireNonNull(team, "team");
        }
    }
}
