package com.uxplima.uxmlib.condition.currency;

import java.math.BigDecimal;
import java.util.Objects;

import com.uxplima.uxmlib.condition.wallet.BridgedWallet;
import com.uxplima.uxmlib.condition.wallet.EconomyProviders;
import com.uxplima.uxmlib.condition.wallet.PlayerArguments;

/**
 * One economy plugin, read through the library's bridge.
 *
 * <p>The plugin does not know how to talk to ten economy plugins and does not want to: that is a mechanism
 * and uxmLib owns it. What is here is the part that belongs to the plugin, and it is three lines of it. A
 * price is a {@link Money} and a balance is a {@link Money}, so the exact sum goes in and the exact sum
 * comes back; whether a currency may be paid to somebody who is asleep, and whether it counts in whole
 * numbers, are facts about the plugin's rules rather than about the plugin's methods.
 *
 * <p>This class used to be four hundred lines of reflection, written because the library's bridge took a
 * live player and counted in doubles. It does neither now: {@code balanceExact}, {@code withdrawExact} and
 * {@code depositExact} take an id and a {@link BigDecimal}, which is exactly what an auction needs to pay
 * a seller who is offline without rounding the price it promised them.
 */
public final class BridgedCurrency implements Currency {

    private final CurrencyId id;
    private final BridgedWallet wallet;
    private final String pool;
    private final boolean paysOffline;
    private final boolean isWhole;

    public BridgedCurrency(CurrencySpec.OfEconomy spec, EconomyProviders providers, System.Logger log) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(providers, "providers");
        Objects.requireNonNull(log, "log");
        this.id = spec.id();
        this.wallet = new BridgedWallet(spec.binding(), providers, PlayerArguments.ofServer(), log);
        this.pool = spec.pool();
        this.paysOffline = spec.paysOffline();
        this.isWhole = spec.isWhole();
    }

    /** The same currency, reading through a wallet somebody else built. The seam a test reaches for. */
    public BridgedCurrency(CurrencyId id, BridgedWallet wallet, String pool, boolean paysOffline, boolean isWhole) {
        this.id = Objects.requireNonNull(id, "id");
        this.wallet = Objects.requireNonNull(wallet, "wallet");
        this.pool = Objects.requireNonNull(pool, "pool");
        this.paysOffline = paysOffline;
        this.isWhole = isWhole;
    }

    @Override
    public CurrencyId id() {
        return id;
    }

    /**
     * Whether the economy behind this is here and answers.
     *
     * <p>A plugin that lists an item priced in a currency nothing answers has taken a seller's item for a
     * sale that cannot happen, so a currency that does not reach its plugin is left out of the list rather
     * than refusing every payment quietly.
     */
    @Override
    public boolean isReady() {
        return wallet.reaches(pool);
    }

    @Override
    public boolean paysOffline() {
        return paysOffline;
    }

    @Override
    public boolean isWhole() {
        return isWhole;
    }

    @Override
    public Money balanceOf(java.util.UUID player) {
        Objects.requireNonNull(player, "player");
        // An economy that allows debt reports a negative balance; a player in debt has nothing to spend.
        return new Money(id, wallet.balanceExact(player, pool).max(BigDecimal.ZERO));
    }

    @Override
    public boolean take(java.util.UUID player, Money amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(amount, "amount");
        return wallet.withdrawExact(player, pool, amount.amount());
    }

    @Override
    public boolean give(java.util.UUID player, Money amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(amount, "amount");
        return wallet.depositExact(player, pool, amount.amount());
    }
}
