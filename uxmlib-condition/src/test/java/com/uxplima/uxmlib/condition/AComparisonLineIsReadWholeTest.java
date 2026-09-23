package com.uxplima.uxmlib.condition;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A condition line whose comparison can never come true is refused when the file is read.
 *
 * <p>Tried one line at a time on 2026-09-23. {@code %player_level% => 10} read: the parser found the {@code >} and
 * compared {@code "<level> ="} with ten, which is never a number, so the line refused every player and nothing said
 * why. So did {@code %player_level% >= } with nothing on the right, {@code >= 10} with nothing on the left,
 * {@code %a% >= 1 extra}, and {@code item DIAMOND >= abc}. An ordering operator compares numbers only, so a written
 * side that is not a number and holds no placeholder makes the line false for everybody.
 */
class AComparisonLineIsReadWholeTest {

    @Test
    @DisplayName("an operator run into another operator character is refused")
    void aRunTogetherOperatorIsRefused() {
        for (String line : List.of("%player_level% => 10", "%player_level% =< 10", "%a% >> 1")) {
            assertThatThrownBy(() -> ConditionLines.read(line))
                    .as(line)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(line);
        }
    }

    @Test
    @DisplayName("an ordering operator with a written side that is not a number is refused")
    void anOrderingSideThatIsNeverANumberIsRefused() {
        for (String line : List.of(
                "%player_level% >= ", ">= 10", "%a% >= 1 extra", "%a% < ten", "item DIAMOND >= abc", "money >= lots")) {
            assertThatThrownBy(() -> ConditionLines.read(line)).as(line).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("an item line that names no item is refused")
    void anItemLineWithNoItemIsRefused() {
        assertThatThrownBy(() -> ConditionLines.read("item >= 5")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the lines an operator means still read")
    void theMeantLinesStillRead() {
        assertThatCode(() -> List.of(
                                "%player_level% >= 10",
                                "%player_level%>=10",
                                "%a% >= %b%",
                                "%a% >= -2.5",
                                "%player_suffix% == ",
                                "%player_name% != Sirac",
                                "%rank% ?= vip",
                                "%rank% * vip*",
                                "%rank% || vip|mvp",
                                "money >= 100",
                                "money >= %cost%",
                                "money gems >= 100",
                                "item DIAMOND >= 5",
                                "item %held% >= <amount>")
                        .forEach(ConditionLines::read))
                .doesNotThrowAnyException();
    }
}
