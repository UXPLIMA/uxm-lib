package com.uxplima.uxmlib.network;

import java.util.function.Consumer;

/**
 * Frames between the servers of a network.
 *
 * <p>A server never receives its own frames. Delivery is at most once and in order per sender while the
 * transport is up; a frame sent while it is down waits in a bounded queue and the oldest is dropped first
 * when the queue is full. Handlers run on the transport's thread, so a handler that touches the server
 * hands the work to the server's scheduler itself.
 */
public interface Bus extends AutoCloseable {

    /** This server's id, as other servers see it in {@link Frame#origin()}. */
    String serverId();

    /** Send to every other server. */
    void publish(String topic, byte[] payload);

    /** Send to one server; nothing happens when that server is not connected. */
    void send(String server, String topic, byte[] payload);

    /** Receive every frame on {@code topic}. Several handlers on one topic each receive it. */
    void subscribe(String topic, Consumer<Frame> handler);

    /** Whether frames sent now have a path to the other servers. */
    boolean healthy();

    @Override
    void close();
}
