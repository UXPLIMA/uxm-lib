package com.uxplima.uxmlib.redis;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import io.lettuce.core.RedisClient;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.jspecify.annotations.Nullable;

/**
 * Lettuce-backed {@link RedisBus}: the real Redis pub/sub wire. Frames travel as raw {@code byte[]} using
 * {@link ByteArrayCodec}; channel names are UTF-8 bytes.
 *
 * <p>Two connections, whatever the number of channels: one carries outbound PUBLISH, and one, opened at the
 * first {@link #subscribe}, holds every subscription and hands each message to the handlers of its channel.
 * Lettuce's automatic reconnect subscribes that connection to all of its channels again after a Redis blip.
 * A failed publish (Redis down) is fail-degraded: handed to the {@code warn} sink,
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
    private final Map<String, List<Consumer<byte[]>>> handlers = new ConcurrentHashMap<>();
    private @Nullable StatefulRedisPubSubConnection<byte[], byte[]> subscribeConnection;

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
    public synchronized void subscribe(String channel, Consumer<byte[]> onFrame) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(onFrame, "onFrame");
        StatefulRedisPubSubConnection<byte[], byte[]> connection = subscribeConnection;
        if (connection == null) {
            connection = client.connectPubSub(ByteArrayCodec.INSTANCE);
            connection.addListener(new FrameRouter(handlers));
            subscribeConnection = connection;
        }
        List<Consumer<byte[]>> forChannel = handlers.get(channel);
        if (forChannel != null) {
            forChannel.add(onFrame); // already subscribed: Redis sends each message once per connection
            return;
        }
        handlers.put(channel, new CopyOnWriteArrayList<>(List.of(onFrame)));
        // Synchronous SUBSCRIBE: runs at wiring time on the setup thread (never a latency-sensitive one), and
        // blocking here guarantees the subscription is active before the caller starts publishing: Redis
        // pub/sub has no buffering, so a message sent before SUBSCRIBE confirms is lost.
        connection.sync().subscribe(channel.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public synchronized void close() {
        StatefulRedisPubSubConnection<byte[], byte[]> connection = subscribeConnection;
        if (connection != null) {
            connection.close();
            subscribeConnection = null;
        }
        handlers.clear();
        publishConnection.close();
        client.shutdown();
    }

    /** Hands a Lettuce pub/sub message to the handlers of its channel, the {@link RedisBus} contract. */
    private static final class FrameRouter extends RedisPubSubAdapter<byte[], byte[]> {
        private final Map<String, List<Consumer<byte[]>>> handlers;

        private FrameRouter(Map<String, List<Consumer<byte[]>>> handlers) {
            this.handlers = handlers;
        }

        @Override
        public void message(byte[] channel, byte[] message) {
            List<Consumer<byte[]>> forChannel = handlers.get(new String(channel, StandardCharsets.UTF_8));
            if (forChannel != null) {
                forChannel.forEach(handler -> handler.accept(message));
            }
        }
    }
}
