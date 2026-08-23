package com.uxplima.uxmlib.packet.scoreboard;

import java.util.Objects;

import net.kyori.adventure.text.Component;

/** One modern score row with a stable protocol holder, independent display text and per-row number format. */
public record ScoreboardScore(
        String objectiveName, String holder, int score, Component displayName, ScoreboardNumberFormat numberFormat) {

    public ScoreboardScore {
        requireIdentifier(objectiveName, "objective name");
        requireIdentifier(holder, "score holder");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(numberFormat, "numberFormat");
    }

    private static void requireIdentifier(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
    }
}
