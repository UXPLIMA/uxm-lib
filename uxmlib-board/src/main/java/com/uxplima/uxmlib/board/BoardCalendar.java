package com.uxplima.uxmlib.board;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;
import java.util.Optional;

/**
 * Where a window starts and ends, at midnight in the server's time zone.
 *
 * <p>A window is named by the moment it starts, in epoch milliseconds, so two servers sharing one database agree on
 * it as long as they share the time zone and the first day of the week. All time is the window that starts at zero.
 *
 * @param zone the time zone a day starts in
 * @param weekStart the day a week starts on
 */
public record BoardCalendar(ZoneId zone, DayOfWeek weekStart) {

    public BoardCalendar {
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(weekStart, "weekStart");
    }

    /** When the window of {@code period} that holds {@code now} started. All time started at zero. */
    public long windowStart(BoardPeriod period, Instant now) {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(now, "now");
        if (period == BoardPeriod.ALLTIME) {
            return 0;
        }
        return midnight(firstDay(period, LocalDate.ofInstant(now, zone)));
    }

    /** When the next window of {@code period} starts after {@code now}, or empty for all time. */
    public Optional<Instant> nextStart(BoardPeriod period, Instant now) {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(now, "now");
        if (period == BoardPeriod.ALLTIME) {
            return Optional.empty();
        }
        LocalDate first = firstDay(period, LocalDate.ofInstant(now, zone));
        return Optional.of(Instant.ofEpochMilli(midnight(step(period, first, 1))));
    }

    /** When the window {@code count} windows before the one holding {@code now} started. */
    public long windowBack(BoardPeriod period, Instant now, int count) {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(now, "now");
        if (period == BoardPeriod.ALLTIME) {
            return 0;
        }
        LocalDate first = firstDay(period, LocalDate.ofInstant(now, zone));
        return midnight(step(period, first, -count));
    }

    private LocalDate firstDay(BoardPeriod period, LocalDate day) {
        return switch (period) {
            case DAILY, ALLTIME -> day;
            case WEEKLY -> day.with(TemporalAdjusters.previousOrSame(weekStart));
            case MONTHLY -> day.withDayOfMonth(1);
        };
    }

    private static LocalDate step(BoardPeriod period, LocalDate first, int count) {
        return switch (period) {
            case DAILY, ALLTIME -> first.plusDays(count);
            case WEEKLY -> first.plusWeeks(count);
            case MONTHLY -> first.plusMonths(count);
        };
    }

    private long midnight(LocalDate day) {
        return day.atStartOfDay(zone).toInstant().toEpochMilli();
    }
}
