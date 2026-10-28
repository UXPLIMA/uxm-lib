package com.uxplima.uxmlib.condition.currency;

import java.util.Objects;

import org.bukkit.Bukkit;

import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * The console, as a currency reaches it.
 *
 * <p>A command runs on the thread the server ticks on, and the money work of this plugin runs off it, so
 * the line hops over and the answer comes back. The answer is only whether the server took the line: a
 * command says nothing about what the plugin behind it did, and {@link PlaceholderCurrency} says what
 * that costs.
 */
public final class ServerConsole implements PlaceholderCurrency.Console {

    private final Scheduler scheduler;

    public ServerConsole(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean run(String line) {
        Objects.requireNonNull(line, "line");
        return GlobalCall.on(scheduler, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line))
                .orElse(false);
    }
}
