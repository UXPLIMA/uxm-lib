package com.uxplima.uxmlib.condition.currency;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.condition.Wallet;
import org.jspecify.annotations.Nullable;

/**
 * The plugin's currencies, read through the seam the library's action engine asks money questions on.
 *
 * <p>An operator writes {@code [take-money 500]} in an effects list, or gates a line of one behind a
 * balance, and the library asks this. Left unwired it asks {@link Wallet#empty()}, which reads
 * every balance as zero and refuses every take: the line is silently skipped and nothing says why. The
 * plugin already holds every currency the operator turned on, so the answer was here all along.
 *
 * <p>The library names a currency with an opaque string and means "the provider's own default" by an
 * empty one, so an empty string is {@link Currencies#preferred()}, which is the same currency a price
 * with no currency is written in. A name that is not a currency id, or an id no file names, or a bridge
 * whose plugin is missing, reads zero and refuses rather than throwing: a wrong word in a menu file must
 * not take the evaluation down with it.
 *
 * <p>The library speaks in {@code double} and the plugin counts in {@link BigDecimal}, and the conversion
 * happens here, at the edge, which is where every other adapter in the plugin puts it. A currency that
 * counts in whole numbers refuses a fraction rather than rounding one, because the plugin would otherwise
 * take money it cannot give back.
 */
public final class CurrencyWallet implements Wallet {

    private final Currencies currencies;

    public CurrencyWallet(Currencies currencies) {
        this.currencies = Objects.requireNonNull(currencies, "currencies");
    }

    @Override
    public double balance(@Nullable Player player, String currency) {
        Objects.requireNonNull(currency, "currency");
        if (player == null) {
            return 0;
        }
        return find(currency)
                .map(found -> found.balanceOf(player.getUniqueId()).amount().doubleValue())
                .orElse(0d);
    }

    @Override
    public boolean withdraw(@Nullable Player player, String currency, double amount) {
        return move(player, currency, amount, Currency::take);
    }

    @Override
    public boolean deposit(@Nullable Player player, String currency, double amount) {
        return move(player, currency, amount, Currency::give);
    }

    private boolean move(@Nullable Player player, String currency, double amount, Movement movement) {
        Objects.requireNonNull(currency, "currency");
        if (amount <= 0) {
            // The library's own contract: nothing to move is not a failure.
            return player != null;
        }
        if (player == null) {
            return false;
        }
        Optional<Currency> found = find(currency);
        if (found.isEmpty()) {
            return false;
        }
        Currency using = found.get();
        Optional<Money> sum = amountIn(using, amount);
        return sum.isPresent() && movement.move(using, player.getUniqueId(), sum.get());
    }

    /**
     * The amount as this currency counts, or nothing when it cannot count it.
     *
     * <p>A currency of whole numbers refuses a fraction. Rounding one down would take less than the
     * operator wrote and rounding up would take more, and either is the plugin deciding what a line meant.
     */
    private static Optional<Money> amountIn(Currency currency, double amount) {
        BigDecimal sum = BigDecimal.valueOf(amount);
        if (currency.isWhole() && sum.stripTrailingZeros().scale() > 0) {
            return Optional.empty();
        }
        return Optional.of(new Money(currency.id(), sum));
    }

    /** The currency under that name, when the name is one and the currency is ready. */
    private Optional<Currency> find(String currency) {
        if (currency.isEmpty()) {
            Currency preferred = currencies.preferred();
            return preferred.isReady() ? Optional.of(preferred) : Optional.empty();
        }
        CurrencyId id;
        try {
            id = new CurrencyId(currency);
        } catch (IllegalArgumentException notAnId) {
            return Optional.empty();
        }
        return currencies.byId(id);
    }

    /** Taking and paying differ by one method, and the refusal around them is the same. */
    @FunctionalInterface
    private interface Movement {
        boolean move(Currency currency, java.util.UUID player, Money amount);
    }
}
