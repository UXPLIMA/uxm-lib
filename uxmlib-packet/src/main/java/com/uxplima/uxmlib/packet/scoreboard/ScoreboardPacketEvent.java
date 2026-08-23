package com.uxplima.uxmlib.packet.scoreboard;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/** Ownership-relevant facts decoded from outbound vanilla scoreboard packets. */
public sealed interface ScoreboardPacketEvent {

    record Display(ScoreboardDisplaySlot slot, @Nullable String rawObjectiveName) implements ScoreboardPacketEvent {
        public Display {
            Objects.requireNonNull(slot, "slot");
        }

        public Optional<String> objectiveName() {
            return Optional.ofNullable(rawObjectiveName);
        }
    }

    record Objective(String objectiveName, ScoreboardObjectiveAction action) implements ScoreboardPacketEvent {
        public Objective {
            Objects.requireNonNull(objectiveName, "objectiveName");
            Objects.requireNonNull(action, "action");
            if (objectiveName.isBlank()) {
                throw new IllegalArgumentException("objective name must not be blank");
            }
        }
    }
}
