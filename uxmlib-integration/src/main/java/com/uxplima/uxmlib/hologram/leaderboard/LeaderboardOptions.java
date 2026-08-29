package com.uxplima.uxmlib.hologram.leaderboard;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import net.kyori.adventure.text.Component;

import org.jspecify.annotations.Nullable;

/**
 * How a {@link LeaderboardRenderer} should lay out a leaderboard: how many places, sort direction, the
 * MiniMessage line format (with {@code {place}}/{@code {name}}/{@code {score}} tokens), an optional
 * distinct format for the top places, the number locale, an empty-slot line, and an optional header.
 * Build one fluently from {@link #topN(int)}.
 */
public final class LeaderboardOptions {

    private int topN = 10;
    private boolean descending = true;
    private String defaultFormat = "<gray>{place}. <white>{name} <yellow>{score}";
    private List<String> topFormats = List.of();
    private Locale locale = Locale.ROOT;
    private String emptyLine = "<dark_gray>...";
    private @Nullable Component header;

    private LeaderboardOptions() {}

    /** Start options showing {@code n} places. */
    public static LeaderboardOptions topN(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("topN must be >= 1");
        }
        LeaderboardOptions options = new LeaderboardOptions();
        options.topN = n;
        return options;
    }

    /** Sort highest-first (default) or lowest-first. */
    public LeaderboardOptions descending(boolean value) {
        this.descending = value;
        return this;
    }

    /** The MiniMessage format for every place without a specific top format. */
    public LeaderboardOptions format(String format) {
        this.defaultFormat = Objects.requireNonNull(format, "format");
        return this;
    }

    /** Per-place formats for the leading places (index 0 = first place); later places use {@link #format}. */
    public LeaderboardOptions topFormats(List<String> formats) {
        this.topFormats = List.copyOf(formats);
        return this;
    }

    /** The locale used to format the numeric score. */
    public LeaderboardOptions locale(Locale value) {
        this.locale = Objects.requireNonNull(value, "locale");
        return this;
    }

    /** The MiniMessage line shown for a place with no entry. */
    public LeaderboardOptions emptyLine(String line) {
        this.emptyLine = Objects.requireNonNull(line, "line");
        return this;
    }

    /** A header line placed above the entries. */
    public LeaderboardOptions header(Component value) {
        this.header = Objects.requireNonNull(value, "header");
        return this;
    }

    int topN() {
        return topN;
    }

    boolean descending() {
        return descending;
    }

    Locale locale() {
        return locale;
    }

    String emptyLine() {
        return emptyLine;
    }

    Optional<Component> header() {
        return Optional.ofNullable(header);
    }

    /** The format for 1-indexed {@code place}: a specific top format if set, else the default. */
    String formatForPlace(int place) {
        int index = place - 1;
        return index < topFormats.size() ? topFormats.get(index) : defaultFormat;
    }
}
