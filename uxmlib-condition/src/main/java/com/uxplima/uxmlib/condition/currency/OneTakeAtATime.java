package com.uxplima.uxmlib.condition.currency;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

/**
 * A currency whose takes for one player run one at a time.
 *
 * <p>Most backends take in two steps, a balance read and then the take, and two takes for one player that
 * interleave would both read the old balance and both pass. Every currency {@link CurrencyBuilder} builds is
 * wrapped in this, all sharing one {@link Takes}, so two currencies over one economy cannot race either.
 *
 * <p>A server thread never waits for the lock: a worker holding it may itself be waiting for that thread,
 * so a take there that finds the player busy is refused instead. A worker waits a bounded time.
 */
final class OneTakeAtATime implements Currency {

    /** Longer than a take that hops twice and asks Treasury a few times, shorter than a stuck worker. */
    static final Duration WAIT = Duration.ofSeconds(30);

    private final Currency inner;
    private final Takes takes;
    private final BooleanSupplier onServerThread;

    OneTakeAtATime(Currency inner, Takes takes, BooleanSupplier onServerThread) {
        this.inner = Objects.requireNonNull(inner, "inner");
        this.takes = Objects.requireNonNull(takes, "takes");
        this.onServerThread = Objects.requireNonNull(onServerThread, "onServerThread");
    }

    @Override
    public CurrencyId id() {
        return inner.id();
    }

    @Override
    public boolean isReady() {
        return inner.isReady();
    }

    @Override
    public boolean paysOffline() {
        return inner.paysOffline();
    }

    @Override
    public boolean isWhole() {
        return inner.isWhole();
    }

    @Override
    public Money balanceOf(UUID player) {
        return inner.balanceOf(player);
    }

    @Override
    public boolean take(UUID player, Money amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(amount, "amount");
        Duration wait = onServerThread.getAsBoolean() ? Duration.ZERO : WAIT;
        return takes.holding(player, wait, () -> inner.take(player, amount));
    }

    @Override
    public boolean give(UUID player, Money amount) {
        return inner.give(player, amount);
    }

    /** The take locks of every player who is taking right now; a lock is dropped when nobody holds it. */
    static final class Takes {

        private final ConcurrentHashMap<UUID, Held> held = new ConcurrentHashMap<>();

        boolean holding(UUID player, Duration wait, BooleanSupplier step) {
            Held lock = held.compute(player, (id, current) -> current == null ? new Held() : current.join());
            try {
                if (!lock.lock.tryLock(wait.toMillis(), TimeUnit.MILLISECONDS)) {
                    return false;
                }
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                leave(player);
                return false;
            }
            try {
                return step.getAsBoolean();
            } finally {
                lock.lock.unlock();
                leave(player);
            }
        }

        private void leave(UUID player) {
            held.computeIfPresent(player, (id, current) -> current.leave());
        }

        /** Whether any player's lock is still kept. */
        boolean isEmpty() {
            return held.isEmpty();
        }
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
