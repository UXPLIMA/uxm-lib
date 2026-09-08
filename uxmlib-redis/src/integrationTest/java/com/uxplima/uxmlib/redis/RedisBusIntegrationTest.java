package com.uxplima.uxmlib.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Round-trip test for {@link LettuceRedisBus} against a real Redis: a publishing bus PUBLISHes a binary frame
 * and a second subscribing bus receives the exact bytes. The broker comes from {@code UXMLIB_TEST_REDIS_URI},
 * or {@code redis://localhost:6379}.
 *
 * <p>It needs a machine the build cannot promise, so it lives in its own source set and {@code check} never
 * runs it. It sat in {@code src/test} until 2026-09-08 and opened with an {@code assumeTrue} that aborted when
 * no broker answered: JUnit records an abort as a skip, so {@code build} on a host without Redis went green
 * having proved nothing. This machine happens to run a Redis, which is exactly why the hole was invisible
 * here. Run it with {@code ./gradlew :uxmlib-redis:integrationTest}: no broker is a failure now, because
 * somebody who asked for this task asked for one.
 */
@org.jspecify.annotations.NullUnmarked
class RedisBusIntegrationTest {

    private static @Nullable String redisUri;

    @BeforeAll
    static void resolveRedis() {
        String env = System.getenv("UXMLIB_TEST_REDIS_URI");
        String uri = env != null && !env.isBlank() ? env : "redis://localhost:6379";
        if (!reachable(uri)) {
            throw new IllegalStateException("no Redis reachable at " + uri + ". Set UXMLIB_TEST_REDIS_URI.");
        }
        redisUri = uri;
    }

    @Test
    void published_frame_reaches_a_subscriber_byte_for_byte() throws InterruptedException {
        String channel = "uxmlib:test:" + UUID.randomUUID();
        RedisURI uri = RedisURI.create(redisUri);
        LettuceRedisBus publisher = new LettuceRedisBus(RedisClient.create(uri), message -> {});
        LettuceRedisBus subscriber = new LettuceRedisBus(RedisClient.create(uri), message -> {});

        BlockingQueue<byte[]> received = new ArrayBlockingQueue<>(4);
        subscriber.subscribe(channel, received::add);

        // High bytes (200, 255) and an embedded NUL prove the wire is binary-safe (a String codec would
        // mangle these); arbitrary codec output must survive untouched.
        byte[] frame = {1, 2, 3, (byte) 200, 0, (byte) 255, 42};

        try {
            publisher.publish(channel, frame);
            byte[] got = received.poll(10, TimeUnit.SECONDS);
            assertThat(got).isEqualTo(frame);
        } finally {
            publisher.close();
            subscriber.close();
        }
    }

    @Test
    void healthy_reflects_the_live_publish_connection() {
        RedisURI uri = RedisURI.create(redisUri);
        LettuceRedisBus bus = new LettuceRedisBus(RedisClient.create(uri), message -> {});

        // An open, just-connected bus reports healthy; closing it tears down the publish connection, so the
        // signal flips to unhealthy: a real connection state, not a constant.
        assertThat(bus.healthy()).isTrue();
        bus.close();
        assertThat(bus.healthy()).isFalse();
    }

    private static boolean reachable(String uri) {
        try {
            RedisURI parsed = RedisURI.create(uri);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(parsed.getHost(), parsed.getPort()), 1500);
                return true;
            }
        } catch (Exception unreachable) {
            return false;
        }
    }
}
