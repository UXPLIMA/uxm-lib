package com.uxplima.uxmlib.board;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * One thing a plugin ranks players by.
 *
 * <ul>
 *   <li>A <b>counter</b> adds every event: kills, crates opened, coins earned.</li>
 *   <li>A <b>record</b> keeps the best single value: the longest parkour run.</li>
 *   <li>A <b>snapshot</b> reads what the player holds now through a {@link Snapshot}, for all time only.</li>
 * </ul>
 *
 * A counter or a record can take its all-time board from a snapshot instead ({@link #withAllTime}), for a value the
 * plugin can lower, such as experience that a prestige resets: the periods still count what was earned in them.
 *
 * <p>The word is what a placeholder writes between {@code top_} and the period. It takes lower-case letters, digits,
 * {@code -} and {@code _}, and is at most 32 long, which the plugin's {@code board_name} column must hold. A key is read
 * from its end, where the period, the place and the field never hold an underscore, so a word may. Two boards are the
 * same board when their words are.
 */
public final class Board {

    /** How a board adds an event to what a player holds. */
    public enum Kind {
        COUNTER,
        RECORD,
        SNAPSHOT
    }

    private static final Pattern WORD = Pattern.compile("[a-z0-9_-]{1,32}");

    private final String word;
    private final Kind kind;
    private final @Nullable Snapshot allTime;

    private Board(String word, Kind kind, @Nullable Snapshot allTime) {
        Objects.requireNonNull(word, "word");
        if (!WORD.matcher(word).matches()) {
            throw new IllegalArgumentException(
                    "a board word takes 1 to 32 lower-case letters, digits, '-' and '_', got: " + word);
        }
        this.word = word;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.allTime = allTime;
    }

    /** A board that adds every event. */
    public static Board counter(String word) {
        return new Board(word, Kind.COUNTER, null);
    }

    /** A board that keeps the best single value. */
    public static Board record(String word) {
        return new Board(word, Kind.RECORD, null);
    }

    /** A board read from what players hold now, for all time only. */
    public static Board snapshot(String word, Snapshot source) {
        return new Board(word, Kind.SNAPSHOT, Objects.requireNonNull(source, "source"));
    }

    /** This counter or record with its all-time board read from {@code source} rather than counted. */
    public Board withAllTime(Snapshot source) {
        if (kind == Kind.SNAPSHOT) {
            throw new IllegalStateException("a snapshot board reads all time already: " + word);
        }
        return new Board(word, kind, Objects.requireNonNull(source, "source"));
    }

    public String word() {
        return word;
    }

    public Kind kind() {
        return kind;
    }

    /** Whether events in {@code period} are kept by the store, as opposed to read from a snapshot or not run. */
    public boolean counts(BoardPeriod period) {
        Objects.requireNonNull(period, "period");
        return kind != Kind.SNAPSHOT && !(period == BoardPeriod.ALLTIME && allTime != null);
    }

    /** Whether {@code period} runs on this board at all: a snapshot board runs for all time only. */
    public boolean runs(BoardPeriod period) {
        return kind != Kind.SNAPSHOT || period == BoardPeriod.ALLTIME;
    }

    /** The source {@code period} is read from when it is not counted, or empty when it is. */
    public Optional<Snapshot> snapshotFor(BoardPeriod period) {
        return period == BoardPeriod.ALLTIME ? Optional.ofNullable(allTime) : Optional.empty();
    }

    /** The score after {@code amount} is added to {@code held}: a sum for a counter, the best for a record. */
    public long after(long held, long amount) {
        return kind == Kind.RECORD ? Math.max(held, amount) : held + amount;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Board board && board.word.equals(word);
    }

    @Override
    public int hashCode() {
        return word.hashCode();
    }

    @Override
    public String toString() {
        return word;
    }
}
