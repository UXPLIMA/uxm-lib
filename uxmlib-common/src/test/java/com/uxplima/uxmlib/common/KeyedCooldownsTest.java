package com.uxplima.uxmlib.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeyedCooldownsTest {

    private static final Duration WAIT = Duration.ofSeconds(10L);

    private final AtomicLong now = new AtomicLong();
    private final KeyedCooldowns<String> cooldowns = KeyedCooldowns.withClock(now::get);
    private final UUID player = UUID.randomUUID();

    @Test
    @DisplayName("the first take goes through and the second one waits")
    void theFirstTakeGoesThrough() {
        assertThat(cooldowns.take(player, "kit", WAIT)).isZero();
        assertThat(cooldowns.take(player, "kit", WAIT)).isEqualTo(WAIT);
    }

    @Test
    @DisplayName("a wait runs down with the clock and ends exactly when it says")
    void aWaitRunsDown() {
        cooldowns.take(player, "kit", WAIT);

        now.addAndGet(4_000L);
        assertThat(cooldowns.remaining(player, "kit")).isEqualTo(Duration.ofSeconds(6L));

        now.addAndGet(6_000L);
        assertThat(cooldowns.remaining(player, "kit")).isZero();
        assertThat(cooldowns.take(player, "kit", WAIT)).isZero();
    }

    @Test
    @DisplayName("a refused take does not push the wait further out")
    void aRefusedTakeDoesNotPushTheWaitOut() {
        cooldowns.take(player, "kit", WAIT);

        now.addAndGet(9_000L);
        assertThat(cooldowns.take(player, "kit", WAIT)).isEqualTo(Duration.ofSeconds(1L));

        now.addAndGet(1_000L);
        assertThat(cooldowns.take(player, "kit", WAIT)).isZero();
    }

    @Test
    @DisplayName("one key does not hold another key back")
    void oneKeyDoesNotHoldAnotherBack() {
        cooldowns.take(player, "kit", WAIT);

        assertThat(cooldowns.take(player, "warp", WAIT)).isZero();
        assertThat(cooldowns.remaining(player, "kit")).isEqualTo(WAIT);
    }

    @Test
    @DisplayName("one player does not hold another player back")
    void onePlayerDoesNotHoldAnotherBack() {
        cooldowns.take(player, "kit", WAIT);

        assertThat(cooldowns.take(UUID.randomUUID(), "kit", WAIT)).isZero();
    }

    @Test
    @DisplayName("a wait of zero stores nothing, so a cooldown switched off costs no memory")
    void aWaitOfZeroStoresNothing() {
        assertThat(cooldowns.take(player, "kit", Duration.ZERO)).isZero();
        assertThat(cooldowns.take(player, "kit", Duration.ofSeconds(-5L))).isZero();

        assertThat(cooldowns.tracked()).isZero();
    }

    @Test
    @DisplayName("starting a wait replaces whatever was left of the last one")
    void startingReplacesWhatIsLeft() {
        cooldowns.take(player, "kit", WAIT);
        now.addAndGet(9_000L);

        cooldowns.start(player, "kit", Duration.ofSeconds(2L));

        assertThat(cooldowns.remaining(player, "kit")).isEqualTo(Duration.ofSeconds(2L));
    }

    @Test
    @DisplayName("starting a wait of zero ends the wait, so an operator who turns it off is obeyed")
    void startingZeroEndsTheWait() {
        cooldowns.take(player, "kit", WAIT);

        cooldowns.start(player, "kit", Duration.ZERO);

        assertThat(cooldowns.remaining(player, "kit")).isZero();
        assertThat(cooldowns.tracked()).isZero();
    }

    @Test
    @DisplayName("clearing gives one action back and leaves the other keys alone")
    void clearingGivesOneActionBack() {
        cooldowns.take(player, "kit", WAIT);
        cooldowns.take(player, "warp", WAIT);

        cooldowns.clear(player, "kit");

        assertThat(cooldowns.remaining(player, "kit")).isZero();
        assertThat(cooldowns.remaining(player, "warp")).isEqualTo(WAIT);
    }

    @Test
    @DisplayName("forgetting a player drops every key they hold and nobody else's")
    void forgettingDropsEveryKeyOfOnePlayer() {
        UUID other = UUID.randomUUID();
        cooldowns.take(player, "kit", WAIT);
        cooldowns.take(player, "warp", WAIT);
        cooldowns.take(other, "kit", WAIT);

        cooldowns.forget(player);

        assertThat(cooldowns.remaining(player, "kit")).isZero();
        assertThat(cooldowns.remaining(player, "warp")).isZero();
        assertThat(cooldowns.remaining(other, "kit")).isEqualTo(WAIT);
    }

    @Test
    @DisplayName("the purge drops what has run out and keeps what has not")
    void thePurgeDropsWhatHasRunOut() {
        cooldowns.take(player, "kit", Duration.ofSeconds(5L));
        cooldowns.take(player, "warp", Duration.ofSeconds(30L));

        now.addAndGet(10_000L);

        assertThat(cooldowns.purgeExpired()).isEqualTo(1);
        assertThat(cooldowns.tracked()).isEqualTo(1);
        assertThat(cooldowns.remaining(player, "warp")).isEqualTo(Duration.ofSeconds(20L));
    }
}
