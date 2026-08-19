package com.uxplima.uxmlib.packet;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmlib.common.ServerVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The line choice, tested without a server: instantiating an adapter needs the Mojang-mapped server, but
 * deciding which one to instantiate is plain arithmetic and is exactly the part that can go wrong quietly.
 */
class ServerCompatsTest {

    @Test
    @DisplayName("the 1.21 line takes the legacy adapter")
    void legacyLineTakesTheLegacyAdapter() {
        assertThat(ServerCompats.lineOf(ServerVersion.parse("1.21"))).isEqualTo(ServerCompats.Line.MC1_21);
        assertThat(ServerCompats.lineOf(ServerVersion.parse("1.21.11"))).isEqualTo(ServerCompats.Line.MC1_21);
    }

    @Test
    @DisplayName("the year-based line takes the 26.x adapter")
    void yearBasedLineTakesTheModernAdapter() {
        assertThat(ServerCompats.lineOf(ServerVersion.parse("26.1"))).isEqualTo(ServerCompats.Line.MC26);
        assertThat(ServerCompats.lineOf(ServerVersion.parse("26.2"))).isEqualTo(ServerCompats.Line.MC26);
    }

    @Test
    @DisplayName("a line newer than any adapter falls to the newest one")
    void newerLinesFallToTheNewestAdapter() {
        assertThat(ServerCompats.lineOf(ServerVersion.parse("27.1"))).isEqualTo(ServerCompats.Line.MC26);
    }
}
