package com.uxplima.uxmlib.nametag;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import org.joml.Vector3f;

/**
 * A recording fake of {@link NametagPackets}. Every packet is a small sentinel record, so a test can assert
 * the structure of what was built and (through {@link #sends}), which recipient each packet reached. Entity
 * ids are handed out from a counter so a test can check id stability across updates.
 */
final class FakeNametagPackets implements NametagPackets {

    /** A packet that adds an entity at a position. */
    record Spawn(int entityId, double x, double y, double z) {}

    /** A packet that sets entity data; carries the per-viewer text so the test can match recipient to content. */
    record Metadata(int entityId, Component text, Appearance appearance, int opacity, Vector3f translation) {}

    /** A packet that seats passengers on a vehicle. */
    record Mount(int vehicleEntityId, List<Integer> passengerIds) {}

    /** A packet that removes entities. */
    record Remove(List<Integer> entityIds) {}

    /** A bundle wrapping several packets into one frame. */
    record Bundle(List<Object> packets) {}

    /** One recorded send: the packet and the player it was written to. */
    record Sent(Player viewer, Object packet) {}

    private final AtomicInteger nextId = new AtomicInteger(1);
    final List<Sent> sends = new ArrayList<>();

    @Override
    public int allocateEntityId() {
        return nextId.getAndIncrement();
    }

    @Override
    public Object spawnPacket(int entityId, double x, double y, double z) {
        return new Spawn(entityId, x, y, z);
    }

    @Override
    public Object metadataPacket(
            int entityId, Component text, Appearance appearance, int opacity, Vector3f translation) {
        return new Metadata(entityId, text, appearance, opacity, translation);
    }

    @Override
    public Object mountPacket(int vehicleEntityId, int[] passengerIds) {
        return new Mount(vehicleEntityId, toList(passengerIds));
    }

    @Override
    public Object removePacket(int[] entityIds) {
        return new Remove(toList(entityIds));
    }

    @Override
    public Object bundle(List<Object> packets) {
        return new Bundle(List.copyOf(packets));
    }

    @Override
    public void send(Player viewer, Object packet) {
        sends.add(new Sent(viewer, packet));
    }

    /** How many entity ids have been handed out so far. */
    int allocations() {
        return nextId.get() - 1;
    }

    private static List<Integer> toList(int[] values) {
        List<Integer> out = new ArrayList<>(values.length);
        for (int value : values) {
            out.add(value);
        }
        return out;
    }
}
