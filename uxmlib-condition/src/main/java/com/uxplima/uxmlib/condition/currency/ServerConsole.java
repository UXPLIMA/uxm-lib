package com.uxplima.uxmlib.condition.currency;

import java.util.Objects;

import com.uxplima.uxmlib.condition.wallet.ConsoleCommands;
import com.uxplima.uxmlib.condition.wallet.PlaceholderWallet;
import com.uxplima.uxmlib.scheduler.GlobalCall;
import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * The console, reached from the thread the money work runs on.
 *
 * <p>{@link ConsoleCommands} sends the line; this carries it to the thread the server ticks on, where a
 * command runs, and brings the answer back. The answer is only whether the server took the line: a command
 * says nothing about what the plugin behind it did, and {@link PlaceholderCurrency} says what that costs.
 */
public final class ServerConsole implements PlaceholderCurrency.Console {

    private final Scheduler scheduler;
    private final PlaceholderWallet.Console console = ConsoleCommands.ofServer();

    public ServerConsole(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean run(String line) {
        Objects.requireNonNull(line, "line");
        return GlobalCall.on(scheduler, () -> console.run(line)).orElse(false);
    }
}
