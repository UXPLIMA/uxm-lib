package com.uxplima.uxmlib.condition.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.uxplima.uxmlib.config.HoconConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CurrencyFileTest {

    @TempDir
    Path folder;

    private List<CurrencySpec> read(String hocon) throws IOException {
        Path file = folder.resolve("currencies.conf");
        Files.writeString(file, hocon);
        return CurrencyFile.read(HoconConfig.load(file), "uxmTest");
    }

    @Test
    void currenciesComeInTheirOrderThenByIdAndATurnedOffOneIsLeftOut() throws IOException {
        List<CurrencySpec> read = read("""
                currencies {
                  b { type = "experience", order = 5 }
                  a { type = "experience", order = 5 }
                  c { type = "experience", order = 1 }
                  d { type = "experience", order = 0, enabled = false }
                }
                """);

        assertThat(read).extracting(spec -> spec.id().value()).containsExactly("c", "a", "b");
    }

    @Test
    void aFileWithNothingTurnedOnOrAnUnknownTypeIsRefused() {
        assertThatThrownBy(() -> read("currencies { a { type = \"experience\", enabled = false } }"))
                .hasMessageContaining("names no currency that is turned on");
        assertThatThrownBy(() -> read("currencies { a { type = \"gems\" } }"))
                .hasMessageContaining("has an unknown type: gems");
    }

    @Test
    void aNameWrittenOnVaultIsDroppedSoItCannotReadAWorldsBalance() throws IOException {
        List<CurrencySpec> read = read("currencies { coins { type = \"vault\", currency-name = \"world_nether\" } }");

        assertThat(read)
                .singleElement()
                .isInstanceOfSatisfying(
                        CurrencySpec.OfEconomy.class,
                        economy -> assertThat(economy.pool()).isEmpty());
    }

    @Test
    void experienceCountsLevelsOrPointsAndNothingElse() throws IOException {
        assertThat(read("currencies { xp { type = \"experience\", unit = \"levels\" } }"))
                .singleElement()
                .isInstanceOfSatisfying(
                        CurrencySpec.OfExperience.class,
                        xp -> assertThat(xp.unit()).isEqualTo(ExperienceCurrency.Unit.LEVELS));
        assertThatThrownBy(() -> read("currencies { xp { type = \"experience\", unit = \"hearts\" } }"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
