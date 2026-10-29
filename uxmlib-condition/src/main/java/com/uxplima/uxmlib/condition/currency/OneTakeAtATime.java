package com.uxplima.uxmlib.condition.currency;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmlib.condition.wallet.TakeLocks;

/**
 * A currency whose takes for one player run one at a time.
 *
 * <p>Most backends take in two steps, a balance read and then the take, and two takes for one player that
 * interleave would both read the old balance and both pass. Every currency {@link CurrencyBuilder} builds is
 * wrapped in this, all sharing one {@link TakeLocks}, so two currencies over one economy cannot race either.
 * {@link TakeLocks} says how a server thread and a worker wait.
 */
final class OneTakeAtATime implements Currency {

    private final Currency inner;
    private final TakeLocks takes;

    OneTakeAtATime(Currency inner, TakeLocks takes) {
        this.inner = Objects.requireNonNull(inner, "inner");
        this.takes = Objects.requireNonNull(takes, "takes");
    }

    @Override
    public CurrencyId id() {
        return inner.id();
    }

    @Override
    public boolean isReady() {
        return inner.isReady();
    }

    @Override
    public boolean paysOffline() {
        return inner.paysOffline();
    }

    @Override
    public boolean isWhole() {
        return inner.isWhole();
    }

    @Override
    public Money balanceOf(UUID player) {
        return inner.balanceOf(player);
    }

    @Override
    public boolean take(UUID player, Money amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(amount, "amount");
        return takes.holding(player, () -> inner.take(player, amount));
    }

    @Override
    public boolean give(UUID player, Money amount) {
        return inner.give(player, amount);
    }
}
