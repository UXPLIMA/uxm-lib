package com.uxplima.uxmlib.network;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;

/**
 * Frames waiting for a connection, oldest first, never more than a fixed number nor a fixed size.
 *
 * <p>A network that loses its link for a minute should not lose every chat line of that minute, and a
 * link that never comes back should not grow the heap until the server dies. The oldest frame goes first
 * when the box is full, because the newest is the one a player is still looking at. Every count changes
 * under the box's own lock, so a frame taken by the writer is never also counted as dropped.
 */
final class Outbox {

    private final ArrayDeque<byte[]> waiting = new ArrayDeque<>();
    private final int capacity;
    private final long maxBytes;
    private long bytes;
    private long dropped;

    /**
     * @param capacity the most frames that wait
     * @param maxBytes the most bytes that wait, so a few large frames cannot hold what many small ones would
     */
    Outbox(int capacity, long maxBytes) {
        this.capacity = capacity;
        this.maxBytes = maxBytes;
    }

    /** Queue {@code body}; answers whether an older frame was dropped to make room. */
    synchronized boolean offer(byte[] body) {
        boolean dropping = false;
        while (!waiting.isEmpty() && (waiting.size() >= capacity || bytes + body.length > maxBytes)) {
            byte[] oldest = waiting.pollFirst();
            bytes -= oldest.length;
            dropped++;
            dropping = true;
        }
        waiting.addLast(body);
        bytes += body.length;
        notifyAll();
        return dropping;
    }

    /** The oldest frame, waiting up to {@code waitMillis} for one; null when none came. */
    synchronized byte @Nullable [] next(long waitMillis) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);
        while (waiting.isEmpty()) {
            long left = until - System.nanoTime();
            if (left <= 0) {
                return null;
            }
            TimeUnit.NANOSECONDS.timedWait(this, left);
        }
        byte[] body = waiting.pollFirst();
        bytes -= body.length;
        return body;
    }

    synchronized boolean isEmpty() {
        return waiting.isEmpty();
    }

    synchronized long dropped() {
        return dropped;
    }
}
