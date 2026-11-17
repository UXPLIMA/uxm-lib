package com.uxplima.uxmlib.board;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoardAnswersTest {

    /** Accounts Mojang made: version 4 ids. */
    private static final UUID ALEX = UUID.fromString("00000000-0000-4000-8000-00000000000a");

    private static final UUID SAM = UUID.fromString("00000000-0000-4000-8000-00000000000b");
    /** An offline-mode account: its id derived from its name, version 3. */
    private static final UUID OFFLINE = UUID.nameUUIDFromBytes("OfflinePlayer:Notch".getBytes(StandardCharsets.UTF_8));
    /** A Bedrock account through Floodgate: the Xbox user id under a zero high half, version 0. */
    private static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6");
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
    void aWordWithAnUnderscoreIsReadFromTheEnd() {
        Board job = Board.counter("my_job");
        Leaderboard jobs = new Leaderboard(
                store,
                List.of(job),
                id -> "Someone",
                () -> new BoardSettings(
                        10,
                        Duration.ofSeconds(15),
                        EnumSet.of(BoardPeriod.ALLTIME, BoardPeriod.WEEKLY),
                        new BoardCalendar(ZoneOffset.UTC, DayOfWeek.MONDAY),
                        12,
                        "steve"),
                clock);
        Standings held = new Standings(store, jobs);
        held.load(ALEX);
        new BoardRecorder(store, jobs, held, Runnable::run, clock).add(ALEX, job, 6);
        jobs.refresh();
        BoardAnswers answers = new BoardAnswers(jobs, held, new Words(), () -> "steve");

        assertThat(answers.answer(null, null, "top_my_job_weekly_1_name")).isEqualTo("Alex");
        assertThat(answers.answer(null, null, "top_my_job_weekly_1_value")).isEqualTo("6");
        assertThat(answers.answer(ALEX, "Alex", "rank_my_job_weekly")).isEqualTo("1");
        assertThat(answers.answer(ALEX, "Alex", "value_my_job_alltime")).isEqualTo("6");
        assertThat(answers.answer(null, null, "top_job_weekly_1_name")).isNull();
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
        assertThat(BoardAnswers.headOf(SAM, "Sam_1", "steve")).isEqualTo("Sam_1");
        assertThat(BoardAnswers.headOf(SAM, "Sam.With.Dots", "steve")).isEqualTo(SAM.toString());
        assertThat(BoardAnswers.headOf(SAM, null, "steve")).isEqualTo(SAM.toString());
        assertThat(answers.answer(SAM, "Sam", "head")).isEqualTo("Sam");
    }

    @Test
    void anOfflineModeOrBedrockAccountIsDrawnAsTheEmptyHeadNeverByItsName() {
        // "Notch" at Mojang is somebody else; a Floodgate id is nobody there.
        assertThat(BoardAnswers.headOf(OFFLINE, "Notch", "steve")).isEqualTo("steve");
        assertThat(BoardAnswers.headOf(BEDROCK, ".BedrockSam", "steve")).isEqualTo("steve");
        assertThat(BoardAnswers.headOf(BEDROCK, "BedrockSam", "steve")).isEqualTo("steve");
        assertThat(answers.answer(OFFLINE, "Notch", "head")).isEqualTo("steve");
        assertThat(answers.answer(BEDROCK, ".BedrockSam", "head")).isEqualTo("steve");
    }

    @Test
    void aHeadThePluginKeepsIsDrawnFirstForEveryAccount() {
        BoardAnswers withSkins = new BoardAnswers(
                leaderboard,
                standings,
                new Words(),
                () -> "steve",
                player -> player.equals(BEDROCK) ? Optional.of("skins/bedrock-sam") : Optional.empty());

        assertThat(withSkins.answer(BEDROCK, ".BedrockSam", "head")).isEqualTo("skins/bedrock-sam");
        assertThat(withSkins.answer(OFFLINE, "Notch", "head")).isEqualTo("steve");
        assertThat(withSkins.answer(SAM, "Sam", "head")).isEqualTo("Sam");
    }

    /** A skin as Mojang hands it out, signed, and one a plugin made itself, unsigned. */
    private static final StoredSkin NOTCH_SKIN = new StoredSkin("ewogICJ0aW1lc3RhbXAiIDogMQp9", "c2lnbmVk");

    private static final StoredSkin BEDROCK_SKIN = StoredSkin.unsigned("ewogICJ0aW1lc3RhbXAiIDogMgp9");

    private final BoardAnswers withStoredSkins = new BoardAnswers(
            leaderboard,
            standings,
            new Words(),
            () -> BoardSettings.STEVE,
            player -> Optional.empty(),
            player -> player.equals(OFFLINE)
                    ? Optional.of(NOTCH_SKIN)
                    : player.equals(BEDROCK) ? Optional.of(BEDROCK_SKIN) : Optional.empty());

    @Test
    void aStoredSkinIsDrawnFromItsTexturesAloneForAnOfflineOrBedrockReader() {
        PlayerHeadObjectContents notch = head(withStoredSkins.headComponent(OFFLINE, "Notch", "head"));
        PlayerHeadObjectContents bedrock = head(withStoredSkins.headComponent(BEDROCK, ".BedrockSam", "head"));

        assertThat(notch.profileProperties()).singleElement().satisfies(property -> {
            assertThat(property.name()).isEqualTo("textures");
            assertThat(property.value()).isEqualTo(NOTCH_SKIN.value());
            assertThat(property.signature()).isEqualTo("c2lnbmVk");
        });
        // No name or id: the client draws the skin it was given and asks Mojang about nobody.
        assertThat(notch.name()).isNull();
        assertThat(notch.id()).isNull();
        assertThat(bedrock.profileProperties()).singleElement().satisfies(property -> {
            assertThat(property.value()).isEqualTo(BEDROCK_SKIN.value());
            assertThat(property.signature()).isNull();
        });
    }

    @Test
    void theTagTextStaysWhatATagCanTakeWhenASkinIsStored() {
        assertThat(withStoredSkins.answer(OFFLINE, "Notch", "head")).isEqualTo(BoardSettings.STEVE);
        assertThat(withStoredSkins.answer(BEDROCK, ".BedrockSam", "head")).isEqualTo(BoardSettings.STEVE);
    }

    @Test
    void aPlaceHolderIsDrawnFromTheirSkinAndAnEmptyPlaceFromTheEmptyHead() {
        store.name(OFFLINE, "Notch");
        BoardRecorder recorder = new BoardRecorder(store, leaderboard, standings, Runnable::run, clock);
        standings.load(OFFLINE);
        recorder.add(OFFLINE, kills, 9);
        leaderboard.refresh();

        PlayerHeadObjectContents first = head(withStoredSkins.headComponent(null, null, "top_kills_weekly_1_head"));
        PlayerHeadObjectContents second = head(withStoredSkins.headComponent(null, null, "top_kills_weekly_2_head"));
        PlayerHeadObjectContents empty = head(withStoredSkins.headComponent(null, null, "top_kills_weekly_3_head"));

        assertThat(first.profileProperties())
                .singleElement()
                .satisfies(property -> assertThat(property.value()).isEqualTo(NOTCH_SKIN.value()));
        assertThat(second.name()).as("a Mojang account without a stored skin").isEqualTo("Alex");
        assertThat(empty.texture()).isEqualTo(Key.key(BoardSettings.STEVE));
        assertThat(empty.profileProperties()).isEmpty();
    }

    @Test
    void aHeadWithoutAStoredSkinIsReadAsTheTagReadsItsText() {
        assertThat(head(withStoredSkins.headComponent(SAM, "Sam", "head")).name())
                .isEqualTo("Sam");
        assertThat(head(withStoredSkins.headComponent(SAM, "Sam.With.Dots", "head"))
                        .id())
                .isEqualTo(SAM);
        assertThat(head(answers.headComponent(OFFLINE, "Notch", "head")).name())
                .as("the configured empty head, a name here")
                .isEqualTo("steve");
    }

    @Test
    void onlyAHeadRequestAnswersAComponent() {
        assertThat(withStoredSkins.headComponent(null, null, "head"))
                .as("no reader")
                .isNull();
        assertThat(withStoredSkins.headComponent(null, null, "top_kills_weekly_1_name"))
                .isNull();
        assertThat(withStoredSkins.headComponent(null, null, "top_nothing_weekly_1_head"))
                .isNull();
        assertThat(withStoredSkins.headComponent(ALEX, "Alex", "rank_kills_daily"))
                .isNull();
    }

    private static PlayerHeadObjectContents head(@Nullable Component component) {
        if (!(component instanceof ObjectComponent object)) {
            throw new AssertionError("expected a head, got " + component);
        }
        return (PlayerHeadObjectContents) object.contents();
    }
}
