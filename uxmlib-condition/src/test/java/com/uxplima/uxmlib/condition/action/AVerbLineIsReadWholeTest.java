package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A particle line and a boss bar line say what they mean, and a word too many in either is refused when the file
 * is read.
 *
 * <p>Tried one line at a time on 2026-09-23: {@code [particle] HAPPY_VILLAGER 20 0.5 extra words} dropped two words
 * without a sound, and so did a bar with a fourth word before its pipe. A colour or an overlay nobody knows still
 * falls back to the default, which {@code ActionParserTest} holds as a choice: the line is kept and drawn. A refusal
 * of a particle's count also called itself a sound, because the one number reader all the verbs share said so.
 */
class AVerbLineIsReadWholeTest {

    @Test
    @DisplayName("a particle with a word after its data is refused")
    void aParticleWithAWordTooManyIsRefused() {
        assertThatThrownBy(() -> ActionList.parse(List.of("[particle] HAPPY_VILLAGER 20 0.5 extra words")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HAPPY_VILLAGER 20 0.5 extra words");
        assertThatCode(() -> ActionList.parse(List.of("[particle] DUST 20 0.5 #ff0000>#00ff00")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a boss bar with a word too many before its pipe is refused, and an unknown colour still falls back")
    void aBarWithAWordTooManyIsRefused() {
        assertThatThrownBy(() -> ActionList.parse(List.of("[bossbar] 5 RED PROGRESS loudly | x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 RED PROGRESS loudly | x");
        assertThatCode(() -> ActionList.parse(
                        List.of("[bossbar] 5 red notched_6 | x", "[bossbar] 5 | x", "[bossbar] 5 PURPLE_ISH | x")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a refusal names the verb's own field, not a sound")
    void aRefusalNamesItsOwnVerb() {
        assertThatThrownBy(() -> ActionList.parse(List.of("[particle] HAPPY_VILLAGER -5")))
                .hasMessageContaining("count")
                .hasMessageNotContaining("sound");
        assertThatThrownBy(() -> ActionList.parse(List.of("[bossbar] -5 RED PROGRESS | x")))
                .hasMessageContaining("seconds")
                .hasMessageNotContaining("sound");
    }
}
