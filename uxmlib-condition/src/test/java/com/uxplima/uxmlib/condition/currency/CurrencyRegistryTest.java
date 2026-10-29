package com.uxplima.uxmlib.condition.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class CurrencyRegistryTest {

    @Test
    void thePreferredCurrencyIsTheFirstThatIsReady() {
        CurrencyRegistry registry = new CurrencyRegistry(
                List.of(new FakeCurrency("off", false, false), new FakeCurrency("coins", true, false)));

        assertThat(registry.preferred().id()).isEqualTo(new CurrencyId("coins"));
        assertThat(registry.byId(new CurrencyId("off"))).isEmpty();
    }

    @Test
    void twoCurrenciesWithOneIdAreRefused() {
        assertThatThrownBy(() -> new CurrencyRegistry(
                        List.of(new FakeCurrency("coins", true, false), new FakeCurrency("coins", true, false))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anIdIsOneLowerCaseWord() {
        assertThat(new CurrencyId("Coins").value()).isEqualTo("coins");
        assertThatThrownBy(() -> new CurrencyId("two words")).isInstanceOf(IllegalArgumentException.class);
    }
}
