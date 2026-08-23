package com.uxplima.uxmlib.packet.scoreboard;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;

class ScoreboardNumberFormatTest {

    @Test
    void exposesDefaultBlankAndFixedFormatsWithoutServerTypes() {
        assertThat(ScoreboardNumberFormat.defaultFormat()).isInstanceOf(ScoreboardNumberFormat.Default.class);
        assertThat(ScoreboardNumberFormat.blank()).isInstanceOf(ScoreboardNumberFormat.Blank.class);
        assertThat(ScoreboardNumberFormat.fixed(Component.text("coins")))
                .isEqualTo(new ScoreboardNumberFormat.Fixed(Component.text("coins")));
    }
}
