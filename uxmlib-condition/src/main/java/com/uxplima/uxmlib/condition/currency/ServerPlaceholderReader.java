package com.uxplima.uxmlib.condition.currency;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;

import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * The one door onto PlaceholderAPI for a currency.
 *
 * <p>Nothing outside this class names a type of that plugin, so a server without it loads this plugin as
 * usual: a class is read by the machine when something calls into it, and nothing calls into the reader
 * until the plugin is found.
 *
 * <p>The question is asked on the thread the server ticks on. An expansion is written for that thread,
 * and the money work of this plugin runs off it.
 */
public final class ServerPlaceholderReader implements PlaceholderCurrency.Reader {

    private static final String PLUGIN = "PlaceholderAPI";

    private final Scheduler scheduler;

    public ServerPlaceholderReader(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean isReady() {
        return Bukkit.getPluginManager().getPlugin(PLUGIN) != null;
    }

    @Override
    public Optional<String> read(UUID player, String placeholder) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(placeholder, "placeholder");
        if (!isReady()) {
            return Optional.empty();
        }
        return GlobalCall.on(scheduler, () -> Answered.of(player, placeholder));
    }

    /** The call itself, in a class of its own, so the type of the other plugin is read only here. */
    private static final class Answered {

        private Answered() {}

        private static String of(UUID player, String placeholder) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(Bukkit.getOfflinePlayer(player), placeholder);
        }
    }
}
