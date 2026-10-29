package com.uxplima.uxmlib.condition.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import me.lokka30.treasury.api.economy.EconomyProvider;
import me.lokka30.treasury.api.economy.account.PlayerAccount;
import me.lokka30.treasury.api.economy.currency.Currency;
import me.lokka30.treasury.api.economy.response.EconomySubscriber;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TreasuryWalletTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final System.Logger LOG = System.getLogger("test");

    private final Map<UUID, BigDecimal> accounts = new ConcurrentHashMap<>();
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final Currency coins = currency("coins", 2);
    private final Currency points = currency("points", 0);
    private volatile boolean silent;

    private TreasuryWallet wallet() {
        EconomyProvider treasury = proxy(EconomyProvider.class, (method, args) -> {
            calls.add(method);
            return switch (method) {
                case "getPrimaryCurrency" -> coins;
                case "findCurrency" ->
                    Optional.ofNullable(args[0].equals("coins") ? coins : args[0].equals("points") ? points : null);
                case "hasPlayerAccount" -> answer(args, accounts.containsKey((UUID) args[0]));
                case "retrievePlayerAccount" -> answer(args, account((UUID) args[0]));
                case "createPlayerAccount" -> {
                    accounts.put((UUID) args[0], BigDecimal.ZERO);
                    yield answer(args, account((UUID) args[0]));
                }
                default -> throw new UnsupportedOperationException(method);
            };
        });
        return new TreasuryWallet(() -> Optional.of(treasury), Duration.ofMillis(200), LOG);
    }

    private PlayerAccount account(UUID who) {
        return proxy(PlayerAccount.class, (method, args) -> {
            calls.add(method);
            switch (method) {
                case "retrieveBalance" -> answer(args, accounts.get(who));
                case "withdrawBalance", "depositBalance" -> {
                    if (silent) {
                        return null;
                    }
                    BigDecimal amount = (BigDecimal) args[0];
                    accounts.merge(who, method.equals("withdrawBalance") ? amount.negate() : amount, BigDecimal::add);
                    answer(args, accounts.get(who));
                }
                default -> throw new UnsupportedOperationException(method);
            }
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Object answer(Object[] args, @Nullable Object value) {
        ((EconomySubscriber<Object>) args[args.length - 1]).succeed(value);
        return null;
    }

    private static Currency currency(String id, int precision) {
        return proxy(Currency.class, (method, args) -> switch (method) {
            case "getPrecision" -> precision;
            case "getIdentifier", "toString" -> id;
            case "hashCode" -> id.hashCode();
            case "equals" -> false;
            default -> throw new UnsupportedOperationException(method);
        });
    }

    @FunctionalInterface
    private interface Handler {
        @Nullable Object handle(String method, Object[] args);
    }

    private static <T> T proxy(Class<T> type, Handler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (self, method, args) -> handler.handle(method.getName(), args == null ? new Object[0] : args)));
    }

    @Test
    void readingAPlayerWithNoAccountReadsZeroAndOpensNone() {
        TreasuryWallet wallet = wallet();

        assertThat(wallet.balanceExact(STEVE, "")).isZero();
        assertThat(wallet.withdrawExact(STEVE, "", BigDecimal.ONE)).isFalse();
        assertThat(calls).doesNotContain("createPlayerAccount");
    }

    @Test
    void aPaymentOpensTheAccountAndATakeMovesOnlyWhatIsThere() {
        TreasuryWallet wallet = wallet();

        assertThat(wallet.depositExact(STEVE, "coins", new BigDecimal("10.25"))).isTrue();
        assertThat(wallet.balanceExact(STEVE, "coins")).isEqualByComparingTo("10.25");
        assertThat(wallet.withdrawExact(STEVE, "coins", new BigDecimal("20"))).isFalse();
        assertThat(wallet.withdrawExact(STEVE, "coins", new BigDecimal("10.25")))
                .isTrue();
        assertThat(accounts.get(STEVE)).isZero();
        assertThat(calls).containsOnlyOnce("createPlayerAccount").containsOnlyOnce("withdrawBalance");
    }

    @Test
    void anUnansweredPaymentReadsAsMadeAndAnUnansweredTakeAsNot() {
        TreasuryWallet wallet = wallet();
        accounts.put(STEVE, new BigDecimal("50"));
        silent = true;

        assertThat(wallet.depositExact(STEVE, "coins", BigDecimal.ONE)).isTrue();
        assertThat(wallet.withdrawExact(STEVE, "coins", BigDecimal.ONE)).isFalse();
    }

    @Test
    void eachCurrencyIsLookedUpOnceAndAnUnknownOneIsNotReached() {
        TreasuryWallet wallet = wallet();
        accounts.put(STEVE, BigDecimal.TEN);

        wallet.balanceExact(STEVE, "points");
        wallet.balanceExact(STEVE, "points");
        assertThat(wallet.countsWhole("points")).isTrue();
        assertThat(wallet.countsWhole("coins")).isFalse();
        assertThat(wallet.reaches("gems")).isFalse();
        assertThat(calls.stream().filter("findCurrency"::equals)).hasSize(3);
    }
}
