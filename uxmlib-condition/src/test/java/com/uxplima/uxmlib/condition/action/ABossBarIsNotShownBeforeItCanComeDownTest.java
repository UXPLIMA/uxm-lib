package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

import net.kyori.adventure.bossbar.BossBar;

import com.uxplima.uxmlib.condition.OperandResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A bar is put on a player only once something can take it off again.
 *
 * <p>{@code [bossbar]} is the one verb that happens twice: it shows a bar and it has to hide it later. The
 * delay is a seam the caller wires to its own scheduler, and left unwired the context throws by design,
 * because a verb an operator wrote that silently does nothing is the failure this estate keeps paying for.
 *
 * <p>The order of those two steps is the whole of this test. Showing first and asking second means an
 * unwired plugin leaves the bar on the player until they log out, and loses every line written after it in
 * the same list. Asking first and showing second costs the same list and leaves the player's screen alone,
 * which is the difference between a defect an operator can fix and one their players see all evening.
 *
 * <p>Nineteen plugins were wired for this in one sweep and one still is not, so the unwired case is not
 * hypothetical: it is what uxmPlots does today.
 */
class ABossBarIsNotShownBeforeItCanComeDownTest {

    @Test
    @DisplayName("an unwired delay throws before the bar reaches the player")
    void nothingIsShownWhenTheDelayIsUnwired() {
        Player player = mock(Player.class);
        ActionContext unwired = ActionContext.builder(OperandResolver.identity())
                .player(player)
                .target(player)
                .build();

        assertThatThrownBy(() -> ActionList.parse(List.of("[bossbar] 5 RED PROGRESS | Opening"))
                        .run(unwired))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("later");

        verify(player, never()).showBossBar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("a wired delay shows the bar and hands the hide to the caller")
    void theBarIsShownAndTheHideIsHandedOver() {
        Player player = mock(Player.class);
        List<Duration> delays = new ArrayList<>();
        List<Runnable> work = new ArrayList<>();
        ActionContext wired = ActionContext.builder(OperandResolver.identity())
                .player(player)
                .target(player)
                .later((delay, what) -> {
                    delays.add(delay);
                    work.add(what);
                })
                .build();

        ActionList.parse(List.of("[bossbar] 5 RED PROGRESS | Opening")).run(wired);

        verify(player).showBossBar(org.mockito.ArgumentMatchers.any(BossBar.class));
        assertThat(delays).containsExactly(Duration.ofSeconds(5));

        work.get(0).run();
        verify(player).hideBossBar(org.mockito.ArgumentMatchers.any(BossBar.class));
    }
}
