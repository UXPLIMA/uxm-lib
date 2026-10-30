package com.uxplima.uxmlib.board;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoardAnswersTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    // A Wednesday at noon: the week starts again in 4 days and 12 hours, the day in 12 hours.
    private static final Instant NOON = Instant.parse("2030-01-02T12:00:00Z");

    private final MovableClock clock = new MovableClock(NOON);
    private final InMemoryBoardStore store = new InMemoryBoardStore();
    private final Board kills = Board.counter("kills");
    private final Board time = Board.record("time");
    private final Leaderboard leaderboard = new Leaderboard(
            store,
            List.of(kills, time),
            id -> "Someone",
            () -> new BoardSettings(
                    10,
                    Duration.ofSeconds(15),
                    EnumSet.of(BoardPeriod.ALLTIME, BoardPeriod.WEEKLY, BoardPeriod.DAILY),
                    new BoardCalendar(ZoneOffset.UTC, DayOfWeek.MONDAY),
                    12,
                    "steve"),
            clock);
    private final Standings standings = new Standings(store, leaderboard);
    private final BoardAnswers answers = new BoardAnswers(leaderboard, standings, new Words(), () -> "steve");

    /** Words a test can read: a time board shows minutes. */
    private static final class Words implements BoardText {

        @Override
        public String nobody(@Nullable UUID reader) {
            return "nobody";
        }

        @Override
        public String none(@Nullable UUID reader) {
            return "none";
        }

        @Override
        public String resets(@Nullable UUID reader, Left left) {
            return switch (left) {
                case Left.Days days -> days.days() + "d " + days.hours() + "h";
                case Left.Hours hours -> hours.hours() + "h " + hours.minutes() + "m";
                case Left.Minutes minutes -> minutes.minutes() + "m";
            };
        }

        @Override
        public String score(Board board, long score, @Nullable UUID reader) {
            return board.word().equals("time") ? score + "s" : Long.toString(score);
        }

        /** All time shows kills as a rank title, the periods as a count. */
        @Override
        public String score(Board board, BoardPeriod period, long score, @Nullable UUID reader) {
            return board.word().equals("kills") && period == BoardPeriod.ALLTIME
                    ? "rank " + score
                    : score(board, score, reader);
        }
    }

    @BeforeEach
    void someoneIsOnTheBoard() {
        store.name(ALEX, "Alex");
        BoardRecorder recorder = new BoardRecorder(store, leaderboard, standings, Runnable::run, clock);
        standings.load(ALEX);
        recorder.add(ALEX, kills, 4);
        recorder.add(ALEX, time, 90);
        leaderboard.refresh();
    }

    @Test
    void aPlaceAnswersItsNameValueAndHead() {
        assertThat(answers.answer(null, null, "top_kills_weekly_1_name")).isEqualTo("Alex");
        assertThat(answers.answer(null, null, "top_kills_weekly_1_value")).isEqualTo("4");
        assertThat(answers.answer(null, null, "top_kills_weekly_1_head")).isEqualTo("Alex");
        assertThat(answers.answer(null, null, "top_time_alltime_1_value")).isEqualTo("90s");
    }

    @Test
    void aPlaceNobodyHoldsStillAnswers() {
        assertThat(answers.answer(null, null, "top_kills_weekly_2_name")).isEqualTo("nobody");
        assertThat(answers.answer(null, null, "top_kills_weekly_2_value")).isEqualTo("none");
        assertThat(answers.answer(null, null, "top_kills_weekly_2_head")).isEqualTo("steve");
    }

    @Test
    void theReaderSeesTheirOwnPlaceAndScore() {
        assertThat(answers.answer(ALEX, "Alex", "rank_kills_daily")).isEqualTo("1");
        assertThat(answers.answer(ALEX, "Alex", "value_kills_daily")).isEqualTo("4");
        assertThat(answers.answer(SAM, "Sam", "rank_kills_daily")).isEqualTo("none");
        assertThat(answers.answer(SAM, "Sam", "value_kills_daily")).isEqualTo("0");
        assertThat(answers.answer(null, null, "rank_kills_daily")).isNull();
    }

    @Test
    void aScoreIsShownByItsPeriod() {
        assertThat(answers.answer(null, null, "top_kills_alltime_1_value")).isEqualTo("rank 4");
        assertThat(answers.answer(null, null, "top_kills_daily_1_value")).isEqualTo("4");
        assertThat(answers.answer(ALEX, "Alex", "value_kills_alltime")).isEqualTo("rank 4");
        assertThat(answers.answer(ALEX, "Alex", "value_kills_weekly")).isEqualTo("4");
    }

    @Test
    void aPeriodStartsAgainInTheReadersWords() {
        assertThat(answers.answer(null, null, "resets_weekly")).isEqualTo("4d 12h");
        assertThat(answers.answer(null, null, "resets_daily")).isEqualTo("12h 0m");
        assertThat(answers.answer(null, null, "resets_alltime")).isEqualTo("none");
    }

    @Test
    void aBoardOrPeriodThePluginDoesNotRunIsLeftAsWritten() {
        assertThat(answers.answer(null, null, "top_deaths_weekly_1_name")).isNull();
        assertThat(answers.answer(null, null, "top_kills_monthly_1_name")).isNull();
        assertThat(answers.answer(null, null, "top_kills_weekly_x_name")).isNull();
        assertThat(answers.answer(null, null, "top_kills_weekly_1_colour")).isNull();
        assertThat(answers.handles("balance")).isFalse();
    }

    @Test
    void aHeadIsDrawnByNameWhenAnAccountCanHaveIt() {
        assertThat(BoardAnswers.headOf(SAM, "Sam_1")).isEqualTo("Sam_1");
        assertThat(BoardAnswers.headOf(SAM, ".BedrockSam")).isEqualTo(SAM.toString());
        assertThat(BoardAnswers.headOf(SAM, null)).isEqualTo(SAM.toString());
        assertThat(answers.answer(SAM, "Sam", "head")).isEqualTo("Sam");
    }
}
