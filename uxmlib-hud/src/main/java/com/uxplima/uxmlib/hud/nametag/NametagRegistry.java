package com.uxplima.uxmlib.hud.nametag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.Nullable;

/**
 * The one owner of a player's name. Every plugin that wants a say hands over a
 * {@link NametagContribution} keyed by its own name; the registry composes them all into a single
 * {@link ComposedNametag} and writes it through a {@link NametagSink}.
 *
 * <p>This exists because a player may belong to exactly one scoreboard team. Plugins that each create their
 * own teams therefore overwrite one another — a tag plugin's prefix disappears the moment a glow plugin puts
 * the player in a colour team — and nothing reports it. Composing into one team the registry owns lets a
 * prefix, a suffix and a colour from three plugins live on the same name.
 *
 * <p>A contribution is dropped when the plugin that made it says so: {@link #withdraw(String)} takes back
 * everything one plugin contributed, which is what its {@code onDisable} calls, and the affected names are
 * recomputed at once. {@link #close} hands the server back what it had.
 *
 * <p>Give it a {@link Scheduler} when the display it writes to belongs to a particular thread — a scoreboard
 * does — and every write is routed onto the global region. Without one, writes happen on the calling thread,
 * which is right for a consumer that already calls from there.
 */
public final class NametagRegistry {

    /** What separates two contributed parts of the same half; a plain space unless an operator says otherwise. */
    public static final String DEFAULT_SEPARATOR = " ";

    private final NametagSink sink;
    private final Logger log;
    private final String separator;
    private final @Nullable Scheduler scheduler;
    private final Map<UUID, PlayerTags> byPlayer = new ConcurrentHashMap<>();
    private final Set<String> reportedClashes = ConcurrentHashMap.newKeySet();

    /** A registry that writes on the calling thread, with a plain space between parts. */
    public NametagRegistry(NametagSink sink, Logger log) {
        this(sink, log, DEFAULT_SEPARATOR, null);
    }

    /**
     * @param separator what goes between two contributed parts of the same half
     * @param scheduler the scheduler that owns the display's thread, or {@code null} to write inline
     */
    public NametagRegistry(NametagSink sink, Logger log, String separator, @Nullable Scheduler scheduler) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.log = Objects.requireNonNull(log, "log");
        this.separator = Objects.requireNonNull(separator, "separator");
        this.scheduler = scheduler;
    }

    /** Record {@code contribution} for {@code player}, replacing that plugin's previous one, and recompose. */
    public void contribute(Player player, NametagContribution contribution) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(contribution, "contribution");
        UUID id = player.getUniqueId();
        byPlayer.computeIfAbsent(id, key -> new PlayerTags(player.getName()))
                .contributions()
                .put(contribution.plugin(), contribution);
        refresh(id);
    }

    /** Take back what {@code plugin} contributed to {@code player} alone, and recompose that name. */
    public void withdraw(Player player, String plugin) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(plugin, "plugin");
        UUID id = player.getUniqueId();
        PlayerTags tags = byPlayer.get(id);
        if (tags != null && tags.contributions().remove(plugin) != null) {
            refresh(id);
        }
    }

    /** Take back everything {@code plugin} contributed, for a plugin disabling, and recompose every name. */
    public void withdraw(String plugin) {
        Objects.requireNonNull(plugin, "plugin");
        for (Map.Entry<UUID, PlayerTags> entry : byPlayer.entrySet()) {
            if (entry.getValue().contributions().remove(plugin) != null) {
                refresh(entry.getKey());
            }
        }
    }

    /** Forget a player entirely and drop the name they wore; for a quit. */
    public void forget(Player player) {
        Objects.requireNonNull(player, "player");
        forget(player.getUniqueId());
    }

    /** Forget the player with {@code id}, as {@link #forget(Player)} does when the Player is already gone. */
    public void forget(UUID id) {
        Objects.requireNonNull(id, "id");
        PlayerTags tags = byPlayer.remove(id);
        if (tags != null) {
            run(() -> sink.clear(id, tags.entry()));
        }
    }

    /** Give the server back what it had: every name this registry wrote is dropped. */
    public void close() {
        byPlayer.clear();
        reportedClashes.clear();
        run(sink::clearAll);
    }

    /** What {@code id} currently wears, for a consumer that wants to inspect the composition it caused. */
    public ComposedNametag composed(UUID id) {
        Objects.requireNonNull(id, "id");
        PlayerTags tags = byPlayer.get(id);
        List<NametagContribution> contributions =
                tags == null ? List.of() : new ArrayList<>(tags.contributions().values());
        return ComposedNametag.compose(contributions, separator);
    }

    private void refresh(UUID id) {
        PlayerTags tags = byPlayer.get(id);
        if (tags == null) {
            return;
        }
        ComposedNametag name = ComposedNametag.compose(tags.contributions().values(), separator);
        reportClash(tags.entry(), name);
        run(() -> sink.apply(id, tags.entry(), name));
    }

    /**
     * Say once which plugins wanted this name's colour and which one has it. Two plugins colouring the same
     * name is not an error — one of them simply cannot win — but silence about it is what turns a settled
     * rule into a bug report, so the losing plugin is named too and an operator can re-order them.
     */
    private void reportClash(String entry, ComposedNametag name) {
        if (!name.hasColorClash()) {
            return;
        }
        List<String> sources = name.colorSources();
        if (!reportedClashes.add(entry + '|' + String.join(",", sources))) {
            return;
        }
        log.info("More than one plugin colours " + entry + "'s name: " + String.join(", ", sources) + ". "
                + sources.get(0) + " wins it; change a plugin's nametag priority to swap them.");
    }

    private void run(Runnable write) {
        Scheduler owner = scheduler;
        if (owner == null) {
            write.run();
            return;
        }
        owner.global(write);
    }

    /** A player's own contributions, plus the entry name the display knows them by. */
    private record PlayerTags(String entry, Map<String, NametagContribution> contributions) {
        PlayerTags(String entry) {
            this(entry, new ConcurrentHashMap<>());
        }
    }
}
