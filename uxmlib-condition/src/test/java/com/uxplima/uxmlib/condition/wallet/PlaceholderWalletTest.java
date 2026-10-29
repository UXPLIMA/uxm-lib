package com.uxplima.uxmlib.condition.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class PlaceholderWalletTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final Map<UUID, String> balances = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final List<String> console = new CopyOnWriteArrayList<>();

    private PlaceholderWallet wallet(PlaceholderWallet.Pool pool) {
        return new PlaceholderWallet(
                Map.of("tokens", pool),
                (player, placeholder) -> Optional.ofNullable(balances.get(player)),
                console::add,
                player -> Optional.ofNullable(names.get(player)));
    }

    private PlaceholderWallet tokens() {
        return wallet(new PlaceholderWallet.Pool("%tokens%", "tokens take {player} {amount}", ",")
                .paying("tokens give {uuid} {amount}"));
    }

    @Test
    void anOfflinePlayerIsReadAndTakenFromByIdWithEveryDigitOfTheSum() {
        balances.put(STEVE, "1,250.75");
        names.put(STEVE, "Steve");

        assertThat(tokens().balanceExact(STEVE, "tokens")).isEqualByComparingTo("1250.75");
        assertThat(tokens().withdrawExact(STEVE, "tokens", new BigDecimal("0.10")))
                .isTrue();
        assertThat(console).containsExactly("tokens take Steve 0.1");
    }

    @Test
    void aTakeIsRefusedWithoutALineWhenThePlayerCannotAffordItOrHasNoName() {
        balances.put(STEVE, "5");

        assertThat(tokens().withdrawExact(STEVE, "tokens", BigDecimal.TEN)).isFalse();
        assertThat(tokens().withdrawExact(STEVE, "tokens", BigDecimal.ONE)).isFalse();
        assertThat(console).isEmpty();
    }

    @Test
    void aPaymentNeedsNoNameWhenTheLineTakesTheIdAndIsRefusedWhenThePoolHasNoGive() {
        assertThat(tokens().depositExact(STEVE, "tokens", new BigDecimal("3"))).isTrue();
        assertThat(console).containsExactly("tokens give " + STEVE + " 3");

        PlaceholderWallet takeOnly = wallet(PlaceholderWallet.Pool.of("%tokens%", "tokens take {player} {amount}"));
        assertThat(takeOnly.depositExact(STEVE, "tokens", BigDecimal.ONE)).isFalse();
    }

    @Test
    void twoTakesAtOnceForOnePlayerNeverBothPassTheBalanceCheck() throws InterruptedException {
        names.put(STEVE, "Steve");
        CountDownLatch bothAsked = new CountDownLatch(2);
        AtomicInteger balance = new AtomicInteger(100);
        PlaceholderWallet slow = new PlaceholderWallet(
                Map.of("tokens", PlaceholderWallet.Pool.of("%tokens%", "tokens take {player} {amount}")),
                (player, placeholder) -> {
                    // The balance is read, then the read waits a moment for the other take, so two takes
                    // that were not held apart would both have read 100.
                    String read = Integer.toString(balance.get());
                    bothAsked.countDown();
                    awaitBriefly(bothAsked);
                    return Optional.of(read);
                },
                line -> {
                    balance.addAndGet(-80);
                    return console.add(line);
                },
                player -> Optional.ofNullable(names.get(player)));
        AtomicInteger passed = new AtomicInteger();

        Thread one = Thread.ofVirtual()
                .start(() -> count(slow.withdrawExact(STEVE, "tokens", new BigDecimal("80")), passed));
        Thread two = Thread.ofVirtual()
                .start(() -> count(slow.withdrawExact(STEVE, "tokens", new BigDecimal("80")), passed));
        one.join();
        two.join();

        assertThat(passed).hasValue(1);
        assertThat(balance).hasValue(20);
    }

    private static void count(boolean taken, AtomicInteger passed) {
        if (taken) {
            passed.incrementAndGet();
        }
    }

    private static void awaitBriefly(CountDownLatch latch) {
        try {
            latch.await(300, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void anUnknownPoolReadsZeroAndRefuses() {
        balances.put(STEVE, "100");

        assertThat(tokens().reaches("gems")).isFalse();
        assertThat(tokens().balanceExact(STEVE, "gems")).isZero();
        assertThat(tokens().withdrawExact(STEVE, "gems", BigDecimal.ONE)).isFalse();
        assertThat(tokens().depositExact(STEVE, "gems", BigDecimal.ONE)).isFalse();
        assertThat(console).isEmpty();
    }
}
