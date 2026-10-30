package com.uxplima.uxmlib.board;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class BoardCalendarTest {

    private final BoardCalendar istanbul = new BoardCalendar(ZoneId.of("Europe/Istanbul"), DayOfWeek.MONDAY);
    // A Wednesday, 01:30 in Istanbul (UTC+3): still Tuesday in UTC.
    private final Instant now = Instant.parse("2030-01-01T22:30:00Z");

    @Test
    void aWindowStartsAtMidnightInTheBoardsZone() {
        assertThat(istanbul.windowStart(BoardPeriod.DAILY, now))
                .isEqualTo(Instant.parse("2030-01-01T21:00:00Z").toEpochMilli());
        assertThat(istanbul.windowStart(BoardPeriod.WEEKLY, now))
                .isEqualTo(Instant.parse("2029-12-30T21:00:00Z").toEpochMilli());
        assertThat(istanbul.windowStart(BoardPeriod.MONTHLY, now))
                .isEqualTo(Instant.parse("2029-12-31T21:00:00Z").toEpochMilli());
        assertThat(istanbul.windowStart(BoardPeriod.ALLTIME, now)).isZero();
    }

    @Test
    void theNextWindowAndOnesBefore() {
        assertThat(istanbul.nextStart(BoardPeriod.WEEKLY, now)).hasValue(Instant.parse("2030-01-06T21:00:00Z"));
        assertThat(istanbul.nextStart(BoardPeriod.ALLTIME, now)).isEmpty();
        assertThat(istanbul.windowBack(BoardPeriod.MONTHLY, now, 2))
                .isEqualTo(Instant.parse("2029-10-31T21:00:00Z").toEpochMilli());
    }
}
