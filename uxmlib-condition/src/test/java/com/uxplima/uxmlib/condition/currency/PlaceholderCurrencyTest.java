package com.uxplima.uxmlib.condition.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PlaceholderCurrencyTest {

    private static final CurrencyId TOKENS = new CurrencyId("tokens");
    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final Map<UUID, String> balances = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final List<String> console = new ArrayList<>();

    private PlaceholderCurrency tokens() {
        CurrencySpec.OfPlaceholder spec = new CurrencySpec.OfPlaceholder(
                TOKENS, 1, "%tokens%", "tokens take {player} {amount}", "tokens give {uuid} {amount}", ",", true, true);
        PlaceholderCurrency.Reader reader = new PlaceholderCurrency.Reader() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public Optional<String> read(UUID player, String placeholder) {
                return Optional.ofNullable(balances.get(player));
            }
        };
        KnownPlayers known = new KnownPlayers() {
            @Override
            public Optional<UUID> byName(String name) {
                return names.entrySet().stream()
                        .filter(entry -> entry.getValue().equals(name))
                        .map(Map.Entry::getKey)
                        .findFirst();
            }

            @Override
            public Optional<String> nameOf(UUID id) {
                return Optional.ofNullable(names.get(id));
            }
        };
        return new PlaceholderCurrency(spec, reader, line -> console.add(line), known);
    }

    @Test
    void aBalanceWithThousandsReadsAsANumberAndOneThatIsNotReadsAsZero() {
        balances.put(STEVE, "1,250");
        assertThat(tokens().balanceOf(STEVE)).isEqualTo(Money.of(TOKENS, "1250"));

        balances.put(STEVE, "n/a");
        assertThat(tokens().balanceOf(STEVE).amount().signum()).isZero();
    }

    @Test
    void takingSendsTheFilledLineOnlyWhenThePlayerHasEnough() {
        names.put(STEVE, "Steve");
        balances.put(STEVE, "100");

        assertThat(tokens().take(STEVE, Money.of(TOKENS, "40"))).isTrue();
        assertThat(console).containsExactly("tokens take Steve 40");

        balances.put(STEVE, "10");
        assertThat(tokens().take(STEVE, Money.of(TOKENS, "40"))).isFalse();
        assertThat(console).hasSize(1);
    }

    @Test
    void aNameNoAccountCouldHaveNeverReachesTheConsole() {
        names.put(STEVE, "a b");
        balances.put(STEVE, "100");

        assertThat(tokens().take(STEVE, Money.of(TOKENS, "5"))).isFalse();
        assertThat(console).isEmpty();
    }

    @Test
    void givingNamesThePlayerByIdWhenTheLineAsksForIt() {
        assertThat(tokens().give(STEVE, Money.of(TOKENS, "5"))).isTrue();
        assertThat(console).containsExactly("tokens give " + STEVE + " 5");
    }
}
