package com.uxplima.uxmlib.condition;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;

import org.jspecify.annotations.Nullable;

/**
 * A wallet held in a map that can pay as well as take, so the {@code [give-money]} action can be driven
 * without a server or an economy plugin.
 *
 * <p>Separate from {@link FakeWallet} on purpose. That one implements the two methods the interface had
 * before there was a payment, and {@code WalletTest} uses exactly that shape to prove the refusing default
 * still holds for a wallet somebody wrote against the older contract. Teaching it to pay would have taken
 * that proof away.
 */
public final class FakePayingWallet implements Wallet {

    private final Map<String, Double> balances = new HashMap<>();

    private int deposits;

    public FakePayingWallet(String currency, double balance) {
        balances.put(currency, balance);
    }

    @Override
    public double balance(@Nullable Player player, String currency) {
        return balances.getOrDefault(currency, 0.0);
    }

    @Override
    public boolean withdraw(@Nullable Player player, String currency, double amount) {
        double held = balance(player, currency);
        if (amount <= 0 || held < amount) {
            return false;
        }
        balances.put(currency, held - amount);
        return true;
    }

    @Override
    public boolean deposit(@Nullable Player player, String currency, double amount) {
        if (amount <= 0) {
            return false;
        }
        deposits++;
        balances.merge(currency, amount, Double::sum);
        return true;
    }

    /** How many payments were actually applied; a refused payment must leave this untouched. */
    public int deposits() {
        return deposits;
    }
}
