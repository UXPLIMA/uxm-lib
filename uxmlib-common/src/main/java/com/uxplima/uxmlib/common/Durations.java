package com.uxplima.uxmlib.common;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats human-friendly durations such as {@code "1h30m"} or {@code "2d 4h"}. The units are
 * {@code d} (days), {@code h} (hours), {@code m} (minutes), {@code s} (seconds) and {@code ms}
 * (milliseconds); tokens may repeat, appear in any order, and be separated by optional whitespace. This
 * is the shared time vocabulary behind cooldowns, bans, and config durations, so callers never hand-roll
 * the arithmetic.
 */
public final class Durations {

    // A single "<number><unit>" token; "ms" is listed before the single letters so "500ms" matches as one.
    private static final Pattern TOKEN = Pattern.compile("(\\d+)\\s*(ms|[dhms])");

    private Durations() {}

    /**
     * Parse {@code text} into a {@link Duration}.
     *
     * @throws IllegalArgumentException if {@code text} is blank or holds anything but valid tokens
     */
    public static Duration parse(String text) {
        Objects.requireNonNull(text, "text");
        return tryParse(text).orElseThrow(() -> new IllegalArgumentException("not a duration: '" + text + "'"));
    }

    /** Parse {@code text}, or empty when it is blank or malformed. Never throws on bad input. */
    public static Optional<Duration> tryParse(String text) {
        Objects.requireNonNull(text, "text");
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        Matcher matcher = TOKEN.matcher(trimmed);
        long millis = 0L;
        int index = 0;
        try {
            while (index < trimmed.length()) {
                while (index < trimmed.length() && Character.isWhitespace(trimmed.charAt(index))) {
                    index++;
                }
                if (index >= trimmed.length()) {
                    break;
                }
                matcher.region(index, trimmed.length());
                if (!matcher.lookingAt()) {
                    return Optional.empty();
                }
                millis = Math.addExact(millis, unitMillis(Long.parseLong(matcher.group(1)), matcher.group(2)));
                index = matcher.end();
            }
        } catch (ArithmeticException | NumberFormatException overflow) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofMillis(millis));
    }

    /**
     * Render {@code duration} as its single largest whole unit, truncated: {@code 90m -> "1h"} (never
     * {@code "2h"}), {@code 5s -> "5s"}, {@code 7d -> "7d"}. Zero, sub-second and negative durations are
     * {@code "0s"}. This is the coarse counterpart of {@link #format(Duration)} (which lists every non-zero
     * unit), for relative displays like {@code "5m ago"} where one unit reads better than {@code "1h 30m"}.
     */
    public static String approximate(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        long seconds = Math.max(0L, duration.toSeconds());
        if (seconds < 60L) {
            return seconds + "s";
        }
        if (seconds < 3_600L) {
            return seconds / 60L + "m";
        }
        if (seconds < 86_400L) {
            return seconds / 3_600L + "h";
        }
        return seconds / 86_400L + "d";
    }

    /**
     * Render {@code duration} as {@code "1d 2h 3m 4s"}, omitting zero units ({@code "0s"} for zero).
     *
     * <p>This form is for logs, config echoes and English-only output. <strong>Do not put it in a message a
     * player reads</strong> if your plugin is translated: the unit letters are English and no other language
     * reliably orders or spells the parts the same way. Hand the parts to your message layer instead and let
     * the translation do the wording: {@link Duration#toDaysPart()}, {@link Duration#toHoursPart()},
     * {@link Duration#toMinutesPart()} and {@link Duration#toSecondsPart()} give exactly those numbers, so a
     * catalogue entry can read {@code "<days> gün <hours> saat"} without the library choosing any of it.
     */
    public static String format(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        long seconds = duration.toSeconds();
        if (seconds <= 0L) {
            return duration.isZero() || seconds < 0L ? "0s" : duration.toMillis() + "ms";
        }
        StringBuilder out = new StringBuilder();
        append(out, seconds / 86_400L, "d");
        append(out, seconds % 86_400L / 3_600L, "h");
        append(out, seconds % 3_600L / 60L, "m");
        append(out, seconds % 60L, "s");
        return out.toString().strip();
    }

    private static void append(StringBuilder out, long value, String unit) {
        if (value > 0L) {
            out.append(value).append(unit).append(' ');
        }
    }

    private static long unitMillis(long value, String unit) {
        return switch (unit) {
            case "ms" -> value;
            case "s" -> Math.multiplyExact(value, 1_000L);
            case "m" -> Math.multiplyExact(value, 60_000L);
            case "h" -> Math.multiplyExact(value, 3_600_000L);
            case "d" -> Math.multiplyExact(value, 86_400_000L);
            default -> throw new IllegalStateException("unhandled unit: " + unit);
        };
    }
}
