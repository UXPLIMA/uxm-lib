package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import net.kyori.adventure.bossbar.BossBar;

import com.uxplima.uxmlib.condition.OperandResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * A bar that counts down empties with its own time.
 *
 * <p>A wait with a limit (a login, a crate, a cooldown) wants the bar to say how much is left by how full it is.
 * A bar that stays full while its text counts down reads as nothing happening, so {@code drain} after the
 * colour and the overlay empties it over the seconds it stays, through the same delay seam the hide uses.
 */
class ABossBarCanDrainTest {

    @Test
    @DisplayName("a draining bar starts full and empties step by step until it comes down")
    void aDrainingBarEmpties() {
        Player player = mock(Player.class);
        Map<Duration, Runnable> later = new LinkedHashMap<>();
        ActionContext wired = ActionContext.builder(OperandResolver.identity())
                .player(player)
                .target(player)
                .later(later::put)
                .build();

        ActionList.parse(List.of("[bossbar] 4 RED PROGRESS DRAIN | Waiting")).run(wired);

        ArgumentCaptor<BossBar> shown = ArgumentCaptor.forClass(BossBar.class);
        verify(player).showBossBar(shown.capture());
        BossBar bar = shown.getValue();
        assertThat(bar.progress()).isEqualTo(1.0f);
        assertThat(later.keySet())
                .containsExactlyInAnyOrder(
                        Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofSeconds(4));

        java.util.Objects.requireNonNull(later.get(Duration.ofSeconds(1))).run();
        assertThat(bar.progress()).isEqualTo(0.75f);
        java.util.Objects.requireNonNull(later.get(Duration.ofSeconds(3))).run();
        assertThat(bar.progress()).isEqualTo(0.25f);
        java.util.Objects.requireNonNull(later.get(Duration.ofSeconds(4))).run();
        verify(player).hideBossBar(bar);
    }

    @Test
    @DisplayName("drain may stand without the overlay, and a long bar takes at most a hundred steps")
    void drainStandsAloneAndStepsAreBounded() {
        Player player = mock(Player.class);
        Map<Duration, Runnable> later = new LinkedHashMap<>();
        ActionContext wired = ActionContext.builder(OperandResolver.identity())
                .player(player)
                .target(player)
                .later(later::put)
                .build();

        ActionList.parse(List.of("[bossbar] 600 red drain | Waiting")).run(wired);

        assertThat(later).hasSize(100);
        assertThat(later).containsKey(Duration.ofMinutes(10));
    }
}
