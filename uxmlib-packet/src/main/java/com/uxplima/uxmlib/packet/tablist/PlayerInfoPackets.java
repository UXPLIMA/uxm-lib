package com.uxplima.uxmlib.packet.tablist;

import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

/**
 * General, NMS-free player-info packet construction port.
 *
 * <p>Every builder accepts a batch and returns one opaque packet. A homogeneous update changes exactly the
 * named field and leaves all other client state untouched. The older {@link TabListPackets} interface remains
 * as a source-compatible convenience facade for existing consumers; new renderers should prefer this port.
 */
public interface PlayerInfoPackets {

    /** Add or fully update all synthetic entries in one player-info packet. */
    Object addOrUpdate(List<PlayerInfoEntry> entries);

    /** Update only display names, in one packet. */
    Object displayNames(List<PlayerInfoValue<Component>> entries);

    /** Update only modern client-side list order values, in one packet. */
    Object listOrders(List<PlayerInfoValue<Integer>> entries);

    /** Update only listed flags, in one packet. */
    Object listed(List<PlayerInfoValue<Boolean>> entries);

    /** Update only latency values, in one packet. */
    Object latencies(List<PlayerInfoValue<Integer>> entries);

    /** Update only game-mode values, in one packet. */
    Object gameModes(List<PlayerInfoValue<PlayerInfoGameMode>> entries);

    /** Update only skin hat-layer flags, in one packet. */
    Object showHat(List<PlayerInfoValue<Boolean>> entries);

    /** Remove all profile ids in one player-info remove packet. */
    Object removeEntries(List<UUID> ids);

    /** Write a previously built packet to one viewer. */
    void sendPacket(Player viewer, Object packet);
}
