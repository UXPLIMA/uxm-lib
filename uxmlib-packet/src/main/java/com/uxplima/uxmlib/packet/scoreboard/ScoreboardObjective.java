package com.uxplima.uxmlib.packet.scoreboard;

import java.util.Objects;

import net.kyori.adventure.text.Component;

/** Complete client-side objective metadata used to create or update an objective. */
public record ScoreboardObjective(
        String name, Component displayName, ScoreboardRenderType renderType, ScoreboardNumberFormat numberFormat) {

    public ScoreboardObjective {
        requireIdentifier(name, "objective name");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(renderType, "renderType");
        Objects.requireNonNull(numberFormat, "numberFormat");
    }

    public ScoreboardObjective(String name, Component displayName, ScoreboardNumberFormat numberFormat) {
        this(name, displayName, ScoreboardRenderType.INTEGER, numberFormat);
    }

    private static void requireIdentifier(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
    }
}
