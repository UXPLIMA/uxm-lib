package com.uxplima.uxmlib.packet.display;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.packet.display.internal.NmsHologramPackets;
import com.uxplima.uxmlib.pipeline.ChannelResolver;
import com.uxplima.uxmlib.pipeline.PacketSender;
import org.joml.Vector3f;

/**
 * The server's {@link HologramPackets}, for a caller that should not name the internals they are built from.
 *
 * <p>A server without those internals, a test server or a line this release was not built for, gets packets that
 * send nothing. Its holograms are then seen by nobody, which costs the viewers the text and never costs the caller
 * its enable.
 */
public final class ServerHologramPackets {

    private ServerHologramPackets() {}

    /** The server's hologram packets, or packets that send nothing where the internals are missing. */
    public static HologramPackets orNone(Plugin plugin) {
        return orNone(plugin, () -> new NmsHologramPackets(new PacketSender(new ChannelResolver())));
    }

    static HologramPackets orNone(Plugin plugin, Supplier<HologramPackets> server) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(server, "server");
        try {
            return server.get();
        } catch (LinkageError absent) {
            plugin.getSLF4JLogger()
                    .warn(
                            "This server does not have the internals holograms are sent with, so no hologram is"
                                    + " shown: {}",
                            absent.toString());
            return new NoHolograms();
        }
    }

    /**
     * The packets of a server without the internals: each is a placeholder that is never written. Ids still differ,
     * because a hologram tells its lines apart by them.
     */
    private static final class NoHolograms implements HologramPackets {

        private static final Object NOTHING = new Object();

        private final AtomicInteger ids = new AtomicInteger(Integer.MIN_VALUE);

        @Override
        public int allocateEntityId() {
            return ids.getAndIncrement();
        }

        @Override
        public Object spawnPacket(
                int entityId, String entityTypeKey, double x, double y, double z, float yaw, float pitch) {
            return NOTHING;
        }

        @Override
        public Object metadataPacket(
                int entityId, Component text, HologramAppearance appearance, Vector3f translation) {
            return NOTHING;
        }

        @Override
        public Object itemMetadata(int entityId, ItemStack item, HologramAppearance appearance, Vector3f translation) {
            return NOTHING;
        }

        @Override
        public Object blockMetadata(
                int entityId, BlockData block, HologramAppearance appearance, Vector3f translation) {
            return NOTHING;
        }

        @Override
        public Object interactionSize(int entityId, float width, float height) {
            return NOTHING;
        }

        @Override
        public Object removePacket(int[] entityIds) {
            return NOTHING;
        }

        @Override
        public Object bundle(List<Object> packets) {
            return NOTHING;
        }

        @Override
        public void send(Player viewer, Object packet) {
            // Nothing is written: this server cannot build a packet a client would read.
        }
    }
}
