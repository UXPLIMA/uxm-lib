package com.uxplima.uxmlib.packet.scoreboard;

import java.util.Objects;

import net.kyori.adventure.text.Component;

/** A server-internal-free description of the number rendered at the right edge of a score. */
public sealed interface ScoreboardNumberFormat {

    /** Use the objective or vanilla integer format. */
    record Default() implements ScoreboardNumberFormat {}

    /** Render no right-edge value. */
    record Blank() implements ScoreboardNumberFormat {}

    /** Render one fixed component instead of the integer score. */
    record Fixed(Component value) implements ScoreboardNumberFormat {
        public Fixed {
            Objects.requireNonNull(value, "value");
        }
    }

    static ScoreboardNumberFormat defaultFormat() {
        return new Default();
    }

    static ScoreboardNumberFormat blank() {
        return new Blank();
    }

    static ScoreboardNumberFormat fixed(Component value) {
        return new Fixed(value);
    }
}
