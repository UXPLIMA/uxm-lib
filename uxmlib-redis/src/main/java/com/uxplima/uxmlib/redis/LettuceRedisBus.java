package com.uxplima.uxmlib.redis;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import io.lettuce.core.RedisClient;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

/**
 * Lettuce-backed {@link RedisBus}: the real Redis pub/sub wire. Frames travel as raw {@code byte[]} using
 * {@link ByteArrayCodec}; channel names are UTF-8 bytes.
 *
 * <p>One {@link StatefulRedisPubSubConnection} carries outbound PUBLISH; each {@link #subscribe} opens its
 * own pub/sub connection so Lettuce's automatic reconnect re-establishes the subscription transparently
 * after a Redis blip. A failed publish (Redis down) is fail-degraded: handed to the {@code warn} sink,
 * never thrown, so a transient outage never propagates into the caller's path.
 *
 * <p>Threading: Lettuce runs all I/O on its own event loop: no {@code new Thread}, no platform scheduler.
 * Inbound messages are handed to the registered {@link Consumer} on that event-loop thread.
 *
 * <p>A flapping Redis would otherwise fire {@code warn} on every failed publish future; the warn path is
 * rate-limited to at most one message per {@value #WARN_INTERVAL_MS}ms so an outage cannot flood the log.
 */
public final class LettuceRedisBus implements RedisBus {

    private static final long WARN_INTERVAL_MS = 60_000L;

    private final RedisClient client;
    private final StatefulRedisPubSubConnection<byte[], byte[]> publishConnection;
    private final RateLimitedWarner warner;
    private final CopyOnWriteArrayList<StatefulRedisPubSubConnection<byte[], byte[]>> subscriptions =
            new CopyOnWriteArrayList<>();

    /**
     * @param client a fully-configured Lettuce client (URI, auth, options are the caller's to set)
     * @param warn the sink for a fail-degraded publish error message (e.g. {@code logger::warn})
     */
    public LettuceRedisBus(RedisClient client, Consumer<String> warn) {
        this(client, warn, System::currentTimeMillis);
    }

    /**
     * Test seam: a caller-supplied {@code clock} (epoch millis) drives the publish-warn throttle so the
     * one-per-window behaviour is exercised deterministically without sleeping. Production wiring uses the
     * public two-arg constructor, which pins {@code clock} to {@link System#currentTimeMillis()}.
     */
    LettuceRedisBus(RedisClient client, Consumer<String> warn, LongSupplier clock) {
        this.client = Objects.requireNonNull(client, "client");
        this.warner = new RateLimitedWarner(Objects.requireNonNull(warn, "warn"), WARN_INTERVAL_MS, clock);
        this.publishConnection = client.connectPubSub(ByteArrayCodec.INSTANCE);
    }

    @Override
    public void publish(String channel, byte[] frame) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(frame, "frame");
        byte[] topic = channel.getBytes(StandardCharsets.UTF_8);
        // Fire-and-forget on the event loop; never blocks the caller. A failed publish (Redis down) is
        // fail-degraded: reported (rate-limited), never thrown.
        publishConnection.async().publish(topic, frame).exceptionally(failure -> {
            warner.warn("redis publish to " + channel + " failed: " + failure.getMessage());
            return null;
        });
    }

    @Override
    public boolean healthy() {
        // The outbound publish connection is the one a publish actually rides; an open one means a frame has a
        // live path to Redis, while a closed one (never opened, or torn down by close) means publishes
        // fail-degrade. Lettuce auto-reconnects, so this flips back to true once the wire recovers.
        return publishConnection.isOpen();
    }

    @Override
    public void subscribe(String channel, Consumer<byte[]> onFrame) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(onFrame, "onFrame");
        StatefulRedisPubSubConnection<byte[], byte[]> connection = client.connectPubSub(ByteArrayCodec.INSTANCE);
        subscriptions.add(connection);
        connection.addListener(new FrameListener(onFrame));
        // Synchronous SUBSCRIBE: runs at wiring time on the setup thread (never a latency-sensitive one), and
        // blocking here guarantees the subscription is active before the caller starts publishing: Redis
        // pub/sub has no buffering, so a message sent before SUBSCRIBE confirms is lost.
        connection.sync().subscribe(channel.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() {
        for (StatefulRedisPubSubConnection<byte[], byte[]> connection : subscriptions) {
            connection.close();
        }
        subscriptions.clear();
        publishConnection.close();
        client.shutdown();
    }

    /** Adapts a Lettuce pub/sub message callback to the {@link RedisBus} contract. */
    private static final class FrameListener extends RedisPubSubAdapter<byte[], byte[]> {
        private final Consumer<byte[]> onFrame;

        private FrameListener(Consumer<byte[]> onFrame) {
            this.onFrame = onFrame;
        }

        @Override
        public void message(byte[] channel, byte[] message) {
            onFrame.accept(message);
        }
    }
}
