package com.uxplima.uxmlib.board;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * The board placeholders, in the grammar every UXM plugin shares. A plugin hands over what follows its own prefix:
 *
 * <ul>
 *   <li>{@code top_<board>_<period>_<n>_name}, {@code _value} and {@code _head}: the player at a place.</li>
 *   <li>{@code rank_<board>_<period>} and {@code value_<board>_<period>}: the reader's own place and score.</li>
 *   <li>{@code resets_<period>}: the time until the period starts again.</li>
 *   <li>{@code head}: what the reader's own head is drawn from.</li>
 * </ul>
 *
 * <p>A board stands whether ten people are on it or one, so a place nobody holds still answers: its name is the
 * plugin's word for nobody, its value the word for none, and its head the configured empty head, so the
 * {@code <head:...>} tag around it always has something to draw. Every answer comes out of memory.
 */
public final class BoardAnswers {

    private static final String TOP = "top_";
    private static final String RANK = "rank_";
    private static final String VALUE = "value_";
    private static final String RESETS = "resets_";
    private static final String HEAD = "head";

    /** A name an account can have. Anything else, a Bedrock name with its prefix for one, is drawn by its id. */
    private static final Pattern ACCOUNT_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private record Target(Board board, BoardPeriod period) {}

    private final Leaderboard leaderboard;
    private final Standings standings;
    private final BoardText text;
    private final Supplier<String> emptyHead;

    /** @param emptyHead read on every answer, so a reload changes it */
    public BoardAnswers(Leaderboard leaderboard, Standings standings, BoardText text, Supplier<String> emptyHead) {
        this.leaderboard = Objects.requireNonNull(leaderboard, "leaderboard");
        this.standings = Objects.requireNonNull(standings, "standings");
        this.text = Objects.requireNonNull(text, "text");
        this.emptyHead = Objects.requireNonNull(emptyHead, "emptyHead");
    }

    /** Whether {@code request}, in lower case and without the plugin's prefix, is one this class answers. */
    public boolean handles(String request) {
        Objects.requireNonNull(request, "request");
        return request.startsWith(TOP)
                || request.startsWith(RANK)
                || request.startsWith(VALUE)
                || request.startsWith(RESETS)
                || request.equals(HEAD);
    }

    /**
     * The answer to {@code request}, or null for a board, period or field the plugin does not have, which
     * PlaceholderAPI leaves as written. The reader's own place, score and head need a reader.
     */
    public @Nullable String answer(@Nullable UUID reader, @Nullable String readerName, String request) {
        Objects.requireNonNull(request, "request");
        if (request.startsWith(TOP)) {
            return top(reader, request.substring(TOP.length()));
        }
        if (request.startsWith(RANK)) {
            return rank(reader, request.substring(RANK.length()));
        }
        if (request.startsWith(VALUE)) {
            return value(reader, request.substring(VALUE.length()));
        }
        if (request.startsWith(RESETS)) {
            return resets(reader, request.substring(RESETS.length()));
        }
        if (request.equals(HEAD)) {
            return reader == null ? null : headOf(reader, readerName);
        }
        return null;
    }

    /**
     * What a {@code <head:...>} tag draws a player from: their name when an account can have it, their id otherwise.
     *
     * <p>The name first, because a server in offline mode, or one behind a proxy that does not forward identities,
     * gives every player an id no account has, and the client draws a stock skin for it. A name finds the account's
     * own skin. A name an account cannot have would fail the tag, so it falls back to the id, which the tag takes.
     */
    public static String headOf(UUID player, @Nullable String name) {
        Objects.requireNonNull(player, "player");
        return name != null && ACCOUNT_NAME.matcher(name).matches() ? name : player.toString();
    }

    /** {@code <board>_<period>_<n>_<field>}. */
    private @Nullable String top(@Nullable UUID reader, String params) {
        String[] parts = params.split("_", -1);
        if (parts.length != 4) {
            return null;
        }
        Optional<Target> target = target(parts[0], parts[1]);
        OptionalInt place = number(parts[2]);
        if (target.isEmpty() || place.isEmpty()) {
            return null;
        }
        Board board = target.get().board();
        Optional<Leaderboard.Row> row = leaderboard.at(board, target.get().period(), place.getAsInt());
        return switch (parts[3]) {
            case "name" -> row.map(Leaderboard.Row::name).orElseGet(() -> text.nobody(reader));
            case "value" ->
                row.map(held -> text.score(board, held.score(), reader)).orElseGet(() -> text.none(reader));
            case "head" -> row.map(held -> headOf(held.player(), held.name())).orElseGet(emptyHead);
            default -> null;
        };
    }

    private @Nullable String rank(@Nullable UUID reader, String params) {
        Optional<Target> target = target(params);
        if (reader == null || target.isEmpty()) {
            return null;
        }
        long score =
                standings.scoreOf(reader, target.get().board(), target.get().period());
        OptionalInt place =
                leaderboard.placeOf(target.get().board(), target.get().period(), score);
        return place.isPresent() ? String.valueOf(place.getAsInt()) : text.none(reader);
    }

    private @Nullable String value(@Nullable UUID reader, String params) {
        Optional<Target> target = target(params);
        if (reader == null || target.isEmpty()) {
            return null;
        }
        Board board = target.get().board();
        return text.score(board, standings.scoreOf(reader, board, target.get().period()), reader);
    }

    private @Nullable String resets(@Nullable UUID reader, String params) {
        Optional<BoardPeriod> period = BoardPeriod.byWord(params);
        if (period.isEmpty()) {
            return null;
        }
        Optional<Duration> left = leaderboard.untilReset(period.get());
        if (left.isEmpty()) {
            return text.none(reader);
        }
        Duration until = left.get();
        if (until.toDays() > 0) {
            return text.resets(reader, new BoardText.Left.Days(until.toDays(), until.toHoursPart()));
        }
        if (until.toHours() > 0) {
            return text.resets(reader, new BoardText.Left.Hours(until.toHours(), until.toMinutesPart()));
        }
        return text.resets(reader, new BoardText.Left.Minutes(Math.max(1, until.toMinutes())));
    }

    /** {@code <board>_<period>}. */
    private Optional<Target> target(String params) {
        String[] parts = params.split("_", -1);
        return parts.length == 2 ? target(parts[0], parts[1]) : Optional.empty();
    }

    /** A board the plugin has, in a period it runs on that board. */
    private Optional<Target> target(String word, String periodWord) {
        Optional<Board> board = leaderboard.board(word);
        Optional<BoardPeriod> period = BoardPeriod.byWord(periodWord);
        if (board.isEmpty() || period.isEmpty() || !leaderboard.runs(board.get(), period.get())) {
            return Optional.empty();
        }
        return Optional.of(new Target(board.get(), period.get()));
    }

    private static OptionalInt number(String written) {
        try {
            return OptionalInt.of(Integer.parseInt(written));
        } catch (NumberFormatException notANumber) {
            return OptionalInt.empty();
        }
    }
}
