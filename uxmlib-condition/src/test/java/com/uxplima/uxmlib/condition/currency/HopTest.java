package com.uxplima.uxmlib.condition.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * A hop to another thread that moves money never reports "nothing happened" while the money moves anyway.
 * Each test waits out the hop's limit once, so each takes a few seconds.
 */
class HopTest {

    @Test
    void workThatStartsAfterTheWaitRanOutNeverRuns() {
        AtomicReference<Runnable> held = new AtomicReference<>();
        AtomicInteger ran = new AtomicInteger();

        Optional<String> answer = Hop.await(held::set, () -> {
            ran.incrementAndGet();
            return "paid";
        });
        java.util.Objects.requireNonNull(held.get(), "the hop hands its work to the lane")
                .run();

        assertThat(answer).isEmpty();
        assertThat(ran).hasValue(0);
    }

    @Test
    void workThatStartedBeforeTheWaitRanOutIsWaitedForAndItsAnswerKept() {
        Optional<String> answer = Hop.await(task -> Thread.ofVirtual().start(task), () -> {
            try {
                Thread.sleep(Hop.LIMIT.plusSeconds(1).toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return "paid";
        });

        assertThat(answer).contains("paid");
    }

    @Test
    void workThatFailsAnswersNothing() {
        Optional<String> answer = Hop.await(Runnable::run, () -> {
            throw new IllegalStateException("the economy is down");
        });

        assertThat(answer).isEmpty();
    }
}
