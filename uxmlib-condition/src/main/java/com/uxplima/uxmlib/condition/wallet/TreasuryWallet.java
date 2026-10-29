package com.uxplima.uxmlib.condition.wallet;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.condition.Wallet;
import me.lokka30.treasury.api.economy.EconomyProvider;
import me.lokka30.treasury.api.economy.account.PlayerAccount;
import me.lokka30.treasury.api.economy.currency.Currency;
import me.lokka30.treasury.api.economy.response.EconomySubscriber;
import me.lokka30.treasury.api.economy.transaction.EconomyTransactionInitiator;
import org.jspecify.annotations.Nullable;

/**
 * A balance Treasury holds.
 *
 * <p>Every other economy here is reached through an {@link EconomyBinding}: the name of a class, the name
 * of two methods, and what they take. Treasury fits in no such description. It returns nothing and calls
 * back a subscriber instead, an account is a hop of its own before the balance is, and moving money needs
 * a currency object and an initiator that says who asked. So this economy is written out.
 *
 * <p>The currency name is the id of a currency inside Treasury, and the empty name is its primary one. The
 * {@code Exact} methods take a player id and a {@link BigDecimal}, so a plugin can pay a player who is
 * offline: an account of Treasury is a row under a player id. The service and each currency are looked up
 * once and kept; an account is asked for on every call, because Treasury may close one in between.
 *
 * <p>Every call waits for its answer with a bound on it, which is {@code waitFor}. A stalled economy costs
 * one purchase and never the server, and an operator whose economy sits on a slow database can give it
 * longer.
 *
 * <p>Nothing outside {@code TreasuryCalls} names a Treasury type, and that inner class is only ever
 * reached once the provider has been found, which happens only once the plugin manager has said Treasury
 * is here. A server without the plugin therefore loads no Treasury class at all: the balance reads zero,
 * every take is refused, and nothing is logged.
 *
 * <p>A take reads the balance first and refuses the whole cost before it moves anything, because Treasury
 * lets a balance go under nothing and a wallet that took more than a player has would be lending money it
 * never agreed to lend. Only a payment opens an account. One honest gap is left, and it is the reason
 * {@code waitFor} is an operator's choice: a request that has left and is not answered inside the bound may
 * still be carried out. A take then reads as not made and a payment as made, so nothing is ever paid twice,
 * and the console names the account so an operator can check it.
 */
public final class TreasuryWallet implements Wallet {

    /** The plugin name, which is the present-guard. */
    public static final String PLUGIN = "Treasury";

    /** The service Treasury registers, named as text so nothing loads it before the plugin is found. */
    public static final String PROVIDER = "me.lokka30.treasury.api.economy.EconomyProvider";

    private final Supplier<Optional<Object>> provider;
    private final Duration waitFor;
    private final System.Logger log;
    /** The currencies of Treasury found so far, by the name asked for, as plain objects. */
    private final Map<String, Object> currencies = new ConcurrentHashMap<>();

    private volatile @Nullable Object handle;
    private volatile boolean readable = true;

