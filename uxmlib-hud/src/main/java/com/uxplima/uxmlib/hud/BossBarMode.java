package com.uxplima.uxmlib.hud;

/**
 * How a tracked boss bar's progress evolves over time.
 *
 * <ul>
 *   <li>{@link #PERMANENT}: progress never changes on its own; the caller sets it explicitly.
 *   <li>{@link #FILLING}: progress ramps {@code 0 -> 1} linearly over the bar's duration, then holds full.
 *   <li>{@link #COUNTDOWN}: progress ramps {@code 1 -> 0} over the duration and the bar auto-hides at the
 *       end. This is the mode behind {@code BossBarManager.countdown}.
 *   <li>{@link #DYNAMIC}: progress (and optionally the name) is re-evaluated from a caller-supplied function
 *       on every tick; the duration is ignored.
 * </ul>
 *
 * <p>The {@link #FILLING} and {@link #COUNTDOWN} ramps are pure arithmetic over elapsed/total millis, kept on
 * the enum so they can be unit-tested without any Bukkit plumbing.
 */
public enum BossBarMode {
    PERMANENT(false),
    FILLING(true),
    COUNTDOWN(true),
    DYNAMIC(false);

    private final boolean timed;

    BossBarMode(boolean timed) {
        this.timed = timed;
    }

    /** Whether this mode derives its progress from elapsed time (so {@link #progressAt} is meaningful). */
    public boolean timed() {
        return timed;
    }

    /**
     * The progress this timed mode should show after {@code elapsedMillis} of a {@code totalMillis} window,
     * clamped to {@code [0, 1]} so a tick before the start or after the end never overshoots.
     *
     * @throws IllegalStateException if the mode is not {@link #timed()}
     * @throws IllegalArgumentException if {@code totalMillis} is not positive
     */
    public float progressAt(long elapsedMillis, long totalMillis) {
        if (!timed) {
            throw new IllegalStateException(name() + " has no time-driven progress");
        }
        if (totalMillis <= 0L) {
            throw new IllegalArgumentException("totalMillis must be positive");
        }
        float fraction = clamp01((float) elapsedMillis / (float) totalMillis);
        return this == COUNTDOWN ? 1.0f - fraction : fraction;
    }

    /**
     * Whether the bar should auto-hide now. Only {@link #COUNTDOWN} finishes: once its window elapses the
     * bar removes itself; every other mode keeps showing.
     */
    public boolean finishedAt(long elapsedMillis, long totalMillis) {
        return this == COUNTDOWN && elapsedMillis >= totalMillis;
    }

    private static float clamp01(float value) {
        if (value < 0.0f) {
            return 0.0f;
        }
        return value > 1.0f ? 1.0f : value;
    }
}
