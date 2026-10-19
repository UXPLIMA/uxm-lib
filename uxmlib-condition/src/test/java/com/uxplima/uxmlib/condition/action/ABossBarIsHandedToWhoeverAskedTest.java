package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

import net.kyori.adventure.bossbar.BossBar;

import com.uxplima.uxmlib.condition.ConditionRequest;
import com.uxplima.uxmlib.condition.OperandResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * A plugin that shows a bar can take it down before its time.
 *
 * <p>A bar stays its seconds. A wait that ends early, a login before the timeout or a crate opened before its
 * countdown, left the bar on the screen telling the player to hurry. The bar is handed to whoever built the
 * context as it goes up, so the plugin that knows the wait is over can hide it.
 */
class ABossBarIsHandedToWhoeverAskedTest {

    @Test
    @DisplayName("the bar a line shows is the bar handed over, with the player it went to")
    void theShownBarIsHandedOver() {
        Player player = mock(Player.class);
        List<BossBar> handed = new ArrayList<>();
        ActionContext context = ActionContext.builder(OperandResolver.identity())
                .player(player)
                .target(player)
                .later((delay, what) -> {})
                .barShown((to, bar) -> {
                    assertThat(to).isSameAs(player);
                    handed.add(bar);
                })
                .build();

        ActionList.parse(List.of("[bossbar] 5 red | Waiting")).run(context);

        ArgumentCaptor<BossBar> shown = ArgumentCaptor.forClass(BossBar.class);
        verify(player).showBossBar(shown.capture());
        assertThat(handed).containsExactly(shown.getValue());
    }

    @Test
    @DisplayName("a context turned into a request keeps where its bars are handed")
    void theRequestKeepsTheBarSeam() {
        List<BossBar> handed = new ArrayList<>();
        ConditionRequest request = ActionContext.builder(OperandResolver.identity())
                .later((delay, what) -> {})
                .barShown((to, bar) -> handed.add(bar))
                .build()
                .asConditionRequest();

        BossBar bar = BossBar.bossBar(
                net.kyori.adventure.text.Component.empty(), 1.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        request.barShown().orElseThrow().accept(mock(Player.class), bar);

        assertThat(handed).containsExactly(bar);
    }
}
