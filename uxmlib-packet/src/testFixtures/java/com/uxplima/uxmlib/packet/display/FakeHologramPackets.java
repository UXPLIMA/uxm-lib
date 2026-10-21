package com.uxplima.uxmlib.packet.display;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import org.joml.Vector3f;

/**
 * A recording fake of {@link HologramPackets}. Every packet is a small sentinel record, so a test can assert
 * the structure of what was built and, through {@link #sends}, which viewer each packet reached. Entity ids
 * come from a counter so a test can check that an id stays bound to its line across refreshes.
 */
public final class FakeHologramPackets implements HologramPackets {

    /** A packet that adds an entity of a type at a position, facing a way. */
    public record Spawn(int entityId, String entityTypeKey, double x, double y, double z, float yaw, float pitch) {}

    /** A packet that paints one line; carries the text so a test can match viewer to content. */
    public record Metadata(int entityId, Component text, HologramAppearance appearance, Vector3f translation) {}

    /** A packet that paints an item display. */
    public record ItemMetadata(int entityId, ItemStack item, HologramAppearance appearance, Vector3f translation) {}

    /** A packet that paints a block display. */
    public record BlockMetadata(int entityId, BlockData block, HologramAppearance appearance, Vector3f translation) {}

    /** A packet that sizes a click box. */
    public record InteractionSize(int entityId, float width, float height) {}

    /** A packet that removes entities. */
    public record Remove(List<Integer> entityIds) {}

    /** A bundle wrapping several packets into one frame. */
    public record Bundle(List<Object> packets) {}

    /** One recorded send: the packet and the viewer it was written to. */
    public record Sent(Player viewer, Object packet) {}

    private final AtomicInteger nextId = new AtomicInteger(1);
    public final List<Sent> sends = new ArrayList<>();

    @Override
    public int allocateEntityId() {
        return nextId.getAndIncrement();
    }

    @Override
    public Object spawnPacket(
            int entityId, String entityTypeKey, double x, double y, double z, float yaw, float pitch) {
        return new Spawn(entityId, entityTypeKey, x, y, z, yaw, pitch);
    }

    @Override
    public Object itemMetadata(int entityId, ItemStack item, HologramAppearance appearance, Vector3f translation) {
        return new ItemMetadata(entityId, item.clone(), appearance, new Vector3f(translation));
    }

    @Override
    public Object blockMetadata(int entityId, BlockData block, HologramAppearance appearance, Vector3f translation) {
        return new BlockMetadata(entityId, block.clone(), appearance, new Vector3f(translation));
    }

    @Override
    public Object metadataPacket(int entityId, Component text, HologramAppearance appearance, Vector3f translation) {
        return new Metadata(entityId, text, appearance, new Vector3f(translation));
    }

    @Override
    public Object interactionSize(int entityId, float width, float height) {
        return new InteractionSize(entityId, width, height);
    }

    @Override
    public Object removePacket(int[] entityIds) {
        List<Integer> ids = new ArrayList<>(entityIds.length);
        for (int id : entityIds) {
            ids.add(id);
        }
        return new Remove(ids);
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
    public int allocations() {
        return nextId.get() - 1;
    }

    /** Everything written to {@code viewer}, in order. */
    public List<Object> packetsFor(Player viewer) {
        return sends.stream()
                .filter(sent -> sent.viewer().equals(viewer))
                .map(Sent::packet)
                .toList();
    }

    public void forget() {
        sends.clear();
    }
}
