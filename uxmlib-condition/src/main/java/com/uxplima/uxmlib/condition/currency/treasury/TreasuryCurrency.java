package com.uxplima.uxmlib.condition.currency.treasury;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.uxplima.uxmlib.condition.currency.Currency;
import com.uxplima.uxmlib.condition.currency.CurrencyId;
import com.uxplima.uxmlib.condition.currency.Money;
import me.lokka30.treasury.api.economy.EconomyProvider;
import me.lokka30.treasury.api.economy.account.PlayerAccount;
import me.lokka30.treasury.api.economy.response.EconomySubscriber;
import me.lokka30.treasury.api.economy.transaction.EconomyTransactionInitiator;

/**
 * A balance Treasury holds.
 *
 * <p>Every other economy of the plugin is reached by a description an operator writes in
 * {@code currencies.conf}: the name of a class, the name of three methods, and what they take. Treasury
 * fits in no such line. It returns nothing and calls back a subscriber instead, an account is a hop of
 * its own before the balance is, and moving money needs a currency object and an initiator that says who
 * asked. So this economy is written out.
 *
 * <p>The plugin asks Treasury off the main threads, like it asks every other economy, and each call waits
 * for the answer with a bound on it. A stalled economy costs one sale and never the server: the bound is
 * {@code wait-for} in the currency block, so an operator whose economy sits on a slow database can give
 * it longer.
 *
 * <p>The provider arrives as a plain object, from the seam that first asks whether the plugin is on the
 * server. That is what keeps a plugin without Treasury from ever loading a Treasury class: this currency
 * is built and answers that it is not ready, and nothing below reads a symbol nobody has.
 */
public final class TreasuryCurrency implements Currency {

    /** The service Treasury registers, named as text so nothing loads it before the plugin is found. */
    public static final String PROVIDER = "me.lokka30.treasury.api.economy.EconomyProvider";

    private final CurrencyId id;
    private final String named;
    private final Supplier<Optional<Object>> provider;
    private final Duration waitFor;
    private final System.Logger log;

    /**
     * @param id the id of this currency in the plugin
     * @param named the id of the currency inside Treasury. An empty one means the primary currency
     * @param provider where the Treasury service comes from, empty when the plugin is not here
     * @param waitFor how long one call may take before the plugin gives up on it
     */
    public TreasuryCurrency(
            CurrencyId id, String named, Supplier<Optional<Object>> provider, Duration waitFor, System.Logger log) {
        this.id = Objects.requireNonNull(id, "id");
        this.named = Objects.requireNonNull(named, "named").trim();
        this.provider = Objects.requireNonNull(provider, "provider");
        this.waitFor = Objects.requireNonNull(waitFor, "waitFor");
        this.log = Objects.requireNonNull(log, "log");
        if (waitFor.isNegative() || waitFor.isZero()) {
            throw new IllegalArgumentException("the treasury currency " + id + " must wait longer than nothing");
        }
    }

    @Override
    public CurrencyId id() {
        return id;
    }

    /** Treasury is here, and it holds the currency this plugin was told to price in. */
    @Override
    public boolean isReady() {
        return currency().isPresent();
    }

    /** An account of Treasury is a row under a player id, so a player who is away is paid like anybody. */
    @Override
    public boolean paysOffline() {
        return true;
    }

    /** Treasury says how many places a currency takes. One that takes none counts in whole numbers. */
    @Override
    public boolean isWhole() {
        return currency().map(currency -> currency.getPrecision() == 0).orElse(false);
    }

    @Override
    public Money balanceOf(UUID player) {
        Objects.requireNonNull(player, "player");
        try {
            Optional<me.lokka30.treasury.api.economy.currency.Currency> currency = currency();
            if (currency.isEmpty()) {
                return Money.zero(id);
            }
            Optional<PlayerAccount> account = account(player);
            if (account.isEmpty()) {
                return Money.zero(id);
            }
            me.lokka30.treasury.api.economy.currency.Currency held = currency.get();
            PlayerAccount owner = account.get();
            BigDecimal has = this.<BigDecimal>answer(subscriber -> owner.retrieveBalance(held, subscriber));
            return has == null ? Money.zero(id) : new Money(id, has);
        } catch (RuntimeException | LinkageError unreachable) {
            log.log(
                    System.Logger.Level.WARNING,
                    "The " + id + " currency could not be read from Treasury.",
                    unreachable);
            return Money.zero(id);
        }
    }

    @Override
    public boolean take(UUID player, Money amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(amount, "amount");
        return move(player, amount, true);
    }

