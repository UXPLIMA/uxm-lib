package com.uxplima.uxmlib.health;

import java.util.Objects;

/**
 * What one check found: a severity and one sentence an operator reads.
 *
 * <p>The sentence is the whole value of the surface, so it says what was found rather than what was asked:
 * "answered in 4 ms" and not "storage ok", "two crate files did not read" and not "content warn".
 *
 * @param status how bad it is
 * @param message one line, in the operator's own console or chat
 */
public record HealthResult(HealthStatus status, String message) {

    public HealthResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");
    }

    /** It works. */
    public static HealthResult ok(String message) {
        return new HealthResult(HealthStatus.OK, message);
    }

    /** It works and something is not as the operator meant it. */
    public static HealthResult warn(String message) {
        return new HealthResult(HealthStatus.WARN, message);
    }

    /** It does not work. */
    public static HealthResult fail(String message) {
        return new HealthResult(HealthStatus.FAIL, message);
    }
}
