package com.uxplima.uxmlib.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * A key an operator misspelled is named when the shipped defaults are merged in.
 *
 * <p>Tried on 2026-09-23: a {@code config.conf} that wrote {@code cycel { tick = 5 }} loaded without a word. The
 * merge found {@code cycle} missing and added it with its shipped value, so the operator's five was lost and the
 * plugin ran on the default. A key the shipped file does not have, one or two letters away from a shipped key the
 * operator's file lacks, is that mistake, and it is named. A key of the operator's own, a reward or a currency they
 * added, is not near any shipped one and is left alone.
 */
class AMisspelledConfigKeyIsNamedTest {

    @Test
    @DisplayName("a key one or two letters from a missing shipped key is named, at any depth")
    void aNearMissIsNamed() throws Exception {
        List<String> named = ConfigTypos.suspects(
                hocon("cycel { tick = 5 }\nstorage { jdbcUrl = \"x\" }"),
                hocon("cycle { tick = 20 }\nstorage { jdbc-url = \"\" }"));

        assertThat(named).hasSize(2);
        assertThat(named).anySatisfy(line -> assertThat(line).contains("cycel", "cycle"));
        assertThat(named).anySatisfy(line -> assertThat(line).contains("jdbcUrl", "jdbc-url", "storage"));
    }

    @Test
    @DisplayName("an operator's own key, or a near key whose shipped twin is also written, is left alone")
    void anOperatorsOwnKeyIsLeftAlone() throws Exception {
        List<String> named = ConfigTypos.suspects(
                hocon("rewards { gold = 1, emerald = 2 }\ncycle { tick = 5 }\ncycles = 2"),
                hocon("rewards { gold = 1, diamond = 3 }\ncycle { tick = 20 }"));

        assertThat(named).isEmpty();
    }

    private static ConfigurationNode hocon(String text) throws Exception {
        return HoconConfigurationLoader.builder().buildAndLoadString(text);
    }
}
