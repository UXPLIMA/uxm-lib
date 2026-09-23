package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A particle line and a boss bar line say what they mean, and a typo in either is refused when the file is read.
 *
 * <p>Tried one line at a time on 2026-09-23: {@code [particle] HAPPY_VILLAGER 20 0.5 extra words} dropped two words
 * without a sound, and {@code [bossbar] 5 PURPLE_ISH PROGRESS | x} drew a white bar, because an unknown colour or
 * overlay fell back to the default. A refusal of a particle's count also called itself a sound, because the one
 * number reader all the verbs share said so.
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
    @DisplayName("a boss bar colour or overlay nobody knows is refused and named")
    void anUnknownBarColourOrOverlayIsRefused() {
        assertThatThrownBy(() -> ActionList.parse(List.of("[bossbar] 5 PURPLE_ISH PROGRESS | x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PURPLE_ISH");
        assertThatThrownBy(() -> ActionList.parse(List.of("[bossbar] 5 RED STRIPES | x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("STRIPES");
        assertThatCode(() -> ActionList.parse(List.of("[bossbar] 5 red notched_6 | x", "[bossbar] 5 | x")))
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
