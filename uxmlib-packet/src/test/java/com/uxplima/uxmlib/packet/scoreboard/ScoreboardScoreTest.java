package com.uxplima.uxmlib.packet.scoreboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;

class ScoreboardScoreTest {

    @Test
    void keepsStableHolderDisplayTextAndPerLineFormatSeparate() {
        ScoreboardScore score = new ScoreboardScore(
                "uxmsb",
                "line:balance",
                4,
                Component.text("Balance"),
                ScoreboardNumberFormat.fixed(Component.text("12")));

        assertThat(score.objectiveName()).isEqualTo("uxmsb");
        assertThat(score.holder()).isEqualTo("line:balance");
        assertThat(score.score()).isEqualTo(4);
        assertThat(score.displayName()).isEqualTo(Component.text("Balance"));
        assertThat(score.numberFormat()).isInstanceOf(ScoreboardNumberFormat.Fixed.class);
    }

    @Test
    void rejectsBlankProtocolIdentifiers() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> new ScoreboardScore(" ", "line", 1, Component.empty(), ScoreboardNumberFormat.blank()));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> new ScoreboardScore("board", " ", 1, Component.empty(), ScoreboardNumberFormat.blank()));
    }
}
