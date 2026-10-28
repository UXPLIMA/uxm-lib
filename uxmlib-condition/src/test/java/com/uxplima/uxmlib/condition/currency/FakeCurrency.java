package com.uxplima.uxmlib.condition.currency;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** A currency held in memory, for the tests of what sits on top of currencies. */
final class FakeCurrency implements Currency {

    private final CurrencyId id;
    private final boolean ready;
    private final boolean whole;
    final Map<UUID, BigDecimal> balances = new ConcurrentHashMap<>();

    FakeCurrency(String id, boolean ready, boolean whole) {
        this.id = new CurrencyId(id);
        this.ready = ready;
        this.whole = whole;
    }

    @Override
    public CurrencyId id() {
        return id;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public boolean paysOffline() {
        return true;
    }

    @Override
    public boolean isWhole() {
        return whole;
    }

    @Override
    public Money balanceOf(UUID player) {
        return new Money(id, balances.getOrDefault(player, BigDecimal.ZERO));
    }

    @Override
    public boolean take(UUID player, Money amount) {
        BigDecimal now = balances.getOrDefault(player, BigDecimal.ZERO);
        if (now.compareTo(amount.amount()) < 0) {
            return false;
        }
        balances.put(player, now.subtract(amount.amount()));
        return true;
    }

    @Override
    public boolean give(UUID player, Money amount) {
        balances.merge(player, amount.amount(), BigDecimal::add);
        return true;
    }
}
