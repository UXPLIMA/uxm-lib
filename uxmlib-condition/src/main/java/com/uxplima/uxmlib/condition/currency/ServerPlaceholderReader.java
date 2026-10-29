package com.uxplima.uxmlib.condition.currency;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmlib.condition.wallet.ServerEconomyProviders;
import com.uxplima.uxmlib.condition.wallet.ServerPlaceholders;
import com.uxplima.uxmlib.scheduler.GlobalCall;
import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * PlaceholderAPI, asked from the thread the money work runs on.
 *
 * <p>{@link ServerPlaceholders} asks the question; this carries it to the thread the server ticks on, which
 * an expansion is written for, and brings the answer back.
 */
public final class ServerPlaceholderReader implements PlaceholderCurrency.Reader {

    private final Scheduler scheduler;
    private final ServerPlaceholders placeholders = new ServerPlaceholders();

    public ServerPlaceholderReader(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean isReady() {
        return ServerEconomyProviders.isPresent(ServerPlaceholders.PLUGIN);
    }

    @Override
    public Optional<String> read(UUID player, String placeholder) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(placeholder, "placeholder");
        if (!isReady()) {
            return Optional.empty();
        }
        return GlobalCall.on(
                scheduler, () -> placeholders.read(player, placeholder).orElse(null));
    }
}
