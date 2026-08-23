package com.uxplima.uxmlib.packet.scoreboard;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmlib.packet.scoreboard.internal.NmsScoreboardPacketEvents;

/** Decodes only objective lifecycle and display-slot facts needed by ownership coordinators. */
public final class ScoreboardPacketEvents {

    private ScoreboardPacketEvents() {}

    public static Optional<ScoreboardPacketEvent> decode(Object packet) {
        return decodeAll(packet).stream().findFirst();
    }

    /** Decode every relevant event, including packets nested inside a vanilla clientbound bundle. */
    public static List<ScoreboardPacketEvent> decodeAll(Object packet) {
        return NmsScoreboardPacketEvents.decodeAll(Objects.requireNonNull(packet, "packet"));
    }
}
