package com.uxplima.uxmlib.packet.scoreboard.internal;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmlib.packet.scoreboard.ScoreboardDisplaySlot;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardObjectiveAction;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardPacketEvent;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import org.jspecify.annotations.Nullable;

/** The NMS-bearing decoder behind the public scoreboard event seam. */
public final class NmsScoreboardPacketEvents {

    private NmsScoreboardPacketEvents() {}

    public static @Nullable ScoreboardPacketEvent decode(Object packet) {
        if (packet instanceof ClientboundSetDisplayObjectivePacket display) {
            return new ScoreboardPacketEvent.Display(slot(display), display.getObjectiveName());
        }
        if (packet instanceof ClientboundSetObjectivePacket objective) {
            return new ScoreboardPacketEvent.Objective(objective.getObjectiveName(), action(objective.getMethod()));
        }
        return null;
    }

    public static List<ScoreboardPacketEvent> decodeAll(Object packet) {
        List<ScoreboardPacketEvent> events = new ArrayList<>();
        collect(packet, events);
        return List.copyOf(events);
    }

    private static void collect(Object packet, List<ScoreboardPacketEvent> events) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            for (Object nested : bundle.subPackets()) {
                collect(nested, events);
            }
            return;
        }
        @Nullable ScoreboardPacketEvent decoded = decode(packet);
        if (decoded != null) {
            events.add(decoded);
        }
    }

    private static ScoreboardDisplaySlot slot(ClientboundSetDisplayObjectivePacket packet) {
        return switch (packet.getSlot()) {
            case LIST -> ScoreboardDisplaySlot.PLAYER_LIST;
            case SIDEBAR -> ScoreboardDisplaySlot.SIDEBAR;
            case BELOW_NAME -> ScoreboardDisplaySlot.BELOW_NAME;
            default -> ScoreboardDisplaySlot.SIDEBAR;
        };
    }

    private static ScoreboardObjectiveAction action(int method) {
        return switch (method) {
            case ClientboundSetObjectivePacket.METHOD_ADD -> ScoreboardObjectiveAction.ADD;
            case ClientboundSetObjectivePacket.METHOD_REMOVE -> ScoreboardObjectiveAction.REMOVE;
            case ClientboundSetObjectivePacket.METHOD_CHANGE -> ScoreboardObjectiveAction.UPDATE;
            default -> throw new IllegalArgumentException("unknown objective action: " + method);
        };
    }
}
