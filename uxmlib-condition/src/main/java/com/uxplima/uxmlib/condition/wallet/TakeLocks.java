package com.uxplima.uxmlib.condition.wallet;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

import org.bukkit.Bukkit;

import org.jspecify.annotations.Nullable;

/**
 * One take at a time for each player.
 *
 * <p>Most backends take in two steps, a balance read and then the take, and two takes for one player that
 * interleave would both read the old balance and both pass. A take held here runs alone for its player;
 * takes for other players are never held up.
 *
 * <p>A server thread never waits for the lock: a worker holding it may itself be waiting for that thread,
 * so a take there that finds the player busy is refused instead. A worker waits a bounded time. A lock is
 * dropped as soon as nobody holds or waits for it, so the map holds only the players taking right now.
 */
public final class TakeLocks {

    /** Longer than a take that hops twice and asks an economy a few times, shorter than a stuck worker. */
    public static final Duration WAIT = Duration.ofSeconds(30);

    private final ConcurrentHashMap<UUID, Held> held = new ConcurrentHashMap<>();
    private final BooleanSupplier onServerThread;

    /** Locks that treat a thread the running server ticks on as a server thread. */
    public TakeLocks() {
        this(() -> Bukkit.getServer() != null && Bukkit.isPrimaryThread());
    }

    /** @param onServerThread whether the calling thread is one that must never wait */
    public TakeLocks(BooleanSupplier onServerThread) {
        this.onServerThread = Objects.requireNonNull(onServerThread, "onServerThread");
    }

    /**
     * Run {@code take} alone for {@code player} and answer what it answered; false when the lock could not
     * be had, in which case {@code take} never ran.
     */
    public boolean holding(UUID player, BooleanSupplier take) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(take, "take");
        Duration wait = onServerThread.getAsBoolean() ? Duration.ZERO : WAIT;
        Held lock = held.compute(player, (id, current) -> current == null ? new Held() : current.join());
        try {
            if (!lock.lock.tryLock(wait.toMillis(), TimeUnit.MILLISECONDS)) {
                leave(player);
                return false;
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            leave(player);
            return false;
        }
        try {
            return take.getAsBoolean();
        } finally {
            lock.lock.unlock();
            leave(player);
        }
    }

    private void leave(UUID player) {
        held.computeIfPresent(player, (id, current) -> current.leave());
    }

    /** Whether no player's lock is kept. */
    public boolean isEmpty() {
        return held.isEmpty();
    }

    /** One player's lock and how many takes are holding or waiting for it; changed only inside compute. */
    private static final class Held {

        private final ReentrantLock lock = new ReentrantLock();
        private int users = 1;

        Held join() {
            users++;
            return this;
        }

        @Nullable Held leave() {
            users--;
            return users == 0 ? null : this;
        }
    }
}
