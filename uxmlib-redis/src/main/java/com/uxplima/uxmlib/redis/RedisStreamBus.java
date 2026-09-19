package com.uxplima.uxmlib.redis;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Enterprise Redis Streams abstraction providing real XADD, XREADGROUP, XACK, and XAUTOCLAIM operations.
 */
public interface RedisStreamBus extends AutoCloseable {

    /**
     * Appends an entry to the specified stream key (XADD).
     *
     * @param streamKey stream name/key
     * @param body map of key-value fields to store
     * @return generated stream message ID
     */
    String xadd(String streamKey, Map<String, String> body);

    /**
     * Reads messages from a stream using a consumer group (XREADGROUP).
     *
     * @param streamKey stream name/key
     * @param group consumer group name
     * @param consumer consumer name within group
     * @param count maximum number of messages to fetch
     * @param block blocking duration (poll timeout)
     * @return list of retrieved stream messages
     */
    List<StreamEntry> xreadgroup(String streamKey, String group, String consumer, int count, Duration block);

    /**
     * Acknowledges successfully processed messages (XACK).
     *
     * @param streamKey stream name/key
     * @param group consumer group name
     * @param messageIds message IDs to acknowledge
     * @return number of messages acknowledged
     */
    long xack(String streamKey, String group, String... messageIds);

    /**
     * Claims pending messages from idle consumers (XAUTOCLAIM).
     *
     * @param streamKey stream name/key
     * @param group consumer group name
     * @param consumer consumer claiming messages
     * @param minIdleTime minimum idle duration before a message is eligible
     * @param count maximum messages to claim
     * @return list of claimed stream messages
     */
    List<StreamEntry> xautoclaim(String streamKey, String group, String consumer, Duration minIdleTime, int count);

    /**
     * Creates a consumer group on the stream if it does not exist (XGROUP CREATE with MKSTREAM).
     *
     * @param streamKey stream name/key
     * @param group consumer group name
     */
    void createGroupIfNotExists(String streamKey, String group);

    /**
     * Checks if the underlying Redis connection is open and healthy.
     */
    default boolean healthy() {
        return true;
    }

    /**
     * Represents a single message entry in a Redis Stream.
     *
     * @param id message ID (e.g. 1526972795262-0)
     * @param body map of key-value pairs stored in the stream entry
     */
    record StreamEntry(String id, Map<String, String> body) {}

    @Override
    void close();
}
