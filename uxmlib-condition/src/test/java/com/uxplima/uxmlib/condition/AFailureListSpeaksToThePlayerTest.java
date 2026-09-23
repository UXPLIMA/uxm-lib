package com.uxplima.uxmlib.condition;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A failure list run by a condition speaks to the player the condition was about.
 *
 * <p>The context a {@code RUN_COMMANDS} entry ran in had no target, no later, no broadcast and no words, and the
 * request could not supply them. So a {@code [message]} went to nobody, a {@code [bossbar]} or a {@code [broadcast]}
 * threw the unwired error with no way for a plugin to wire it, and a line naming a key by {@code @} threw too. No
 * plugin used the policy yet, which is why nobody had met it.
 */
class AFailureListSpeaksToThePlayerTest {

    private static final Condition FAIL = request -> false;

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * A failure list's text is painted the way the request says. The request carried the words and not the
     * style, so a plugin's own theme reached its effect lines and never its refusals.
     */
    @Test
    @DisplayName("a failure list's text is painted with the request's style")
    void aFailureListIsPaintedWithTheRequestsStyle() {
        PlayerMock player = server.addPlayer();
        ConditionRequest request = ConditionRequest.builder(OperandResolver.identity())
                .player(player)
                .style(line -> line.replace("<shout>", "<red>"))
                .build();

        ConditionList.builder()
                .runCommands(FAIL, List.of("[message] <shout>no entry"))
                .build()
                .test(request);

        net.kyori.adventure.text.Component shown = player.nextComponentMessage();
        assertThat(com.uxplima.uxmlib.text.Text.plain(shown)).isEqualTo("no entry");
        assertThat(shown.color()).isEqualTo(net.kyori.adventure.text.format.NamedTextColor.RED);
    }

    @Test
    @DisplayName("a message in a failure list reaches the player, and a key is read from the request's words")
    void aMessageReachesThePlayer() {
        PlayerMock player = server.addPlayer();
        ConditionRequest request = ConditionRequest.builder(OperandResolver.identity())
                .player(player)
                .words(Map.of("gate.closed", "The gate is closed")::get)
                .build();

        ConditionList.builder()
                .runCommands(FAIL, List.of("[message] you may not pass", "[message] @gate.closed"))
                .build()
                .test(request);

        assertThat(player.nextMessage()).contains("you may not pass");
        assertThat(player.nextMessage()).contains("The gate is closed");
    }

    @Test
    @DisplayName("a boss bar and a broadcast in a failure list run through what the request supplies")
    void aBarAndABroadcastRun() {
        PlayerMock player = server.addPlayer();
        PlayerMock other = server.addPlayer();
        List<Duration> scheduled = new ArrayList<>();
        ConditionRequest request = ConditionRequest.builder(OperandResolver.identity())
                .player(player)
                .later((delay, task) -> scheduled.add(delay))
                .broadcast(net.kyori.adventure.audience.Audience.audience(player, other))
                .build();

        ConditionList.builder()
                .runCommands(FAIL, List.of("[bossbar] 3 RED PROGRESS | closed", "[broadcast] somebody was stopped"))
                .build()
                .test(request);

        assertThat(scheduled).hasSize(1);
        assertThat(other.nextMessage()).contains("somebody was stopped");
    }
}
