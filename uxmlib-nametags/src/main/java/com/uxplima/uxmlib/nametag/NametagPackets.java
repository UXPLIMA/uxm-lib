package com.uxplima.uxmlib.nametag;

import java.util.List;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import org.joml.Vector3f;

/**
 * The seam between the pure renderer and the NMS packet construction. Every packet crosses this boundary as
 * an opaque {@link Object}, so this interface (and everything that depends on it) carries no
 * {@code net.minecraft} reference and stays unit-testable with a fake. The single implementation that builds
 * real packets against the Mojang-mapped dev bundle lands behind this port in a later milestone.
 */
public interface NametagPackets {

    /**
     * Reserve a fresh, server-unused entity id for a packet display. The renderer mounts the display on the
     * target with this id; it must not collide with a live server entity.
     */
    int allocateEntityId();

    /** Build the add-entity packet that spawns a text display with {@code entityId} at the given position. */
    Object spawnPacket(int entityId, double x, double y, double z);

    /**
     * Build the set-entity-data packet that paints {@code entityId} with one viewer's text, the shared
     * {@link Appearance}, a line-of-sight opacity, and the translation offset.
     */
    Object metadataPacket(int entityId, Component text, Appearance appearance, int opacity, Vector3f translation);

    /** Build the set-passengers packet that seats {@code passengerIds} on {@code vehicleEntityId}. */
    Object mountPacket(int vehicleEntityId, int[] passengerIds);

    /** Build the remove-entities packet that despawns {@code entityIds} for a viewer. */
    Object removePacket(int[] entityIds);

    /** Wrap several packets into one bundle so the client applies them in a single frame. */
    Object bundle(List<Object> packets);

    /** Write {@code packet} to {@code viewer}'s connection. A no-op if the connection cannot be resolved. */
    void send(Player viewer, Object packet);
}
