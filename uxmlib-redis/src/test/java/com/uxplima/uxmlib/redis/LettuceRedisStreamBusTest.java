package com.uxplima.uxmlib.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.models.stream.ClaimedMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LettuceRedisStreamBusTest {

    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> commands;
    private LettuceRedisStreamBus streamBus;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        connection = mock(StatefulRedisConnection.class);
        commands = mock(RedisCommands.class);
        when(connection.sync()).thenReturn(commands);
        when(connection.isOpen()).thenReturn(true);

        streamBus = new LettuceRedisStreamBus(connection, msg -> {});
    }

    @Test
    @DisplayName("xadd delegates to commands and returns generated message ID")
    void testXadd() {
        Map<String, String> body = Map.of("key", "val");
        when(commands.xadd("test-stream", body)).thenReturn("1000-0");

        String id = streamBus.xadd("test-stream", body);
        assertThat(id).isEqualTo("1000-0");
        verify(commands).xadd("test-stream", body);
    }

    @Test
    @DisplayName("xack delegates to commands and returns count")
    void testXack() {
        when(commands.xack("test-stream", "my-group", "1000-0")).thenReturn(1L);

        long acked = streamBus.xack("test-stream", "my-group", "1000-0");
        assertThat(acked).isEqualTo(1L);
        verify(commands).xack("test-stream", "my-group", "1000-0");
    }

    @Test
    @DisplayName("xreadgroup reads and maps messages")
    void testXreadgroup() {
        io.lettuce.core.StreamMessage<String, String> msg =
                new io.lettuce.core.StreamMessage<>("test-stream", "1000-0", Map.of("data", "hello"));
        when(commands.xreadgroup(any(), any(), any())).thenReturn(List.of(msg));

        List<RedisStreamBus.StreamEntry> entries =
                streamBus.xreadgroup("test-stream", "group1", "consumer1", 10, Duration.ofSeconds(1));
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).id()).isEqualTo("1000-0");
        assertThat(entries.get(0).body()).containsEntry("data", "hello");
    }

    @Test
    @DisplayName("xautoclaim claims and maps messages")
    void testXautoclaim() {
        io.lettuce.core.StreamMessage<String, String> msg =
                new io.lettuce.core.StreamMessage<>("test-stream", "2000-0", Map.of("data", "stale"));
        ClaimedMessages<String, String> claimed = new ClaimedMessages<>("0-0", List.of(msg));
        when(commands.xautoclaim(eq("test-stream"), any())).thenReturn(claimed);

        List<RedisStreamBus.StreamEntry> entries =
                streamBus.xautoclaim("test-stream", "group1", "consumer1", Duration.ofSeconds(30), 10);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).id()).isEqualTo("2000-0");
    }

    @Test
    @DisplayName("healthy reflects underlying connection status")
    void testHealthy() {
        assertThat(streamBus.healthy()).isTrue();
        when(connection.isOpen()).thenReturn(false);
        assertThat(streamBus.healthy()).isFalse();
    }
}
