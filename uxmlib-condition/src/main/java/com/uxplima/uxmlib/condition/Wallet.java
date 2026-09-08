package com.uxplima.uxmlib.condition;

import java.util.Objects;

import org.bukkit.entity.Player;

import org.jspecify.annotations.Nullable;

/**
 * The seam through which a {@link MoneyCondition} reads a balance, a {@code [take-money]} action spends one
 * and a plugin that owes a player money pays it. It is the money mirror of {@link OperandResolver}: a plain contract this module owns, never a
 * dependency on the integration module.
 *
 * <p>A consumer that has an economy present passes an adapter over its own bridge (uxmLib ships {@code
 * EconomyBridge} in {@code uxmlib-integration}, and a two-line lambda adapts it to this shape); a consumer
 * without one passes {@link #empty()}; a test passes a fake map. Keeping the contract here means the
 * condition module never reaches "upward" into integration.
 *
 * <p>The currency is an opaque string the implementation names its own pools with, and an empty string means
 * "the provider's own default currency". This module fixes no currency, no price and no conversion: those are
 * the game a plugin plays, not the mechanism this engine is.
 *
 * <p>Reads and writes may block on the backing economy plugin, so route them off the main thread. The {@code
 * [take-money]} action declares itself sync for that reason: the driver decides the lane, not the engine.
 */
public interface Wallet {

    /**
     * The subject's balance in {@code currency}. A {@code null} player, an unknown player or an unknown
     * currency reads zero rather than throwing, so a condition never fails the whole evaluation over an
     * absent subject.
     */
    double balance(@Nullable Player player, String currency);

    /**
     * Take {@code amount} of {@code currency} from the subject and report whether the whole amount was taken.
     *
     * <p>An implementation must be all or nothing. It never takes a part of the amount and then reports
     * failure, because a caller that reads {@code false} is entitled to assume nothing was spent. A
     * non-positive amount takes nothing and succeeds.
     */
    boolean withdraw(@Nullable Player player, String currency, double amount);

    /**
     * Pay {@code amount} of {@code currency} to the subject and report whether the whole amount arrived.
     *
     * <p>The mirror of {@link #withdraw}, and it carries the same promise the other way round: an
     * implementation is all or nothing, so a caller that reads {@code false} is entitled to assume nothing
     * arrived and may pay again through another road without paying twice. A non-positive amount pays
     * nothing and succeeds.
     *
     * <p>It is a {@code default} that refuses, so a wallet written before this method existed keeps
     * compiling and keeps its own meaning: it can read a balance and it can take, and it says plainly that
     * it cannot pay out. A caller that is told {@code false} has been told the truth, which is the whole of
     * the contract. Every backend this library ships overrides it.
     *
     * <p>What is paid, to whom, and whether it is a wage, a refund or a prize is the game a plugin plays.
     * This is the mechanism and nothing else.
     */
    default boolean deposit(@Nullable Player player, String currency, double amount) {
        Objects.requireNonNull(currency, "currency");
        return false;
    }

    /**
     * The empty wallet: every balance reads zero, and every withdrawal and every payment fails. It is the default on a request
     * and on an action context, so a consumer that wires no economy still parses and runs a list without
     * null-checking, and a {@code [take-money]} in that list fails loudly rather than silently succeeding.
     */
    static Wallet empty() {
        return new Wallet() {

            @Override
            public double balance(@Nullable Player player, String currency) {
                Objects.requireNonNull(currency, "currency");
                return 0;
            }

            @Override
            public boolean withdraw(@Nullable Player player, String currency, double amount) {
                Objects.requireNonNull(currency, "currency");
                return false;
            }

            @Override
            public boolean deposit(@Nullable Player player, String currency, double amount) {
                Objects.requireNonNull(currency, "currency");
                return false;
            }
        };
    }
}
