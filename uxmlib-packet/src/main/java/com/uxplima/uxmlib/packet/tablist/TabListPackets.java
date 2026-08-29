package com.uxplima.uxmlib.packet.tablist;

import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

/**
 * The seam between pure tab-list logic and the NMS packet construction. Every packet crosses this boundary as
 * an opaque {@link Object}, so this interface (and everything that depends on it) carries no
 * {@code net.minecraft} reference and stays unit-testable against a fake. The single implementation that builds
 * real {@code ClientboundPlayerInfoUpdatePacket}/{@code ClientboundPlayerInfoRemovePacket} packets against the
 * Mojang-mapped dev bundle lives behind this port in {@code tablist.internal}.
 *
 * <p>These packets are how you do the per-viewer tab things native Paper cannot: a custom display name, a
 * client-side sort order, and a custom skin for an entry you fully control. Each method returns one built
 * packet; {@link #send(Player, Object)} writes it to a viewer's connection, so the same packet can be sent to
 * many viewers without rebuilding it.
 */
public interface TabListPackets {

    /**
     * Build an add-or-update packet that introduces a fully-controlled {@link TabEntry}: profile (with the
     * skin when present), listed, display name, and list order. Use this for an entry you own; you would not
     * re-add a real online player this way.
     */
    Object addOrUpdate(TabEntry entry);

    /** Build a packet that changes only the display name of an existing entry. */
    Object displayName(UUID id, Component name);

    /** Build a packet that changes only the client-side sort order of an existing entry. */
    Object listOrder(UUID id, int order);

    /** Build a packet that removes the given profile ids from the tab list. */
    Object remove(List<UUID> ids);

    /**
     * Build a packet that flips only the {@code listed} flag of existing entries (an {@code UPDATE_LISTED}
     * action), without touching their profile, skin, name, or order. With {@code listed=false} an entry stays a
     * known client entity but drops out of the tab list; with {@code listed=true} it reappears. This is the
     * counterpart to {@link PlayerInfoUpdates#forceUnlisted}: it re-lists real players when a viewer leaves a
     * synthetic-tab "suppress real players" mode, so their rows come back without re-adding their profiles.
     */
    Object relist(List<UUID> ids, boolean listed);

    /** Write {@code packet} to {@code viewer}'s connection. A no-op if the connection cannot be resolved. */
    void send(Player viewer, Object packet);
}
