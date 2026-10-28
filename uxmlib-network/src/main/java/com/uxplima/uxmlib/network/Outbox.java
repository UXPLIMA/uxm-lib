package com.uxplima.uxmlib.network;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;

/**
 * Frames waiting for a connection, oldest first, never more than a fixed number.
 *
 * <p>A network that loses its link for a minute should not lose every chat line of that minute, and a
 * link that never comes back should not grow the heap until the server dies. The oldest frame goes first
 * when the box is full, because the newest is the one a player is still looking at.
 */
final class Outbox {

    private final LinkedBlockingDeque<byte[]> waiting;
    private long dropped;

    Outbox(int capacity) {
        this.waiting = new LinkedBlockingDeque<>(capacity);
    }

    /** Queue {@code body}; answers whether an older frame was dropped to make room. */
    synchronized boolean offer(byte[] body) {
        boolean dropping = false;
        while (!waiting.offerLast(body)) {
            waiting.pollFirst();
            dropped++;
            dropping = true;
        }
        return dropping;
    }

    byte @Nullable [] next(long waitMillis) throws InterruptedException {
        return waiting.pollFirst(waitMillis, TimeUnit.MILLISECONDS);
    }

    /** Put back a frame a dead connection could not send, so the next one sends it first. */
    void putBack(byte[] body) {
        waiting.offerFirst(body);
    }

    synchronized long dropped() {
        return dropped;
    }
}
