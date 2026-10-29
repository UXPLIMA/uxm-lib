package com.uxplima.uxmlib.condition.currency;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;

import com.uxplima.uxmlib.condition.currency.treasury.TreasuryCurrency;
import com.uxplima.uxmlib.condition.wallet.EconomyProviders;
import com.uxplima.uxmlib.condition.wallet.TakeLocks;
import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * The second half of reading {@code currencies.conf}: the description becomes a currency.
 *
 * <p>{@link CurrencyFile} says what the operator wrote and this asks the server for it. The split
 * is what lets the file be proved correct with no server running, and it is why a missing plugin or a
 * misspelt material is answered here rather than in the reader.
 *
 * <p>A currency that cannot be built is left out and the console says why. The plugin keeps every other
 * currency, because one wrong line must not close a shop.
 */
public final class CurrencyBuilder {

    /** The plugin that registers the Treasury service. */
    private static final String TREASURY = "Treasury";

    private CurrencyBuilder() {}

    /** Build every currency of {@code specs} that this server can give. */
    public static List<Currency> build(
            List<CurrencySpec> specs,
            Items items,
            OnlinePlayers players,
            Scheduler scheduler,
            EconomyProviders economies,
            PlaceholderCurrency.Reader placeholders,
            PlaceholderCurrency.Console console,
            KnownPlayers known,
            System.Logger log) {
        Objects.requireNonNull(specs, "specs");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(economies, "economies");
        Objects.requireNonNull(placeholders, "placeholders");
        Objects.requireNonNull(console, "console");
        Objects.requireNonNull(known, "known");
        Objects.requireNonNull(log, "log");

        List<Currency> built = new ArrayList<>();
        for (CurrencySpec spec : specs) {
            switch (spec) {
                case CurrencySpec.OfItem item -> {
                    Optional<Material> material = items.byName(item.material());
                    if (material.isEmpty()) {
                        log.log(
                                System.Logger.Level.WARNING,
                                "The {0} currency names the item {1}, and this server has no such item."
                                        + " That currency stays off.",
                                item.id(),
                                item.material());
                        continue;
                    }
                    built.add(new ItemCurrency(item.id(), material.get(), players, scheduler));
                }
                case CurrencySpec.OfExperience experience ->
                    built.add(new ExperienceCurrency(experience.id(), experience.unit(), players, scheduler));
                case CurrencySpec.OfEconomy economy -> built.add(new BridgedCurrency(economy, economies, log));
                case CurrencySpec.OfPlaceholder asked ->
                    built.add(new PlaceholderCurrency(asked, placeholders, console, known));
                // Treasury is the one economy with a class of ours behind it, so it is the one that can
                // fail to load rather than fail to answer. A server that never had the API on its class
                // path says so once and keeps every other currency.
                case CurrencySpec.OfTreasury treasury -> {
                    try {
                        built.add(new TreasuryCurrency(
                                treasury.id(),
                                treasury.named(),
                                () -> economies.service(TREASURY, TreasuryCurrency.PROVIDER),
                                treasury.waitFor(),
                                log));
                    } catch (LinkageError missing) {
                        log.log(
                                System.Logger.Level.WARNING,
                                "The {0} currency is a Treasury currency, and this server cannot load the"
                                        + " Treasury API. That currency stays off.",
                                treasury.id());
                    }
                }
            }
        }
        // One set of take locks for every currency built here, so two currencies over one economy take
        // one at a time per player as well.
        TakeLocks takes = new TakeLocks();
        return built.stream()
                .<Currency>map(currency -> new OneTakeAtATime(currency, takes))
                .toList();
    }
}
