package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A {@code [sound]} line says a key, a volume and a pitch, and a word more or a volume below nothing is refused when
 * the file is read.
 *
 * <p>The parser already refused a volume that is not a number, so a typo was caught at load. A fourth word was
 * dropped without a word, and a volume of {@code -1} was sent to the client, which plays nothing, so the line looked
 * as though it worked. Both are the same kind of typo and are refused the same way.
 */
class ASoundLineIsReadWholeTest {

    @Test
    @DisplayName("a word after the pitch is refused and the line is named")
    void aWordAfterThePitchIsRefused() {
        assertThatThrownBy(() -> ActionList.parse(List.of("[sound] block.note_block.bell 1 1 loud")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("block.note_block.bell 1 1 loud");
    }

    @Test
    @DisplayName("a volume or a pitch below nothing is refused")
    void aNegativeVolumeOrPitchIsRefused() {
        assertThatThrownBy(() -> ActionList.parse(List.of("[sound] block.note_block.bell -1 1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("volume");
        assertThatThrownBy(() -> ActionList.parse(List.of("[sound] block.note_block.bell 1 -0.5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pitch");
    }

    @Test
    @DisplayName("a key alone, a key and a volume, and all three still read")
    void theWrittenShapesStillRead() {
        assertThatCode(() -> ActionList.parse(List.of(
                        "[sound] block.note_block.bell",
                        "[sound] block.note_block.bell 0.5",
                        "[sound] ENTITY_PLAYER_LEVELUP 1 1.2",
                        "[sound] {delay=20} ui.button.click 0 2")))
                .doesNotThrowAnyException();
    }
}
