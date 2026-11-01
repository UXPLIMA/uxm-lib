package com.uxplima.uxmlib.condition.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.uxplima.uxmlib.config.HoconConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MoneyTextTest {

    private static final CurrencyId COINS = new CurrencyId("coins");

    @TempDir
    Path folder;

    private MoneyText read(String hocon) throws IOException {
        Path file = folder.resolve("currencies.conf");
        Files.writeString(file, hocon);
        return MoneyText.read(HoconConfig.load(file));
    }

    @Test
    void aCurrencyWrittenWithCapitalsStillFindsItsDisplay() throws IOException {
        MoneyText text = read("""
                currencies { Coins { type = "vault", display { format = "${amount}" } } }
                """);

        assertThat(text.of(Money.of(COINS, "5"))).isEqualTo("$5");
    }

    @Test
    void exactlyOneTakesTheSingularEvenWhenDecimalsAreKept() throws IOException {
        MoneyText text = read("""
                currencies { coins { display {
                  format = "{amount} coins", format-one = "{amount} coin", decimals = 2, trim = false
                } } }
                """);

        assertThat(text.of(Money.of(COINS, "1"))).isEqualTo("1.00 coin");
        assertThat(text.of(Money.of(COINS, "2"))).isEqualTo("2.00 coins");
    }

    @Test
    void anAmountThatRoundsUpToTheNextThousandTakesTheNextSuffix() throws IOException {
        MoneyText text = read("""
                currencies { coins { display { format = "{amount}", short = true, short-suffixes = ["k", "M"] } } }
                """);

        assertThat(text.of(Money.of(COINS, "999999"))).isEqualTo("1M");
    }

    @Test
    void aFormatThatNamesALineReadsThatLineInTheReadersLanguage() throws IOException {
        MoneyText text = read("""
                currencies { coins { display {
                  format = "@currency.coins", format-one = "@currency.coin", short = true, short-suffixes = ["@suffix.k"]
                } } }
                """);
        java.util.Map<String, String> turkish = java.util.Map.of(
                "@currency.coins", "{amount} jeton", "@currency.coin", "{amount} jeton", "@suffix.k", "B");

        assertThat(text.of(Money.of(COINS, "5"), line -> turkish.getOrDefault(line, line)))
                .isEqualTo("5 jeton");
        assertThat(text.of(Money.of(COINS, "1"), line -> turkish.getOrDefault(line, line)))
                .isEqualTo("1 jeton");
        assertThat(text.of(Money.of(COINS, "2500"), line -> turkish.getOrDefault(line, line)))
                .isEqualTo("2.5B jeton");
        assertThat(text.of(Money.of(COINS, "5")))
                .as("no reader: the value as written")
                .isEqualTo("@currency.coins");
    }
}
