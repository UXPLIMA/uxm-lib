package com.uxplima.uxmlib.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/** A hop to another thread that moves money never reports "nothing happened" while the money moves anyway. */
class HopTest {

    private static final Duration LIMIT = Duration.ofMillis(200);

    @Test
    void workThatStartsAfterTheWaitRanOutNeverRuns() {
        AtomicReference<Runnable> held = new AtomicReference<>();
        AtomicInteger ran = new AtomicInteger();

        Optional<String> answer = Hop.await(
                held::set,
                () -> {
                    ran.incrementAndGet();
                    return "paid";
                },
                LIMIT);
        Objects.requireNonNull(held.get(), "the hop hands its work to the lane").run();

        assertThat(answer).isEmpty();
        assertThat(ran).hasValue(0);
    }

    @Test
    void workThatStartedBeforeTheWaitRanOutIsWaitedForAndItsAnswerKept() {
        Optional<String> answer = Hop.await(
                task -> Thread.ofVirtual().start(task),
                () -> {
                    sleep(LIMIT.multipliedBy(3));
                    return "paid";
                },
                LIMIT);

        assertThat(answer).contains("paid");
    }

    @Test
    void workThatFailsAnswersNothing() {
        Optional<String> answer = Hop.await(
                Runnable::run,
                () -> {
                    throw new IllegalStateException("the economy is down");
                },
                LIMIT);

        assertThat(answer).isEmpty();
    }

    @Test
    void workThatDiesWithAnErrorAfterTheWaitRanOutStillAnswers() throws InterruptedException {
        AtomicReference<Optional<String>> answer = new AtomicReference<>();
        Thread caller = Thread.ofVirtual()
                .start(() -> answer.set(Hop.await(
                        task -> Thread.ofVirtual()
                                .start(() -> assertThatThrownBy(task::run).isInstanceOf(LinkageError.class)),
                        () -> {
                            sleep(LIMIT.multipliedBy(3));
                            throw new NoClassDefFoundError("an economy class went away");
                        },
                        LIMIT)));

        assertThat(caller.join(Duration.ofSeconds(5)))
                .as("the caller is not left waiting")
                .isTrue();
        assertThat(answer.get()).isEmpty();
    }

    private static void sleep(Duration time) {
        try {
            Thread.sleep(time.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
