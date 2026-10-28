package com.uxplima.uxmlib.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.uxplima.uxmlib.redis.RedisBus;
import org.junit.jupiter.api.Test;

/** The Redis transport, over a Redis that lives in memory and can be down. */
class RedisNetworkBusTest {

    private static final System.Logger LOG = System.getLogger("test");

    /** One Redis shared by every bus in a test; it refuses subscriptions while down. */
    private static final class MemoryRedis implements RedisBus {

        private final Map<String, List<Consumer<byte[]>>> channels = new HashMap<>();
        private boolean down;
        private boolean closed;

        @Override
        public void publish(String channel, byte[] frame) {
            channels.getOrDefault(channel, List.of()).forEach(listener -> listener.accept(frame));
        }

        @Override
        public void subscribe(String channel, Consumer<byte[]> onFrame) {
            if (down) {
                throw new IllegalStateException("Redis is down");
            }
            channels.computeIfAbsent(channel, name -> new ArrayList<>()).add(onFrame);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    void aTopicWhoseSubscribeFailedIsSubscribedOnTheNextTry() {
        MemoryRedis redis = new MemoryRedis();
        RedisNetworkBus lobby = new RedisNetworkBus("lobby", "test", redis, LOG);
        RedisNetworkBus survival = new RedisNetworkBus("survival", "test", redis, LOG);
        List<Frame> heard = new ArrayList<>();

        redis.down = true;
        assertThatThrownBy(() -> survival.subscribe("chat", heard::add)).isInstanceOf(IllegalStateException.class);
        redis.down = false;
        survival.subscribe("chat", frame -> {});
        lobby.publish("chat", "hello".getBytes(StandardCharsets.UTF_8));

        assertThat(heard).hasSize(1);
    }

    @Test
    void closingTheBusLeavesTheSharedRedisOpenAndHearsNothingMore() {
        MemoryRedis redis = new MemoryRedis();
        RedisNetworkBus lobby = new RedisNetworkBus("lobby", "test", redis, LOG);
        RedisNetworkBus survival = new RedisNetworkBus("survival", "test", redis, LOG);
        List<Frame> heard = new ArrayList<>();
        survival.subscribe("chat", heard::add);

        survival.close();
        lobby.publish("chat", "hello".getBytes(StandardCharsets.UTF_8));

        assertThat(redis.closed).isFalse();
        assertThat(heard).isEmpty();
    }
}
