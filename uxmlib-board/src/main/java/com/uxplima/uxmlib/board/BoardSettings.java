package com.uxplima.uxmlib.board;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmlib.config.HoconConfig;

/**
 * How a plugin's boards run: the {@code leaderboard} block every UXM plugin with boards writes the same way.
 *
 * <pre>{@code
 * leaderboard {
 *     size = 10                 # places each board keeps
 *     refresh-seconds = 15      # how often the boards are read again
 *     periods = [alltime, monthly, weekly, daily]
 *     time-zone = ""            # empty is the server's own
 *     week-starts = monday
 *     keep-windows = 12         # ended windows kept for a look back
 *     empty-head = "entity/player/wide/steve"
 * }
 * }</pre>
 *
 * @param emptyHead what the head of a place nobody holds is drawn from: a texture path, a name or an id
 */
public record BoardSettings(
        int size,
        Duration refresh,
        Set<BoardPeriod> periods,
        BoardCalendar calendar,
        int keepWindows,
        String emptyHead) {

    /** The head the game draws for a player it has no skin for. */
    public static final String STEVE = "entity/player/wide/steve";

    public BoardSettings {
        Objects.requireNonNull(refresh, "refresh");
        Objects.requireNonNull(calendar, "calendar");
        Objects.requireNonNull(emptyHead, "emptyHead");
        periods = Set.copyOf(Objects.requireNonNull(periods, "periods"));
        if (size < 1) {
            throw new IllegalArgumentException("leaderboard.size must be at least 1, got: " + size);
        }
        if (refresh.isZero() || refresh.isNegative()) {
            throw new IllegalArgumentException("leaderboard.refresh-seconds must be positive, got: " + refresh);
        }
        if (keepWindows < 1) {
            throw new IllegalArgumentException("leaderboard.keep-windows must be at least 1, got: " + keepWindows);
        }
        if (emptyHead.isBlank()) {
            throw new IllegalArgumentException("leaderboard.empty-head must name a head, got nothing");
        }
    }

    /** Ten places, a read every fifteen seconds, every period, the server's zone, weeks from Monday. */
    public static BoardSettings defaults() {
        return new BoardSettings(
                10,
                Duration.ofSeconds(15),
                EnumSet.allOf(BoardPeriod.class),
                new BoardCalendar(ZoneId.systemDefault(), DayOfWeek.MONDAY),
                12,
                STEVE);
    }

    /**
     * Read the block at {@code path}, {@code "leaderboard"} in every UXM plugin. A key left out takes the
     * {@link #defaults()} value; a key written wrong throws, naming the key and what it takes.
     */
    public static BoardSettings read(HoconConfig config, String path) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(path, "path");
        String at = path + ".";
        return new BoardSettings(
                config.getInt(at + "size", 10),
                Duration.ofSeconds(config.getLong(at + "refresh-seconds", 15L)),
                periods(config.getList(at + "periods", String.class), at),
                new BoardCalendar(
                        zone(config.getString(at + "time-zone", ""), at),
                        weekStart(config.getString(at + "week-starts", "monday"), at)),
                config.getInt(at + "keep-windows", 12),
                config.getString(at + "empty-head", STEVE));
    }

    /** The periods named in the file. A list that names none keeps every period. */
    private static Set<BoardPeriod> periods(List<String> written, String at) {
        if (written.isEmpty()) {
            return EnumSet.allOf(BoardPeriod.class);
        }
        Set<BoardPeriod> periods = EnumSet.noneOf(BoardPeriod.class);
        for (String word : written) {
            periods.add(BoardPeriod.byWord(word.toLowerCase(Locale.ROOT).strip())
                    .orElseThrow(() -> new IllegalArgumentException(at + "periods names no period: " + word
                            + ". It takes alltime, monthly, weekly and daily")));
        }
        return periods;
    }

    private static ZoneId zone(String written, String at) {
        if (written.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(written.strip());
        } catch (DateTimeException unknown) {
            throw new IllegalArgumentException(
                    at + "time-zone is not a time zone: " + written + ". Write one such as Europe/Istanbul", unknown);
        }
    }

    private static DayOfWeek weekStart(String written, String at) {
        try {
            return DayOfWeek.valueOf(written.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(
                    at + "week-starts is not a day: " + written + ". Write one such as monday", unknown);
        }
    }
}
