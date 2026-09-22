package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.condition.OperandResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A broadcast nobody wired says so, rather than reaching nobody.
 *
 * <p>The third seam of the same shape. {@code [broadcast]} sends to the audience the caller wires, and
 * the default was {@link Audience#empty()}, which has no members: the line is rendered, handed over, and
 * discarded. Nothing throws and nothing is logged, and the operator reads their own file back and sees
 * an announcement written out in full.
 *
 * <p>It happened. uxmCrates ships three of them, the supply drop envoy whose whole feature is telling
 * the server that crates are falling, and its context wired no audience: the announcement reached nobody
 * for as long as the feature has existed.
 *
 * <p>{@code Audience.empty()} stays, and a caller who means an announcement that goes nowhere writes it.
 * That is the same rule the command sinks got: silence is asked for, never inherited.
 */
class AnUnwiredBroadcastSaysSoTest {

    @Test
    @DisplayName("an unwired broadcast throws and names the seam to wire")
    void anUnwiredBroadcastThrows() {
        ActionContext unwired =
                ActionContext.builder(OperandResolver.identity()).build();

        assertThatThrownBy(() -> ActionList.parse(List.of("[broadcast] the drops are falling"))
                        .run(unwired))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("broadcast");
    }

    @Test
    @DisplayName("a wired audience is handed the rendered line")
    void awiredAudienceIsHandedTheLine() {
        List<String> heard = new ArrayList<>();
        ActionContext wired = ActionContext.builder(OperandResolver.identity())
                .broadcast(listening(heard))
                .build();

        ActionList.parse(List.of("[broadcast] the drops are falling")).run(wired);

        assertThat(heard).containsExactly("the drops are falling");
    }

    @Test
    @DisplayName("an announcement that goes nowhere is still available, and has to be asked for")
    void silenceIsStillAvailable() {
        ActionContext silent = ActionContext.builder(OperandResolver.identity())
                .broadcast(Audience.empty())
                .build();

        ActionList.parse(List.of("[broadcast] nobody hears this")).run(silent);
    }

    /** An audience that records what it is told, in plain text. */
    private static Audience listening(List<String> heard) {
        return new Audience() {

            @Override
            public void sendMessage(Component message) {
                heard.add(PlainTextComponentSerializer.plainText().serialize(message));
            }
        };
    }
}
