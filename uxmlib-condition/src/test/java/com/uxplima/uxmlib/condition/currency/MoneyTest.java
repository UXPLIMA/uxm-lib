package com.uxplima.uxmlib.condition.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MoneyTest {

    private static final CurrencyId COINS = new CurrencyId("coins");
    private static final CurrencyId GEMS = new CurrencyId("gems");

    @Test
    void equalAmountsAreEqualWhateverTheScale() {
        assertThat(Money.of(COINS, "10.50")).isEqualTo(Money.of(COINS, "10.5"));
        assertThat(Money.of(COINS, "100").amount().toPlainString()).isEqualTo("100");
        assertThat(Money.of(COINS, "10.50").hashCode())
                .isEqualTo(Money.of(COINS, "10.5").hashCode());
    }

    @Test
    void arithmeticStaysInOneCurrency() {
        Money ten = Money.of(COINS, "10");

        assertThat(ten.plus(Money.of(COINS, "2.5"))).isEqualTo(Money.of(COINS, "12.5"));
        assertThat(ten.minus(Money.of(COINS, "10"))).isEqualTo(Money.zero(COINS));
        assertThat(ten.times(3)).isEqualTo(Money.of(COINS, "30"));
        assertThatThrownBy(() -> ten.plus(Money.of(GEMS, "1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ten.minus(Money.of(COINS, "11"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPercentAndAShareRoundDown() {
        assertThat(Money.of(COINS, "99.99").percent(new BigDecimal("5"))).isEqualTo(Money.of(COINS, "4.9995"));
        assertThat(Money.of(COINS, "10").dividedBy(3)).isEqualTo(Money.of(COINS, "3.3333"));
        assertThatThrownBy(() -> Money.of(COINS, "10").dividedBy(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moneyIsNeverNegative() {
        assertThatThrownBy(() -> Money.of(COINS, "-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(COINS, "1").times(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparisonsReadAsWritten() {
        Money five = Money.of(COINS, "5");

        assertThat(five.isAtLeast(Money.of(COINS, "5.00"))).isTrue();
        assertThat(five.isMoreThan(Money.of(COINS, "5"))).isFalse();
        assertThat(Money.zero(COINS).isPositive()).isFalse();
    }

    @Test
    void currencyIdsAreLowerCase() {
        assertThat(new CurrencyId("Coins")).isEqualTo(COINS);
        assertThatThrownBy(() -> new CurrencyId("two words")).isInstanceOf(IllegalArgumentException.class);
    }
}
