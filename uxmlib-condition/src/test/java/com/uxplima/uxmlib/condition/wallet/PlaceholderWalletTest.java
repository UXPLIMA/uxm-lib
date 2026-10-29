package com.uxplima.uxmlib.condition.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PlaceholderWalletTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final Map<UUID, String> balances = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final List<String> console = new ArrayList<>();

    private PlaceholderWallet wallet(PlaceholderWallet.Pool pool) {
        return new PlaceholderWallet(
                Map.of("tokens", pool),
                (player, placeholder) -> Optional.ofNullable(balances.get(player)),
                console::add,
                player -> Optional.ofNullable(names.get(player)));
    }

    private PlaceholderWallet tokens() {
        return wallet(new PlaceholderWallet.Pool("%tokens%", "tokens take {player} {amount}", ",")
                .paying("tokens give {uuid} {amount}"));
    }

    @Test
    void anOfflinePlayerIsReadAndTakenFromByIdWithEveryDigitOfTheSum() {
        balances.put(STEVE, "1,250.75");
        names.put(STEVE, "Steve");

        assertThat(tokens().balanceExact(STEVE, "tokens")).isEqualByComparingTo("1250.75");
        assertThat(tokens().withdrawExact(STEVE, "tokens", new BigDecimal("0.10")))
                .isTrue();
        assertThat(console).containsExactly("tokens take Steve 0.1");
    }

    @Test
    void aTakeIsRefusedWithoutALineWhenThePlayerCannotAffordItOrHasNoName() {
        balances.put(STEVE, "5");

        assertThat(tokens().withdrawExact(STEVE, "tokens", BigDecimal.TEN)).isFalse();
        assertThat(tokens().withdrawExact(STEVE, "tokens", BigDecimal.ONE)).isFalse();
        assertThat(console).isEmpty();
    }

    @Test
    void aPaymentNeedsNoNameWhenTheLineTakesTheIdAndIsRefusedWhenThePoolHasNoGive() {
        assertThat(tokens().depositExact(STEVE, "tokens", new BigDecimal("3"))).isTrue();
        assertThat(console).containsExactly("tokens give " + STEVE + " 3");

        PlaceholderWallet takeOnly = wallet(PlaceholderWallet.Pool.of("%tokens%", "tokens take {player} {amount}"));
        assertThat(takeOnly.depositExact(STEVE, "tokens", BigDecimal.ONE)).isFalse();
    }

    @Test
    void anUnknownPoolReadsZeroAndRefuses() {
        balances.put(STEVE, "100");

        assertThat(tokens().reaches("gems")).isFalse();
        assertThat(tokens().balanceExact(STEVE, "gems")).isZero();
        assertThat(tokens().withdrawExact(STEVE, "gems", BigDecimal.ONE)).isFalse();
        assertThat(tokens().depositExact(STEVE, "gems", BigDecimal.ONE)).isFalse();
        assertThat(console).isEmpty();
    }
}
