package com.uxplima.uxmlib.condition.currency.treasury;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.uxplima.uxmlib.condition.currency.Currency;
import com.uxplima.uxmlib.condition.currency.CurrencyId;
import com.uxplima.uxmlib.condition.currency.Money;
import com.uxplima.uxmlib.condition.wallet.TreasuryWallet;

/**
 * A balance Treasury holds.
 *
 * <p>The work is {@link TreasuryWallet}'s: it waits for each answer with a bound, reads before it takes,
 * opens an account only to pay, and names the account in the console when Treasury leaves a move
 * unanswered. What this adds is the plugin's view of it: a balance under zero reads as nothing to spend,
 * and an account of Treasury is a row under a player id, so a player who is away is paid like anybody.
 *
 * <p>The plugin asks Treasury off the main threads, like it asks every other economy. The bound is
 * {@code wait-for} in the currency block, so an operator whose economy sits on a slow database can give it
 * longer.
 */
public final class TreasuryCurrency implements Currency {

    /** The service Treasury registers, named as text so nothing loads it before the plugin is found. */
    public static final String PROVIDER = TreasuryWallet.PROVIDER;

    private final CurrencyId id;
    private final String named;
    private final TreasuryWallet wallet;

    /**
     * @param id the id of this currency in the plugin
     * @param named the id of the currency inside Treasury. An empty one means the primary currency
     * @param provider where the Treasury service comes from, empty when the plugin is not here
     * @param waitFor how long one call may take before the plugin gives up on it
     */
    public TreasuryCurrency(
            CurrencyId id, String named, Supplier<Optional<Object>> provider, Duration waitFor, System.Logger log) {
        this(id, named, new TreasuryWallet(provider, checked(id, waitFor), log));
    }

    /** The same currency, over a wallet somebody else built. */
    public TreasuryCurrency(CurrencyId id, String named, TreasuryWallet wallet) {
        this.id = Objects.requireNonNull(id, "id");
        this.named = Objects.requireNonNull(named, "named").strip();
        this.wallet = Objects.requireNonNull(wallet, "wallet");
    }

    private static Duration checked(CurrencyId id, Duration waitFor) {
        Objects.requireNonNull(waitFor, "waitFor");
        if (waitFor.isNegative() || waitFor.isZero()) {
            throw new IllegalArgumentException("the treasury currency " + id + " must wait longer than nothing");
        }
        return waitFor;
    }

    @Override
    public CurrencyId id() {
        return id;
    }

    /** Treasury is here, and it holds the currency this plugin was told to price in. */
    @Override
    public boolean isReady() {
        return wallet.reaches(named);
    }

    @Override
    public boolean paysOffline() {
        return true;
    }

    /** Treasury says how many places a currency takes. One that takes none counts in whole numbers. */
    @Override
    public boolean isWhole() {
        return wallet.countsWhole(named);
    }

    @Override
    public Money balanceOf(UUID player) {
        Objects.requireNonNull(player, "player");
        // Treasury lets a balance go under nothing; a player in debt has nothing to spend.
        return new Money(id, wallet.balanceExact(player, named).max(BigDecimal.ZERO));
    }

    @Override
    public boolean take(UUID player, Money amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(amount, "amount");
        return wallet.withdrawExact(player, named, amount.amount());
    }

    @Override
    public boolean give(UUID player, Money amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(amount, "amount");
        return wallet.depositExact(player, named, amount.amount());
    }
}
