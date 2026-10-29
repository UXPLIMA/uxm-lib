package com.uxplima.uxmlib.network;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.uxplima.uxmlib.redis.RedisBus;

/**
 * A network over Redis pub/sub, for networks that already run Redis.
 *
 * <p>Each topic is one Redis channel under a prefix, so two plugins on one Redis never read each other's
 * frames. Redis hands a publisher its own messages back and delivers a targeted frame to everyone, so the
 * reader drops its own frames and those meant for another server. Redis is trusted as the network's own
 * service: it carries no signatures, and its password is what keeps strangers out.
 */
public final class RedisNetworkBus implements Bus {

    private final String serverId;
    private final String prefix;
    private final RedisBus redis;
    private final Handlers handlers;
    private final System.Logger log;
    private final Set<String> listening = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    /**
     * @param prefix what every channel of this plugin starts with, such as {@code uxmchat}
     */
    public RedisNetworkBus(String serverId, String prefix, RedisBus redis, System.Logger log) {
        this.serverId = ServerId.check(Objects.requireNonNull(serverId, "serverId"));
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        if (prefix.isBlank()) {
            throw new IllegalArgumentException("a channel prefix must name the plugin");
        }
        this.redis = Objects.requireNonNull(redis, "redis");
        this.log = Objects.requireNonNull(log, "log");
        this.handlers = new Handlers(log);
    }

    @Override
    public String serverId() {
        return serverId;
    }

    @Override
    public void publish(String topic, byte[] payload) {
        Frame frame = new Frame(serverId, "", topic, payload);
        redis.publish(channel(topic), FrameCodec.encode(frame));
    }

    @Override
    public void send(String server, String topic, byte[] payload) {
        Frame frame = new Frame(serverId, server, topic, payload);
        redis.publish(channel(topic), FrameCodec.encode(frame));
    }

    @Override
    public void subscribe(String topic, Consumer<Frame> handler) {
        handlers.add(Objects.requireNonNull(topic, "topic"), Objects.requireNonNull(handler, "handler"));
        if (listening.add(topic)) {
            try {
                redis.subscribe(channel(topic), this::receive);
            } catch (RuntimeException down) {
                // Not listening after all, so the next subscribe to this topic tries again.
                listening.remove(topic);
                throw down;
            }
        }
    }

    @Override
    public boolean healthy() {
        return redis.healthy();
    }

    /**
     * Stop handing frames to this bus's handlers. The {@link RedisBus} is the caller's and stays open, since
     * other code may share it; the caller closes it when nothing uses it.
     */
    @Override
    public void close() {
        closed = true;
    }

    private void receive(byte[] bytes) {
        if (closed) {
            return;
        }
        Frame frame;
        try {
            frame = FrameCodec.decode(bytes);
        } catch (IllegalArgumentException unreadable) {
            log.log(System.Logger.Level.WARNING, "Dropped a Redis frame this library cannot read.", unreadable);
            return;
        }
        if (frame.origin().equals(serverId)) {
            return;
        }
        if (!frame.isBroadcast() && !frame.target().equals(serverId)) {
            return;
        }
        handlers.deliver(frame);
    }

    private String channel(String topic) {
        return prefix + ":" + topic;
    }
}