    /**
     * @param provider where the Treasury service comes from, empty when the plugin is not here
     * @param waitFor how long one call may take before this gives up on it
     */
    public TreasuryWallet(Supplier<Optional<Object>> provider, Duration waitFor, System.Logger log) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.waitFor = Objects.requireNonNull(waitFor, "waitFor");
        this.log = Objects.requireNonNull(log, "log");
        if (waitFor.isNegative() || waitFor.isZero()) {
            throw new IllegalArgumentException("a treasury wallet must wait longer than nothing");
        }
    }

    /** The wallet this server's Treasury answers for, asking the service manager for the provider. */
    public static TreasuryWallet ofServer(Duration waitFor, System.Logger log) {
        Objects.requireNonNull(log, "log");
        EconomyProviders providers = new ServerEconomyProviders(log);
        return new TreasuryWallet(() -> providers.service(PLUGIN, PROVIDER), waitFor, log);
    }

    /** Whether Treasury is here and holds {@code currency}. */
    public boolean reaches(String currency) {
        Objects.requireNonNull(currency, "currency");
        return currency(currency) != null;
    }

    /** Whether {@code currency} takes no decimal places in Treasury. False when Treasury does not hold it. */
    public boolean countsWhole(String currency) {
        Objects.requireNonNull(currency, "currency");
        Object found = currency(currency);
        try {
            return found != null && TreasuryCalls.whole(found);
        } catch (RuntimeException | LinkageError unreachable) {
            warnOnce("Treasury could not say how the " + named(currency) + " counts.", unreachable);
            return false;
        }
    }

    /** The named currency of Treasury, or its primary one when the name is empty. Zero if neither is here. */
    @Override
    public double balance(@Nullable Player player, String currency) {
        Objects.requireNonNull(currency, "currency");
        return player == null ? 0 : balanceExact(player.getUniqueId(), currency).doubleValue();
    }

    /**
     * The balance as Treasury holds it, which may be under zero. A player with no account, and a currency
     * Treasury does not hold, read zero; reading never opens an account.
     */
    public BigDecimal balanceExact(UUID player, String currency) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(currency, "currency");
        Object treasury = treasury();
        Object held = currency(currency);
        if (treasury == null || held == null) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal has = TreasuryCalls.balance(treasury, held, player, waitFor);
            readable = true;
            return has == null ? BigDecimal.ZERO : has;
        } catch (RuntimeException | LinkageError unreachable) {
            warnOnce("Treasury could not read the " + named(currency) + " for " + player + ".", unreachable);
            return BigDecimal.ZERO;
        }
    }

    @Override
    public boolean withdraw(@Nullable Player player, String currency, double amount) {
        Objects.requireNonNull(currency, "currency");
        return amount <= 0
                || (player != null && withdrawExact(player.getUniqueId(), currency, BigDecimal.valueOf(amount)));
    }

    /** Take the whole amount, or take nothing and say so. */
    public boolean withdrawExact(UUID player, String currency, BigDecimal amount) {
        return move(player, currency, amount, true);
    }

    @Override
    public boolean deposit(@Nullable Player player, String currency, double amount) {
        Objects.requireNonNull(currency, "currency");
        return amount <= 0
                || (player != null && depositExact(player.getUniqueId(), currency, BigDecimal.valueOf(amount)));
    }

    /** Pay the whole amount in, opening the player's account when they have none. */
    public boolean depositExact(UUID player, String currency, BigDecimal amount) {
        return move(player, currency, amount, false);
    }

    private boolean move(UUID player, String currency, BigDecimal amount, boolean away) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            return true;
        }
        Object treasury = treasury();
        Object held = currency(currency);
        if (treasury == null || held == null) {
            return false;
        }
        try {
            return TreasuryCalls.move(treasury, held, player, amount, away, waitFor);
        } catch (Unanswered unanswered) {
            log.log(
                    System.Logger.Level.WARNING,
                    "Treasury did not say whether it " + (away ? "took " : "paid ") + amount.toPlainString()
                            + " of the " + named(currency) + (away ? " from " : " to ") + player
                            + ". Check that account.",
                    unanswered);
            return !away;
        } catch (RuntimeException | LinkageError unreachable) {
            log.log(
                    System.Logger.Level.WARNING,
                    "Treasury could not " + (away ? "take" : "pay") + " the " + named(currency) + ", so nothing moved.",
                    unreachable);
            return false;
        }
    }

    /**
     * The Treasury service, as a plain object, resolved on first use and kept.
     *
     * <p>Every method asks this one first and gives up on a {@code null} answer before it reaches anything
     * that names a Treasury type. That is not a style: it is what keeps a server without the plugin from
     * ever loading one of its classes.
     */
    private @Nullable Object treasury() {
        Object known = handle;
        if (known != null) {
            return known;
        }
        Object found = provider.get().orElse(null);
        if (found != null) {
            handle = found;
        }
        return found;
    }

    /** The currency of Treasury under {@code named}, found once and kept; null while Treasury holds none. */
    private @Nullable Object currency(String named) {
        String key = named.strip();
        Object known = currencies.get(key);
        if (known != null) {
            return known;
        }
        Object treasury = treasury();
        if (treasury == null) {
            return null;
        }
        try {
            Object found = TreasuryCalls.currency(treasury, key);
            if (found != null) {
                currencies.put(key, found);
            }
            return found;
        } catch (RuntimeException | LinkageError unreachable) {
            warnOnce("Treasury could not find the " + named(named) + ".", unreachable);
            return null;
        }
    }

    private static String named(String currency) {
        return currency.isBlank() ? "primary currency" : "currency " + currency.strip();
    }

    /**
     * Say once that Treasury cannot be read. A menu asks for a balance on every draw, so a warning per ask
     * would bury the console; the next balance read that works lets it speak again.
     */
    private void warnOnce(String what, Throwable why) {
        if (readable) {
            readable = false;
            log.log(System.Logger.Level.WARNING, what, why);
        }
    }

    /** Treasury got the request and gave no answer in time, so whether it ran is unknown. */
    private static final class Unanswered extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        Unanswered(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Everything that names a Treasury type.
     *
     * <p>It is a class of its own because the machine reads the names in a method the moment it runs that
     * method, whether the line that names them does anything or not. A guard in the same class as the call
     * is not a guard. This class is loaded on its first call, and its first call is behind the guard.
     */
    private static final class TreasuryCalls {

        private TreasuryCalls() {}

        /** The currency the caller named, or the primary one; null when Treasury holds no such currency. */
        static @Nullable Object currency(Object provider, String named) {
            if (!(provider instanceof EconomyProvider treasury)) {
                return null;
            }
            return named.isEmpty()
                    ? treasury.getPrimaryCurrency()
                    : treasury.findCurrency(named).orElse(null);
        }

        static boolean whole(Object currency) {
            return currency instanceof Currency held && held.getPrecision() == 0;
        }

        /** The balance, or {@code null} when the player has no account. */
        static @Nullable BigDecimal balance(Object provider, Object currency, UUID who, Duration waitFor) {
            if (!(provider instanceof EconomyProvider treasury) || !(currency instanceof Currency held)) {
                return null;
            }
            PlayerAccount account = account(treasury, who, false, waitFor);
            return account == null ? null : answer(waitFor, subscriber -> account.retrieveBalance(held, subscriber));
        }

        /** Move the whole amount one way, or move nothing and say so. */
        static boolean move(
                Object provider, Object currency, UUID who, BigDecimal amount, boolean away, Duration waitFor) {
            if (!(provider instanceof EconomyProvider treasury) || !(currency instanceof Currency held)) {
                return false;
            }
            // A take needs money that is there, so only a payment opens an account.
            PlayerAccount account = account(treasury, who, !away, waitFor);
            if (account == null) {
                return false;
            }
            if (away) {
                BigDecimal has = answer(waitFor, subscriber -> account.retrieveBalance(held, subscriber));
                if (has == null || has.compareTo(amount) < 0) {
                    return false;
                }
            }
            EconomyTransactionInitiator<?> asked =
                    EconomyTransactionInitiator.createInitiator(EconomyTransactionInitiator.Type.PLAYER, who);
            // The answer is the balance that is left. Nothing here reads it: what matters is that the call
            // answered at all, and one that did not throws out of the wait.
            TreasuryCalls.<BigDecimal>answer(waitFor, subscriber -> {
                if (away) {
                    account.withdrawBalance(amount, asked, held, subscriber);
                } else {
                    account.depositBalance(amount, asked, held, subscriber);
                }
            });
            return true;
        }

        /** The account of one player; made when Treasury has none and {@code create} asks for one. */
        private static @Nullable PlayerAccount account(
                EconomyProvider treasury, UUID who, boolean create, Duration waitFor) {
            Boolean known = answer(waitFor, subscriber -> treasury.hasPlayerAccount(who, subscriber));
            if (Boolean.TRUE.equals(known)) {
                return answer(waitFor, subscriber -> treasury.retrievePlayerAccount(who, subscriber));
            }
            return create ? answer(waitFor, subscriber -> treasury.createPlayerAccount(who, subscriber)) : null;
        }

        /**
         * Ask Treasury one thing and wait for the answer.
         *
         * <p>Treasury answers a subscriber rather than returning, so the call is turned into a future and
         * the future is waited on with a bound. A refusal throws, and an economy that never answers throws
         * {@link Unanswered}, since that request may still be carried out.
         */
        private static <T> @Nullable T answer(Duration waitFor, Consumer<EconomySubscriber<T>> call) {
            CompletableFuture<T> asked = EconomySubscriber.asFuture(call);
            try {
                return asked.get(waitFor.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("the wait for Treasury was interrupted", stopped);
            } catch (ExecutionException refused) {
                throw new IllegalStateException("Treasury refused", refused.getCause());
            } catch (TimeoutException late) {
                throw new Unanswered("Treasury did not answer in " + waitFor, late);
            }
        }
    }
}
