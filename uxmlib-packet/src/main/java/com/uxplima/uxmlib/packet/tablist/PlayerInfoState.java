package com.uxplima.uxmlib.packet.tablist;

import java.util.Objects;
import java.util.UUID;

import net.kyori.adventure.text.Component;

import org.jspecify.annotations.Nullable;

/**
 * The safely rewritable fields of one entry in an outbound player-info update.
 *
 * <p>The profile and secure-chat session are intentionally absent: the NMS adapter always carries those opaque
 * server-owned values through unchanged. The profile id is present for selection but a transformer may not change
 * it. Copy methods make single-field rewrites explicit and keep transforms readable.
 */
public record PlayerInfoState(
        UUID id,
        boolean listed,
        int latency,
        PlayerInfoGameMode gameMode,
        @Nullable Component displayName,
        boolean showHat,
        int listOrder) {

    public PlayerInfoState {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(gameMode, "gameMode");
    }

    public PlayerInfoState withListed(boolean value) {
        return new PlayerInfoState(id, value, latency, gameMode, displayName, showHat, listOrder);
    }

    public PlayerInfoState withLatency(int value) {
        return new PlayerInfoState(id, listed, value, gameMode, displayName, showHat, listOrder);
    }

    public PlayerInfoState withGameMode(PlayerInfoGameMode value) {
        return new PlayerInfoState(id, listed, latency, value, displayName, showHat, listOrder);
    }

    public PlayerInfoState withDisplayName(@Nullable Component value) {
        return new PlayerInfoState(id, listed, latency, gameMode, value, showHat, listOrder);
    }

    public PlayerInfoState withShowHat(boolean value) {
        return new PlayerInfoState(id, listed, latency, gameMode, displayName, value, listOrder);
    }

    public PlayerInfoState withListOrder(int value) {
        return new PlayerInfoState(id, listed, latency, gameMode, displayName, showHat, value);
    }
}
