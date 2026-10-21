package com.uxplima.uxmlib.pipeline;

/**
 * The interception seam. A listener inspects a raw packet object (an opaque {@link Object}: this foundation
 * does not decode packets into typed wrappers) flowing through a player's connection channel and answers with a
 * {@link PacketVerdict}: pass it, cancel it, or rewrite it into another packet.
 *
 * <p>Both callbacks run on a <b>Netty I/O thread</b>, never the main server thread. Implementations must not
 * touch the Bukkit API directly here; hand work to a {@code Scheduler} if it must run on a region thread. A
 * listener must be cheap and must not block. If a callback throws, the registry swallows it and the packet
 * passes (fail-open), so a faulty listener can never break a player's connection.
 *
 * <p>The inbound callback passes by default, so a listener that only watches what the server sends implements
 * {@link #onSend} alone.
 */
@FunctionalInterface
public interface PacketListener {

    /**
     * Inspect an outbound (server-to-client) packet.
     *
     * @param player the channel owner's unique id, or {@code null} before the login handshake names the
     *     connection (the channel exists before a {@code Player} does)
     * @param packet the raw packet object; never {@code null}
     * @return the verdict: pass, cancel, or a rewrite carrying the packet the client is sent instead
     */
    PacketVerdict onSend(java.util.@org.jspecify.annotations.Nullable UUID player, Object packet);

    /**
     * Inspect an inbound (client-to-server) packet. Passes by default.
     *
     * @param player the channel owner's unique id, or {@code null} before login
     * @param packet the raw packet object; never {@code null}
     * @return the verdict: pass, cancel, or a rewrite carrying the packet the server reads instead
     */
    default PacketVerdict onReceive(java.util.@org.jspecify.annotations.Nullable UUID player, Object packet) {
        return PacketVerdict.pass();
    }
}
