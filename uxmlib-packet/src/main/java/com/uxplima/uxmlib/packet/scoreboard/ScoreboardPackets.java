package com.uxplima.uxmlib.packet.scoreboard;

import java.util.List;

import org.bukkit.entity.Player;

/** General, NMS-free construction and delivery port for modern client-side scoreboard packets. */
public interface ScoreboardPackets {

    Object createObjective(ScoreboardObjective objective);

    Object updateObjective(ScoreboardObjective objective);

    Object removeObjective(String objectiveName);

    Object displayObjective(ScoreboardDisplaySlot slot, String objectiveName);

    Object clearDisplay(ScoreboardDisplaySlot slot);

    Object setScore(ScoreboardScore score);

    Object removeScore(String objectiveName, String holder);

    void sendPacket(Player viewer, Object packet);

    void sendPackets(Player viewer, List<Object> packets);
}
