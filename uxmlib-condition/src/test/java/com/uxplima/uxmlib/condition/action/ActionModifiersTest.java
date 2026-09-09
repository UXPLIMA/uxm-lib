package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.audience.Audience;

import com.uxplima.uxmlib.condition.OperandResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * When an action runs, and how often.
 *
 * <p>An action list was a flat list of verbs: every line ran, once, immediately, for everybody. Three
 * products in this market moved past that years ago, and it is what forces an operator to ask us to add "and
 * if they are a first timer, say this too" as a feature instead of writing it.
 *
 * <p>The rule that matters most is the one about files that already exist: a line with no brace block after
 * its verb parses exactly as it did before, so nothing anybody has written changes.
 */
class ActionModifiersTest {

    /** A context that remembers what it was asked to say and to schedule, and runs the wait at once. */
    private static final class Recording {

        private final List<String> said = new ArrayList<>();
        private final List<Duration> waits = new ArrayList<>();
        private boolean runsWaits = true;

        private ActionContext context() {
            Audience audience = new Audience() {

                @Override
                public void sendMessage(net.kyori.adventure.text.Component message) {
                    said.add(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                            .serialize(message));
                }
            };
            return ActionContext.builder(OperandResolver.identity())
                    .target(audience)
                    .broadcast(audience)
                    .later((delay, what) -> {
                        waits.add(delay);
                        if (runsWaits) {
                            what.run();
                        }
                    })
                    .build();
        }
    }

    @Test
    @DisplayName("a line with no brace block parses and runs exactly as it always did")
    void aplainLineIsUnchanged() {
        ParsedAction parsed = ActionParser.parse("[message] hello");

        assertThat(parsed.payload()).isEqualTo("hello");
        assertThat(parsed.modifiers().isPlain()).isTrue();

        Recording recording = new Recording();
        parsed.action().run(recording.context());
        assertThat(recording.said).containsExactly("hello");
        assertThat(recording.waits).isEmpty();
    }

    @Test
    @DisplayName("a delay is written in ticks and the payload starts after the block")
    void adelayIsInTicks() {
        ParsedAction parsed = ActionParser.parse("[message] {delay=40} later");

        assertThat(parsed.payload()).isEqualTo("later");
        assertThat(parsed.modifiers().delay()).isEqualTo(Duration.ofSeconds(2));

        Recording recording = new Recording();
        parsed.action().run(recording.context());
        assertThat(recording.waits).containsExactly(Duration.ofSeconds(2));
        assertThat(recording.said).containsExactly("later");
    }

    @Test
    @DisplayName("a repeat with no gap runs its copies in the same tick")
    void arepeatWithNoGapRunsAtOnce() {
        Recording recording = new Recording();

        ActionParser.parse("[message] {repeat=3} tick").action().run(recording.context());

        assertThat(recording.said).containsExactly("tick", "tick", "tick");
        assertThat(recording.waits).isEmpty();
    }

    @Test
    @DisplayName("a repeat with a gap waits between its copies")
    void arepeatWithAGapWaits() {
        Recording recording = new Recording();

        ActionParser.parse("[message] {repeat=3, every=5} tick").action().run(recording.context());

        assertThat(recording.said).containsExactly("tick", "tick", "tick");
        assertThat(recording.waits).containsExactly(Duration.ofMillis(250), Duration.ofMillis(250));
    }

    /** A condition that does not hold means the action does not run, and nothing else changes. */
    @Test
    @DisplayName("a condition that fails stops the action, and one that holds lets it through")
    void aconditionGatesTheAction() {
        Recording refused = new Recording();
        ActionParser.parse("[message] {if=1 >= 10} no").action().run(refused.context());
        assertThat(refused.said).isEmpty();

        Recording allowed = new Recording();
        ActionParser.parse("[message] {if=10 >= 1} yes").action().run(allowed.context());
        assertThat(allowed.said).containsExactly("yes");
    }

    @Test
    @DisplayName("a chance of zero never runs and a chance of one always does")
    void achanceOfZeroNeverRuns() {
        Recording never = new Recording();
        for (int at = 0; at < 50; at++) {
            ActionParser.parse("[message] {chance=0} no").action().run(never.context());
        }
        assertThat(never.said).isEmpty();

        Recording always = new Recording();
        ActionParser.parse("[message] {chance=1} yes").action().run(always.context());
        assertThat(always.said).containsExactly("yes");
    }

    @Test
    @DisplayName("the block may hold several modifiers at once")
    void thewholeBlockIsRead() {
        ActionModifiers modifiers = ActionParser.parse(
                        "[message] {delay=20, repeat=2, every=10, chance=0.5, if=5 >= 1} hi")
                .modifiers();

        assertThat(modifiers.delay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(modifiers.repeat()).isEqualTo(2);
        assertThat(modifiers.every()).isEqualTo(Duration.ofMillis(500));
        assertThat(modifiers.chance()).isEqualTo(0.5);
        assertThat(modifiers.condition()).contains("5 >= 1");
    }

    @Test
    @DisplayName("a block that is opened and not closed says so")
    void anunclosedBlockIsLoud() {
        assertThatThrownBy(() -> ActionParser.parse("[message] {delay=40 hello"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("}");
    }

    @Test
    @DisplayName("a modifier nobody has heard of names itself")
    void anunknownModifierIsLoud() {
        assertThatThrownBy(() -> ActionParser.parse("[message] {colour=red} hello"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("colour");
    }

    @Test
    @DisplayName("a delay that is not a number names itself")
    void anonsenseDelayIsLoud() {
        assertThatThrownBy(() -> ActionParser.parse("[message] {delay=soon} hello"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("soon");
    }

    /**
     * A cost is checked before any action in the list runs, so a cost that might not run cannot be part of
     * that check. A pre-flight that lies is worse than none: it takes the money and refuses the reward.
     */
    @Test
    @DisplayName("a cost action may not carry modifiers, and says why")
    void acostTakesNoModifiers() {
        assertThatThrownBy(() -> ActionParser.parse("[take-money] {chance=0.5} vault 100"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cost");
    }

    @Test
    @DisplayName("a repeat below one and a chance outside zero to one are refused")
    void nonsenseValuesAreRefused() {
        assertThatThrownBy(() -> ActionParser.parse("[message] {repeat=0} hi"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ActionParser.parse("[message] {chance=2} hi"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ActionParser.parse("[message] {delay=-1} hi"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
