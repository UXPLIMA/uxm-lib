package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.audience.Audience;

import com.uxplima.uxmlib.condition.ConditionRequest;
import com.uxplima.uxmlib.condition.OperandResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A context turned into a condition request keeps the words, the style, the delay and the broadcast it was given.
 *
 * <p>From uxm-plots: {@link ActionContext#asConditionRequest()} passed the wallet, the items and the sinks and dropped
 * these four, so a failure list run from the request would have drawn a key as its path, painted in the default
 * theme, and thrown on a boss bar or a broadcast the context had answered.
 */
class AContextTurnedIntoARequestKeepsItsSeamsTest {

    @Test
    @DisplayName("the request answers with the context's words, style, delay and broadcast")
    void theRequestKeepsTheFourSeams() {
        List<Duration> delays = new ArrayList<>();
        Audience everybody = Audience.empty();
        ActionContext context = ActionContext.builder(OperandResolver.identity())
                .words(Map.of("gate.closed", "The gate is closed")::get)
                .style(line -> "styled " + line)
                .later((delay, what) -> delays.add(delay))
                .broadcast(everybody)
                .build();

        ConditionRequest request = context.asConditionRequest();

        assertThat(request.words())
                .hasValueSatisfying(
                        words -> assertThat(words.apply("gate.closed")).isEqualTo("The gate is closed"));
        assertThat(request.style())
                .hasValueSatisfying(style -> assertThat(style.apply("line")).isEqualTo("styled line"));
        assertThat(request.broadcast()).containsSame(everybody);
        request.later().orElseThrow().accept(Duration.ofSeconds(3), () -> {});
        assertThat(delays).containsExactly(Duration.ofSeconds(3));
    }
}
