package com.uxplima.uxmlib.network;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;

/**
 * Frames waiting for a connection, oldest first, never more than a fixed number nor a fixed size.
 *
 * <p>A network that loses its link for a minute should not lose every chat line of that minute, and a
 * link that never comes back should not grow the heap until the server dies. The oldest frame goes first
 * when the box is full, because the newest is the one a player is still looking at.
 */
final class Outbox {

    private final LinkedBlockingDeque<byte[]> waiting;
    private final long maxBytes;
    private long bytes;
    private long dropped;

    /**
     * @param capacity the most frames that wait
     * @param maxBytes the most bytes that wait, so a few large frames cannot hold what many small ones would
     */
    Outbox(int capacity, long maxBytes) {
        this.waiting = new LinkedBlockingDeque<>(capacity);
        this.maxBytes = maxBytes;
    }

    /** Queue {@code body}; answers whether an older frame was dropped to make room. */
    synchronized boolean offer(byte[] body) {
        boolean dropping = false;
        while (!waiting.isEmpty() && (waiting.remainingCapacity() == 0 || bytes + body.length > maxBytes)) {
            byte[] oldest = waiting.pollFirst();
            if (oldest != null) {
                bytes -= oldest.length;
            }
            dropped++;
            dropping = true;
        }
        if (waiting.offerLast(body)) {
            bytes += body.length;
        }
        return dropping;
    }

    byte @Nullable [] next(long waitMillis) throws InterruptedException {
        byte[] body = waiting.pollFirst(waitMillis, TimeUnit.MILLISECONDS);
        if (body != null) {
            synchronized (this) {
                bytes -= body.length;
            }
        }
        return body;
    }

    /** Put back a frame a dead connection could not send, so the next one sends it first. */
    synchronized void putBack(byte[] body) {
        if (waiting.offerFirst(body)) {
            bytes += body.length;
        }
    }

    synchronized long dropped() {
        return dropped;
    }
}
