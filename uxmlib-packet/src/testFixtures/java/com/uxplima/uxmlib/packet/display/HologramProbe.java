package com.uxplima.uxmlib.packet.display;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    /** The entity types that are painted by a metadata packet after they are spawned. */
    private static final Set<String> DISPLAYS = Set.of("text_display", "item_display", "block_display");

    private final FakeHologramPackets packets;

    public HologramProbe(FakeHologramPackets packets) {
        this.packets = Objects.requireNonNull(packets, "packets");
    }

    /** The lines {@code viewer} holds, top first; empty when it holds nothing. */
    public List<PacketLine> heldBy(Player viewer) {
        Map<Integer, PacketLine> held = new LinkedHashMap<>();
        for (Object packet : packets.packetsFor(viewer)) {
            apply(held, packet);
        }
        return new ArrayList<>(held.values());
    }

    private static void apply(Map<Integer, PacketLine> held, Object packet) {
        switch (packet) {
            case FakeHologramPackets.Bundle bundle -> bundle.packets().forEach(inner -> apply(held, inner));
            case FakeHologramPackets.Spawn spawn ->
                // A display holds nothing until it is painted; a mob is whole the moment it is spawned.
                held.put(
                        spawn.entityId(),
                        DISPLAYS.contains(spawn.entityTypeKey())
                                ? PacketLine.text(Component.empty())
                                : PacketLine.mob(spawn.entityTypeKey()));
            case FakeHologramPackets.Metadata metadata ->
                paint(held, metadata.entityId(), PacketLine.text(metadata.text()));
            case FakeHologramPackets.ItemMetadata item -> paint(held, item.entityId(), PacketLine.item(item.item()));
            case FakeHologramPackets.BlockMetadata block ->
                paint(held, block.entityId(), PacketLine.block(block.block()));
            case FakeHologramPackets.Remove remove -> remove.entityIds().forEach(held::remove);
            default -> throw new IllegalStateException("a packet the probe does not know: " + packet);
        }
    }

    /** A client paints only an entity it holds; metadata for any other id is dropped, as a real client drops it. */
    private static void paint(Map<Integer, PacketLine> held, int entityId, PacketLine line) {
        if (held.containsKey(entityId)) {
            held.put(entityId, line);
        }
    }
}
