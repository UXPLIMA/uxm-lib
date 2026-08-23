package com.uxplima.uxmlib.packet.tablist;

import java.util.Objects;
import java.util.UUID;

/** One id-addressed value in a homogeneous player-info update packet. */
public record PlayerInfoValue<T>(UUID id, T value) {

    public PlayerInfoValue {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(value, "value");
    }

    public static <T> PlayerInfoValue<T> of(UUID id, T value) {
        return new PlayerInfoValue<>(id, value);
    }
}
