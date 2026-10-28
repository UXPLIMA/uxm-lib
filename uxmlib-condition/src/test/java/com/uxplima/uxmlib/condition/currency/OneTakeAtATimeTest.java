package com.uxplima.uxmlib.condition.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** Two takes for one player never both pass one balance check. */
class OneTakeAtATimeTest {

    private static final CurrencyId COINS = new CurrencyId("coins");
    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void twoTakesAtOnceForOnePlayerNeverOverdraw() throws InterruptedException {
        SlowCurrency slow = new SlowCurrency(new BigDecimal("100"), null);
        OneTakeAtATime.Takes takes = new OneTakeAtATime.Takes();
        Currency first = new OneTakeAtATime(slow, takes, () -> false);
        Currency second = new OneTakeAtATime(slow, takes, () -> false);
        AtomicInteger passed = new AtomicInteger();

        Thread one = Thread.ofVirtual().start(() -> count(first.take(STEVE, Money.of(COINS, "80")), passed));
        Thread two = Thread.ofVirtual().start(() -> count(second.take(STEVE, Money.of(COINS, "80")), passed));
        one.join();
        two.join();

        assertThat(passed).hasValue(1);
        assertThat(slow.balanceOf(STEVE)).isEqualTo(Money.of(COINS, "20"));
        assertThat(takes.isEmpty()).isTrue();
    }

    @Test
    void aServerThreadThatFindsThePlayerBusyIsRefusedRatherThanWaiting() throws InterruptedException {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SlowCurrency held = new SlowCurrency(new BigDecimal("100"), () -> {
            inside.countDown();
            await(release);
        });
        OneTakeAtATime.Takes takes = new OneTakeAtATime.Takes();
        Thread worker = Thread.ofVirtual()
                .start(() -> new OneTakeAtATime(held, takes, () -> false).take(STEVE, Money.of(COINS, "10")));
        await(inside);

        long started = System.nanoTime();
        boolean taken = new OneTakeAtATime(held, takes, () -> true).take(STEVE, Money.of(COINS, "10"));

        assertThat(taken).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
        release.countDown();
        worker.join();
    }

    private static void count(boolean taken, AtomicInteger passed) {
        if (taken) {
            passed.incrementAndGet();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Reads the balance, lingers, then takes: the two steps a real backend has. */
    private static final class SlowCurrency implements Currency {

        private final Runnable during;
        private volatile BigDecimal balance;

        SlowCurrency(BigDecimal balance, @org.jspecify.annotations.Nullable Runnable during) {
            this.balance = balance;
            this.during = during == null ? () -> sleep() : during;
        }

        @Override
        public CurrencyId id() {
            return COINS;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public boolean paysOffline() {
            return true;
        }

        @Override
        public boolean isWhole() {
            return false;
        }

        @Override
        public Money balanceOf(UUID player) {
            return new Money(COINS, balance);
        }

        @Override
        public boolean take(UUID player, Money amount) {
            BigDecimal read = balance;
            during.run();
            if (read.compareTo(amount.amount()) < 0) {
                return false;
            }
            balance = read.subtract(amount.amount());
            return true;
        }

        @Override
        public boolean give(UUID player, Money amount) {
            balance = balance.add(amount.amount());
            return true;
        }

        private static void sleep() {
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
