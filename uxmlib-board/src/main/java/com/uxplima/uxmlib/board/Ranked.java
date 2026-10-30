package com.uxplima.uxmlib.board;

import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * One place on a board as its source reads it.
 *
 * @param name the name the player was last seen under, or null when none was kept
 */
public record Ranked(UUID player, @Nullable String name, long score) {

    public Ranked {
        Objects.requireNonNull(player, "player");
    }
}
