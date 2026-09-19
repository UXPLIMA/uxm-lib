package com.uxplima.uxmlib.redis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import io.lettuce.core.RedisBusyException;
import io.lettuce.core.RedisClient;
import io.lettuce.core.XAutoClaimArgs;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.XReadArgs.StreamOffset;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.models.stream.ClaimedMessages;

/**
 * Lettuce-backed implementation of {@link RedisStreamBus} providing real XADD, XREADGROUP, XACK, and XAUTOCLAIM.
 */
public final class LettuceRedisStreamBus implements RedisStreamBus {

    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> commands;
    private final RateLimitedWarner warner;

    public LettuceRedisStreamBus(RedisClient client, Consumer<String> warn) {
        Objects.requireNonNull(client, "client must not be null");
        this.warner = new RateLimitedWarner(Objects.requireNonNull(warn, "warn"), 60_000L, System::currentTimeMillis);
        this.connection = client.connect();
        this.commands = connection.sync();
    }

    public LettuceRedisStreamBus(StatefulRedisConnection<String, String> connection, Consumer<String> warn) {
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
        this.commands = connection.sync();
        this.warner = new RateLimitedWarner(Objects.requireNonNull(warn, "warn"), 60_000L, System::currentTimeMillis);
    }

    @Override
    public String xadd(String streamKey, Map<String, String> body) {
        Objects.requireNonNull(streamKey, "streamKey");
        Objects.requireNonNull(body, "body");
        try {
            return commands.xadd(streamKey, body);
        } catch (Exception e) {
            warner.warn("redis xadd failed on stream " + streamKey + ": " + e.getMessage());
            throw new RuntimeException("Redis xadd failed", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<StreamEntry> xreadgroup(String streamKey, String group, String consumer, int count, Duration block) {
        Objects.requireNonNull(streamKey, "streamKey");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(consumer, "consumer");

        createGroupIfNotExists(streamKey, group);

        try {
            XReadArgs args = XReadArgs.Builder.count(count).block(block);
            io.lettuce.core.Consumer<String> lettuceConsumer = io.lettuce.core.Consumer.from(group, consumer);
            List<io.lettuce.core.StreamMessage<String, String>> messages =
                    commands.xreadgroup(lettuceConsumer, args, StreamOffset.lastConsumed(streamKey));

            if (messages == null || messages.isEmpty()) {
                return Collections.emptyList();
            }

            List<StreamEntry> entries = new ArrayList<>(messages.size());
            for (io.lettuce.core.StreamMessage<String, String> msg : messages) {
                entries.add(new StreamEntry(msg.getId(), msg.getBody()));
            }
            return Collections.unmodifiableList(entries);
        } catch (Exception e) {
            warner.warn("redis xreadgroup failed on stream " + streamKey + ": " + e.getMessage());
            throw new RuntimeException("Redis xreadgroup failed", e);
        }
    }

    @Override
    public long xack(String streamKey, String group, String... messageIds) {
        Objects.requireNonNull(streamKey, "streamKey");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(messageIds, "messageIds");
        if (messageIds.length == 0) {
            return 0L;
        }
        try {
            Long count = commands.xack(streamKey, group, messageIds);
            return count != null ? count : 0L;
        } catch (Exception e) {
            warner.warn("redis xack failed on stream " + streamKey + ": " + e.getMessage());
            throw new RuntimeException("Redis xack failed", e);
        }
    }

    @Override
    public List<StreamEntry> xautoclaim(
            String streamKey, String group, String consumer, Duration minIdleTime, int count) {
        Objects.requireNonNull(streamKey, "streamKey");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(consumer, "consumer");

        createGroupIfNotExists(streamKey, group);

        try {
            XAutoClaimArgs<String> args = XAutoClaimArgs.Builder.xautoclaim(
                            io.lettuce.core.Consumer.from(group, consumer), minIdleTime, "0-0")
                    .count(count);
            ClaimedMessages<String, String> claimed = commands.xautoclaim(streamKey, args);
            if (claimed == null
                    || claimed.getMessages() == null
                    || claimed.getMessages().isEmpty()) {
                return Collections.emptyList();
            }

            List<StreamEntry> entries = new ArrayList<>(claimed.getMessages().size());
            for (io.lettuce.core.StreamMessage<String, String> msg : claimed.getMessages()) {
                entries.add(new StreamEntry(msg.getId(), msg.getBody()));
            }
            return Collections.unmodifiableList(entries);
        } catch (Exception e) {
            warner.warn("redis xautoclaim failed on stream " + streamKey + ": " + e.getMessage());
            throw new RuntimeException("Redis xautoclaim failed", e);
        }
    }

    @Override
    public void createGroupIfNotExists(String streamKey, String group) {
        try {
            commands.xgroupCreate(StreamOffset.from(streamKey, "0-0"), group, XGroupCreateArgs.Builder.mkstream(true));
        } catch (RedisBusyException e) {
            // Group already exists, expected
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                return;
            }
            warner.warn("redis xgroupCreate failed on stream " + streamKey + " / " + group + ": " + e.getMessage());
        }
    }

    @Override
    public boolean healthy() {
        return connection.isOpen();
    }

    @Override
    public void close() {
        connection.close();
    }
}
