package com.uxplima.uxmlib.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What an operator is told when they ask a plugin how it is.
 *
 * <p>Every plugin of ours reads its content at boot, writes what it could not read to the console and then
 * says nothing about it ever again. An operator who was not watching the console, or who is not the person
 * with access to it, has no way to ask. uxmEssentials answers that with {@code /uxmess doctor} and the
 * others have nothing, so the mechanism moves here and each plugin brings its own checks.
 */
final class HealthReportTest {

    @Test
    @DisplayName("the report is the worst answer any check gave")
    void theworstAnswerWins() {
        assertThat(HealthReport.run(List.of(fixed("storage", HealthResult.ok("answered"))))
                        .overall())
                .isEqualTo(HealthStatus.OK);
        assertThat(HealthReport.run(List.of(
                                fixed("storage", HealthResult.ok("answered")),
                                fixed("content", HealthResult.warn("two files did not read"))))
                        .overall())
                .isEqualTo(HealthStatus.WARN);
        assertThat(HealthReport.run(List.of(
                                fixed("content", HealthResult.warn("two files did not read")),
                                fixed("storage", HealthResult.fail("no connection"))))
                        .overall())
                .isEqualTo(HealthStatus.FAIL);
    }

    @Test
    @DisplayName("a report of nothing is not a failure, it is a plugin with nothing to check")
    void nochecksIsNotAFailure() {
        HealthReport empty = HealthReport.run(List.of());

        assertThat(empty.entries()).isEmpty();
        assertThat(empty.overall()).isEqualTo(HealthStatus.OK);
        assertThat(empty.hasFailure()).isFalse();
    }

    @Test
    @DisplayName("a check that throws is a failed check and never a failed report run")
    void athrowIsOneFailedLine() {
        HealthReport report =
                HealthReport.run(List.of(fixed("storage", HealthResult.ok("answered")), new HealthCheck() {

                    @Override
                    public String name() {
                        return "economy";
                    }

                    @Override
                    public HealthResult check() {
                        throw new IllegalStateException("the economy plugin threw");
                    }
                }));

        assertThat(report.entries()).hasSize(2);
        assertThat(report.overall()).isEqualTo(HealthStatus.FAIL);
        assertThat(report.entries().get(1).result().message())
                .describedAs("the operator is told what threw, because that is the whole answer")
                .contains("the economy plugin threw");
    }

    @Test
    @DisplayName("the entries come back in the order they were asked")
    void theorderIsKept() {
        HealthReport report =
                HealthReport.run(List.of(fixed("first", HealthResult.ok("a")), fixed("second", HealthResult.ok("b"))));

        assertThat(report.entries()).extracting(HealthReport.Entry::name).containsExactly("first", "second");
    }

    private static HealthCheck fixed(String name, HealthResult result) {
        return new HealthCheck() {

            @Override
            public String name() {
                return name;
            }

            @Override
            public HealthResult check() {
                return result;
            }
        };
    }
}
