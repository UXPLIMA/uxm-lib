package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmlib.condition.OperandResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A command verb nobody wired says so, rather than doing nothing.
 *
 * <p>{@code [console]} and {@code [player]} dispatch through a sink the caller wires. The default was
 * {@code CommandSink.noop()}, which discards the line: an operator writes {@code [console] give %player%
 * diamond}, the file is right, the log is clean, and the command never runs. That is the failure this
 * estate keeps paying for, and it is the same reason the delay seam throws when it is unwired.
 *
 * <p>Three plugins build a context with no sink today, so this is not hypothetical. None of the three
 * ships a console line of its own, which is exactly why nobody noticed: the first person to find it would
 * have been an operator writing one into a file we shipped empty.
 *
 * <p>{@code CommandSink.noop()} stays, because a test that asserts a line was reached needs a sink that
 * does nothing. What changed is the default: silence has to be asked for.
 */
class AnUnwiredCommandSinkSaysSoTest {

    @Test
    @DisplayName("an unwired console sink throws and names the seam to wire")
    void anUnwiredConsoleSinkThrows() {
        ActionContext unwired =
                ActionContext.builder(OperandResolver.identity()).build();

        assertThatThrownBy(
                        () -> ActionList.parse(List.of("[console] say hello")).run(unwired))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("consoleSink");
    }

    @Test
    @DisplayName("an unwired player sink throws and names the seam to wire")
    void anUnwiredPlayerSinkThrows() {
        ActionContext unwired =
                ActionContext.builder(OperandResolver.identity()).build();

        assertThatThrownBy(() -> ActionList.parse(List.of("[player] spawn")).run(unwired))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("playerSink");
    }

    @Test
    @DisplayName("a wired sink is handed the resolved line, with no slash")
    void awiredSinkIsHandedTheLine() {
        List<String> dispatched = new ArrayList<>();
        ActionContext wired = ActionContext.builder(OperandResolver.identity())
                .consoleSink(dispatched::add)
                .build();

        ActionList.parse(List.of("[console] /say hello")).run(wired);

        assertThat(dispatched).containsExactly("say hello");
    }

    @Test
    @DisplayName("silence is still available, and has to be asked for")
    void silenceIsStillAvailable() {
        ActionContext silent = ActionContext.builder(OperandResolver.identity())
                .consoleSink(CommandSink.noop())
                .build();

        ActionList.parse(List.of("[console] say hello")).run(silent);
    }
}
