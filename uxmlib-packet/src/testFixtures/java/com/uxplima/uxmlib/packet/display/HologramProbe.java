package com.uxplima.uxmlib.packet.display;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

/**
 * What a viewer's client holds, rebuilt from the packets {@link FakeHologramPackets} recorded for that viewer and
 * from nothing else.
 *
 * <p>A test of a change drives the change, runs a frame, and compares {@link #heldBy} against what the viewer
 * should see now. Reading the client side rather than the hologram's own state is the point: a hologram that
 * believes it repainted a line and never sent the packet is caught here.
 */
public final class HologramProbe {

    private final FakeHologramPackets packets;

    public HologramProbe(FakeHologramPackets packets) {
        this.packets = Objects.requireNonNull(packets, "packets");
    }

    /** The lines {@code viewer} holds, top first; empty when it holds nothing. */
    public List<Component> heldBy(Player viewer) {
        Map<Integer, Component> held = new LinkedHashMap<>();
        for (Object packet : packets.packetsFor(viewer)) {
            apply(held, packet);
        }
        return new ArrayList<>(held.values());
    }

    private static void apply(Map<Integer, Component> held, Object packet) {
        switch (packet) {
            case FakeHologramPackets.Bundle bundle -> bundle.packets().forEach(inner -> apply(held, inner));
            case FakeHologramPackets.Spawn spawn -> held.put(spawn.entityId(), Component.empty());
            case FakeHologramPackets.Metadata metadata -> {
                if (held.containsKey(metadata.entityId())) {
                    held.put(metadata.entityId(), metadata.text());
                }
            }
            case FakeHologramPackets.Remove remove -> remove.entityIds().forEach(held::remove);
            default -> throw new IllegalStateException("a packet the probe does not know: " + packet);
        }
    }
}
