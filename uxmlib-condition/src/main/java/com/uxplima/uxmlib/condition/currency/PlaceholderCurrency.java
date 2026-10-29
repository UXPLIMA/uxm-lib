package com.uxplima.uxmlib.condition.currency;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmlib.condition.wallet.PlaceholderWallet;

/**
 * A balance that is read through a placeholder and changed by two commands.
 *
 * <p>Some economy plugins publish no API at all, and some publish one that changes every release. Nearly
 * all of them publish a placeholder for the balance and a command for a give and a take, because that is
 * what an operator uses every day. This currency is that pair, and the work is {@link PlaceholderWallet}'s:
 * it reads the number a placeholder answers with, refuses a take the player cannot afford, and after that
 * trusts the line it sent. What this adds is the plugin's rules: whether an absent player may be paid, and
 * whether the currency counts in whole numbers.
 *
 * <p>A balance under zero, or one that cannot be read as a number, is read as zero. Zero is the safe way
 * round: it refuses a purchase that the plugin cannot prove the player can pay, and it never invents money.
 */
public final class PlaceholderCurrency implements Currency {

    /** What a placeholder answers for one player, and whether the plugin that answers is on this server. */
    public interface Reader extends PlaceholderWallet.Reader {

        /** Whether the plugin that answers placeholders is on this server. */
        boolean isReady();
    }

    /** How one line reaches the server. True means the server took the line. */
    @FunctionalInterface
    public interface Console extends PlaceholderWallet.Console {}

    /** The wallet holds one pool, under the default name. */
    private static final String POOL = "";

    private final CurrencyId id;
    private final CurrencySpec.OfPlaceholder spec;
    private final Reader reader;
    private final PlaceholderWallet wallet;

    public PlaceholderCurrency(CurrencySpec.OfPlaceholder spec, Reader reader, Console console, KnownPlayers players) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.id = spec.id();
        this.reader = Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(console, "console");
        Objects.requireNonNull(players, "players");
        PlaceholderWallet.Pool pool =
                new PlaceholderWallet.Pool(spec.placeholder(), spec.take(), spec.thousands(), spec.give());
        this.wallet = new PlaceholderWallet(Map.of(POOL, pool), reader, console, players::nameOf);
    }

    @Override
    public CurrencyId id() {
        return id;
    }

    @Override
    public boolean isReady() {
        return reader.isReady();
    }

    @Override
    public boolean paysOffline() {
        return spec.paysOffline();
    }

    @Override
    public boolean isWhole() {
        return spec.whole();
    }

    @Override
    public Money balanceOf(UUID player) {
        Objects.requireNonNull(player, "player");
        return new Money(id, wallet.balanceExact(player, POOL).max(BigDecimal.ZERO));
    }

    @Override
    public boolean take(UUID player, Money amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(amount, "amount");
        return wallet.withdrawExact(player, POOL, amount.amount());
    }

    @Override
    public boolean give(UUID player, Money amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(amount, "amount");
        return wallet.depositExact(player, POOL, amount.amount());
    }
}
