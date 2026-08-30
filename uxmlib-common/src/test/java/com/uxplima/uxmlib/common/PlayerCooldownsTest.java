package com.uxplima.uxmlib.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlayerCooldownsTest {

    private static final Duration WAIT = Duration.ofSeconds(4L);

    private final AtomicLong now = new AtomicLong();
    private final PlayerCooldowns cooldowns = PlayerCooldowns.withClock(now::get);
    private final UUID player = UUID.randomUUID();

    @Test
    @DisplayName("the first take goes through and the second one waits")
    void theFirstTakeGoesThrough() {
        assertThat(cooldowns.take(player, WAIT)).isZero();
        assertThat(cooldowns.take(player, WAIT)).isEqualTo(WAIT);
    }

    @Test
    @DisplayName("the wait ends when the clock says it does")
    void theWaitEnds() {
        cooldowns.take(player, WAIT);

        now.addAndGet(4_000L);

        assertThat(cooldowns.remaining(player)).isZero();
        assertThat(cooldowns.take(player, WAIT)).isZero();
    }

    @Test
    @DisplayName("one player does not hold another player back")
    void onePlayerDoesNotHoldAnotherBack() {
        cooldowns.take(player, WAIT);

        assertThat(cooldowns.take(UUID.randomUUID(), WAIT)).isZero();
    }

    @Test
    @DisplayName("clearing gives the action back and drops the player")
    void clearingGivesTheActionBack() {
        cooldowns.take(player, WAIT);
        cooldowns.clear(player);
        assertThat(cooldowns.remaining(player)).isZero();

        cooldowns.take(player, WAIT);
        cooldowns.clear(player);
        assertThat(cooldowns.tracked()).isZero();
    }

    @Test
    @DisplayName("the purge drops a wait that has run out")
    void thePurgeDropsWhatHasRunOut() {
        cooldowns.take(player, WAIT);

        now.addAndGet(5_000L);

        assertThat(cooldowns.purgeExpired()).isEqualTo(1);
        assertThat(cooldowns.tracked()).isZero();
    }
}
