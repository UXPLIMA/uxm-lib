package com.uxplima.uxmlib.gui.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * A chat line awaited as input is handed on and hidden from both of the events Paper raises it as, so no other
 * plugin reads it; a line nobody awaits, or one after the prompt expired, is chat as usual.
 */
class ChatPromptsTest {

    private static final UUID ALEX = UUID.randomUUID();

    private final ChatPrompts prompts = new ChatPrompts();
    private final List<String> captured = new ArrayList<>();

    @Test
    void aLineCapturedFromTheLegacyEventIsHiddenFromTheModernOneTooAndTheNextLineIsChat() {
        prompts.await(ALEX, captured::add, 1_000L);

        assertThat(prompts.take(ALEX, "1234", 100L, true))
                .as("the legacy raising")
                .isTrue();
        assertThat(prompts.take(ALEX, "", 101L, false))
                .as("the modern raising of the same line")
                .isTrue();
        assertThat(prompts.take(ALEX, "hello", 200L, true)).as("the next line").isFalse();
        assertThat(prompts.take(ALEX, "hello", 201L, false)).isFalse();

        assertThat(captured).containsExactly("1234");
    }

    @Test
    void aLineRaisedOnlyAsTheModernEventIsCapturedAndHidden() {
        prompts.await(ALEX, captured::add, 1_000L);

        assertThat(prompts.take(ALEX, "1234", 100L, false)).isTrue();
        assertThat(prompts.take(ALEX, "hello", 200L, false)).isFalse();

        assertThat(captured).containsExactly("1234");
    }

    @Test
    void aLineAfterThePromptExpiredIsChat() {
        prompts.await(ALEX, captured::add, 1_000L);

        assertThat(prompts.take(ALEX, "late", 1_001L, true)).isFalse();
        assertThat(prompts.take(ALEX, "late", 1_002L, false)).isFalse();

        assertThat(captured).isEmpty();
    }

    @Test
    void aCancelledPromptCapturesNothing() {
        prompts.await(ALEX, captured::add, 1_000L);

        prompts.cancel(ALEX);

        assertThat(prompts.take(ALEX, "1234", 100L, true)).isFalse();
        assertThat(captured).isEmpty();
    }

    @Test
    void aSecondRaisingThatNeverCameDoesNotHideALineSecondsLater() {
        prompts.await(ALEX, captured::add, 100_000L);
        prompts.take(ALEX, "1234", 100L, true);

        assertThat(prompts.take(ALEX, "hello", 100L + ChatEcho.WINDOW_MILLIS, false))
                .isFalse();
    }
}
