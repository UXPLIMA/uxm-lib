package com.uxplima.uxmlib.network;

import java.util.Arrays;
import java.util.Objects;

/**
 * One message on the network: who sent it, who it is for, what it is about, and the sender's own bytes.
 *
 * <p>A class rather than a record because it carries an array, which a record would compare by identity.
 */
public final class Frame {

    private final String origin;
    private final String target;
    private final String topic;
    private final byte[] payload;

    /**
     * @param origin the id of the server that sent it
     * @param target the id of the one server it is for, or empty for every server but the origin
     * @param topic what it is about, chosen by the plugin that sends it
     * @param payload the plugin's own bytes; the bus never reads them
     */
    public Frame(String origin, String target, String topic, byte[] payload) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(payload, "payload");
        ServerId.check(origin);
        if (!target.isEmpty()) {
            ServerId.check(target);
        }
        if (topic.isBlank() || topic.length() > 64) {
            throw new IllegalArgumentException("a topic is 1 to 64 characters, got: " + topic);
        }
        this.origin = origin;
        this.target = target;
        this.topic = topic;
        this.payload = payload.clone();
    }

    public String origin() {
        return origin;
    }

    public String target() {
        return target;
    }

    public String topic() {
        return topic;
    }

    /** Whether every server but the origin receives it. */
    public boolean isBroadcast() {
        return target.isEmpty();
    }

    public byte[] payload() {
        return payload.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Frame frame
                && origin.equals(frame.origin)
                && target.equals(frame.target)
                && topic.equals(frame.topic)
                && Arrays.equals(payload, frame.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(origin, target, topic, Arrays.hashCode(payload));
    }

    @Override
    public String toString() {
        return "Frame[" + origin + " -> " + (target.isEmpty() ? "*" : target) + ", " + topic + ", " + payload.length
                + " bytes]";
    }
}