    @Override
    public boolean give(UUID player, Money amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(amount, "amount");
        return move(player, amount, false);
    }

    /**
     * Move money one way.
     *
     * <p>What is taken is read first. Treasury lets a balance go under nothing, and a plugin that took
     * more than a player has would be lending money it never agreed to lend.
     */
    private boolean move(UUID player, Money amount, boolean away) {
        try {
            Optional<me.lokka30.treasury.api.economy.currency.Currency> currency = currency();
            if (currency.isEmpty()) {
                return false;
            }
            Optional<PlayerAccount> account = account(player);
            if (account.isEmpty()) {
                return false;
            }
            me.lokka30.treasury.api.economy.currency.Currency held = currency.get();
            PlayerAccount owner = account.get();
            if (away) {
                BigDecimal has = this.<BigDecimal>answer(subscriber -> owner.retrieveBalance(held, subscriber));
                if (has == null || has.compareTo(amount.amount()) < 0) {
                    return false;
                }
            }
            EconomyTransactionInitiator<?> asked =
                    EconomyTransactionInitiator.createInitiator(EconomyTransactionInitiator.Type.PLAYER, player);
            if (away) {
                this.<BigDecimal>answer(subscriber -> owner.withdrawBalance(amount.amount(), asked, held, subscriber));
            } else {
                this.<BigDecimal>answer(subscriber -> owner.depositBalance(amount.amount(), asked, held, subscriber));
            }
            return true;
        } catch (RuntimeException | LinkageError unreachable) {
            return refuse("The " + id + " currency could not move money through Treasury.", unreachable);
        }
    }

    /** The account of one player, made when Treasury has none for them yet. */
    private Optional<PlayerAccount> account(UUID player) {
        try {
            Optional<EconomyProvider> found = treasury();
            if (found.isEmpty()) {
                return Optional.empty();
            }
            EconomyProvider treasury = found.get();
            Boolean known = this.<Boolean>answer(subscriber -> treasury.hasPlayerAccount(player, subscriber));
            return Optional.ofNullable(
                    Boolean.TRUE.equals(known)
                            ? this.<PlayerAccount>answer(
                                    subscriber -> treasury.retrievePlayerAccount(player, subscriber))
                            : this.<PlayerAccount>answer(
                                    subscriber -> treasury.createPlayerAccount(player, subscriber)));
        } catch (RuntimeException | LinkageError unreachable) {
            log.log(
                    System.Logger.Level.WARNING,
                    "The " + id + " currency could not read a Treasury account.",
                    unreachable);
            return Optional.empty();
        }
    }

    /** The currency of Treasury this plugin prices in: the one the file names, or the primary one. */
    private Optional<me.lokka30.treasury.api.economy.currency.Currency> currency() {
        try {
            Optional<EconomyProvider> found = treasury();
            if (found.isEmpty()) {
                return Optional.empty();
            }
            EconomyProvider treasury = found.get();
            return named.isEmpty() ? Optional.of(treasury.getPrimaryCurrency()) : treasury.findCurrency(named);
        } catch (RuntimeException | LinkageError unreachable) {
            log.log(
                    System.Logger.Level.WARNING,
                    "The " + id + " currency could not be read from Treasury.",
                    unreachable);
            return Optional.empty();
        }
    }

    /**
     * The Treasury service, as the type it really is.
     *
     * <p>Every method of this class asks this one first and gives up on an empty answer before it names
     * a Treasury type. That is not a style: a plugin without the plugin runs these methods whenever a menu
     * lists a currency, and the machine reads the names in a line the moment it runs the line, whether
     * the line does anything or not. A lambda over a Treasury type is read the same way, which is why
     * there are none here.
     */
    private Optional<EconomyProvider> treasury() {
        Optional<Object> found = provider.get();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        return found.filter(EconomyProvider.class::isInstance).map(EconomyProvider.class::cast);
    }

    /**
     * Ask Treasury one thing and wait for the answer.
     *
     * <p>Treasury answers a subscriber rather than returning, so the call is turned into a future and the
     * future is waited on with a bound. Nothing here runs on a thread the server needs.
     */
    private <T> T answer(Consumer<EconomySubscriber<T>> call) {
        CompletableFuture<T> asked = EconomySubscriber.asFuture(call);
        try {
            return asked.get(waitFor.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("the wait for Treasury was interrupted", stopped);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException unanswered) {
            throw new IllegalStateException("Treasury did not answer in " + waitFor, unanswered);
        }
    }

    private boolean refuse(String what, Throwable why) {
        log.log(System.Logger.Level.WARNING, what, why);
        return false;
    }
}
