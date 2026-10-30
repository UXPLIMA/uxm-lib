package com.uxplima.uxmlib.board;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Every board of a plugin, in every period it runs, read on a timer and answered out of memory.
 *
 * <p>A board is a database query, and a hologram or a sidebar asks for it on every repaint, on whatever thread is
 * drawing. So {@link #refresh()} runs on the async lane at the interval the settings give, and everything that shows a
 * board reads the last answer. A board the timer has not reached yet holds nobody, and so does a board whose window
 * ended since the last read: last week's winners are not shown as this week's for the seconds until the next read.
 *
 * <p>A player's place is worked out against their score as it is now: the timer counts the players at each score,
 * and a place is one more than the players whose score is higher. A score that just moved therefore shows its place
 * at once, and two players on the same score share a place.
 */
public final class Leaderboard {

    /** One place on a board, with the name already looked up so a reader never asks for one. */
    public record Row(UUID player, String name, long score) {

        public Row {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(name, "name");
        }
    }

    private record Key(Board board, BoardPeriod period) {}

    /** One board as the last read left it, for the window it was read in. */
    private record Shown(long window, List<Row> rows, Places places) {}

    /** The old windows are dropped this often at most: a delete per period per refresh would be noise. */
    private static final Duration PRUNE_EVERY = Duration.ofHours(1);

    /** The boards the supplier last gave, by word, kept while it gives the same list. */
    private record Named(List<Board> boards, Map<String, Board> byWord) {}

    private final BoardStore store;
    private final Supplier<List<Board>> boards;
    private final Function<UUID, String> names;
    private final Supplier<BoardSettings> settings;
    private final Clock clock;

    private volatile Named named = new Named(List.of(), Map.of());
    private volatile Map<Key, Shown> shown = Map.of();
    private volatile Instant prunedAt = Instant.EPOCH;

    /**
     * @param names the name of a player whose row has none kept. It runs on the async lane, so it must not go to the
     *     network
     * @param settings read again at every refresh, so a reload changes it
     */
    public Leaderboard(
            BoardStore store,
            List<Board> boards,
            Function<UUID, String> names,
            Supplier<BoardSettings> settings,
            Clock clock) {
        this(store, fixed(boards), names, settings, clock);
    }

    /**
     * Boards that can change while the plugin runs, such as one board per track an operator configures: the supplier
     * is read at every refresh and every answer, so a reload that adds a board shows it from the next refresh and one
     * that takes a board away stops it answering at once. It must be cheap, and give no two boards the same word.
     *
     * @param names the name of a player whose row has none kept. It runs on the async lane, so it must not go to the
     *     network
     * @param settings read again at every refresh, so a reload changes it
     */
    public Leaderboard(
            BoardStore store,
            Supplier<List<Board>> boards,
            Function<UUID, String> names,
            Supplier<BoardSettings> settings,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.boards = Objects.requireNonNull(boards, "boards");
        this.names = Objects.requireNonNull(names, "names");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static Supplier<List<Board>> fixed(List<Board> boards) {
        List<Board> held = List.copyOf(Objects.requireNonNull(boards, "boards"));
        byWord(held);
        return () -> held;
    }

    private static Map<String, Board> byWord(List<Board> boards) {
        Map<String, Board> byWord = new LinkedHashMap<>();
        for (Board board : boards) {
            if (byWord.putIfAbsent(board.word(), board) != null) {
                throw new IllegalArgumentException("two boards share the word " + board.word());
            }
        }
        return byWord;
    }

    /** The boards the supplier gives now, by word, worked out again only when it gives another list. */
    private Map<String, Board> named() {
        List<Board> now = boards.get();
        Named last = named;
        if (!last.boards().equals(now)) {
            last = new Named(List.copyOf(now), Collections.unmodifiableMap(byWord(now)));
            named = last;
        }
        return last.byWord();
    }

    /** Every board, in the order the plugin named them. */
    public List<Board> boards() {
        return List.copyOf(named().values());
    }

    /** The board a word names. */
    public Optional<Board> board(String word) {
        return Optional.ofNullable(named().get(Objects.requireNonNull(word, "word")));
    }

    /** Whether {@code board} runs in {@code period}: the period is on in the settings and the board has it. */
    public boolean runs(Board board, BoardPeriod period) {
        return settings.get().periods().contains(period) && board.runs(period);
    }

    /** The player at a place on a board, counted from one, or empty when nobody holds it. */
    public Optional<Row> at(Board board, BoardPeriod period, int place) {
        Optional<Shown> current = current(board, period);
        if (current.isEmpty() || place < 1 || place > current.get().rows().size()) {
            return Optional.empty();
        }
        return Optional.of(current.get().rows().get(place - 1));
    }

    /**
     * The place a score of {@code score} holds on a board, counted from one, or empty when the score is nothing or
     * the board has not been read in this window yet.
     */
    public OptionalInt placeOf(Board board, BoardPeriod period, long score) {
        Optional<Shown> current = current(board, period);
        return current.isEmpty() || score < 1
                ? OptionalInt.empty()
                : OptionalInt.of(current.get().places().ahead(score) + 1);
    }

    /** The window of {@code period} that holds the present moment. */
    public long windowOf(BoardPeriod period) {
        return settings.get().calendar().windowStart(period, clock.instant());
    }

    /** How long until {@code period} starts again, or empty for all time. */
    public Optional<Duration> untilReset(BoardPeriod period) {
        Instant now = clock.instant();
        return settings.get().calendar().nextStart(period, now).map(next -> Duration.between(now, next));
    }

    /** The window of each period that runs, holding the present moment. */
    public Map<BoardPeriod, Long> openWindows() {
        BoardSettings now = settings.get();
        Instant at = clock.instant();
        Map<BoardPeriod, Long> open = new EnumMap<>(BoardPeriod.class);
        now.periods().forEach(period -> open.put(period, now.calendar().windowStart(period, at)));
        return Map.copyOf(open);
    }

    /** The open windows an event on {@code board} is counted in: the running periods the board keeps. */
    public Map<BoardPeriod, Long> countedWindows(Board board) {
        Objects.requireNonNull(board, "board");
        Map<BoardPeriod, Long> counted = new EnumMap<>(BoardPeriod.class);
        openWindows().forEach((period, window) -> {
            if (board.counts(period)) {
                counted.put(period, window);
            }
        });
        return Map.copyOf(counted);
    }

    /** Read every running board again, and drop the windows past keeping. Run on the async lane only. */
    public void refresh() {
        BoardSettings now = settings.get();
        Map<Key, Shown> read = new HashMap<>();
        openWindows().forEach((period, window) -> {
            for (Board board : named().values()) {
                if (board.runs(period)) {
                    read.put(new Key(board, period), read(board, period, window, now.size()));
                }
            }
        });
        shown = Map.copyOf(read);
        prune(now);
    }

    private Shown read(Board board, BoardPeriod period, long window, int size) {
        Optional<Snapshot> snapshot = board.snapshotFor(period);
        List<Ranked> top =
                snapshot.map(source -> source.top(size)).orElseGet(() -> store.top(board, period, window, size));
        Map<Long, Integer> counted =
                snapshot.map(Snapshot::countByScore).orElseGet(() -> store.countByScore(board, period, window));
        List<Row> rows = top.stream()
                .limit(size)
                .map(ranked -> new Row(ranked.player(), nameOf(ranked), ranked.score()))
                .toList();
        return new Shown(window, rows, Places.of(counted));
    }

    private String nameOf(Ranked ranked) {
        String kept = ranked.name();
        return kept != null ? kept : names.apply(ranked.player());
    }

    private void prune(BoardSettings now) {
        Instant at = clock.instant();
        if (Duration.between(prunedAt, at).compareTo(PRUNE_EVERY) < 0) {
            return;
        }
        prunedAt = at;
        for (BoardPeriod period : now.periods()) {
            if (period != BoardPeriod.ALLTIME) {
                store.forgetBefore(period, now.calendar().windowBack(period, at, now.keepWindows()));
            }
        }
    }

    /** A board as last read, unless its period no longer runs or its window has ended since. */
    private Optional<Shown> current(Board board, BoardPeriod period) {
        Shown read = shown.get(new Key(board, period));
        return read == null || read.window() != windowOf(period) ? Optional.empty() : Optional.of(read);
    }

    /**
     * The scores on record, highest first, and the players ahead of each.
     *
     * <p>{@code ahead[i]} counts the players whose score is higher than {@code scores[i]}, and the one extra entry at
     * the end counts every player. One entry per distinct score rather than per player.
     */
    private static final class Places {

        private final long[] scores;
        private final int[] ahead;

        private Places(long[] scores, int[] ahead) {
            this.scores = scores;
            this.ahead = ahead;
        }

        static Places of(Map<Long, Integer> players) {
            List<Map.Entry<Long, Integer>> highestFirst = players.entrySet().stream()
                    .filter(entry -> entry.getKey() > 0)
                    .sorted(Map.Entry.<Long, Integer>comparingByKey().reversed())
                    .toList();
            long[] scores = new long[highestFirst.size()];
            int[] ahead = new int[scores.length + 1];
            for (int i = 0; i < scores.length; i++) {
                scores[i] = highestFirst.get(i).getKey();
                ahead[i + 1] = ahead[i] + highestFirst.get(i).getValue();
            }
            return new Places(scores, ahead);
        }

        /** How many players have a score higher than {@code score}. */
        int ahead(long score) {
            int higher = 0;
            int notHigher = scores.length;
            while (higher < notHigher) {
                int middle = (higher + notHigher) >>> 1;
                if (scores[middle] > score) {
                    higher = middle + 1;
                } else {
                    notHigher = middle;
                }
            }
            return ahead[higher];
        }
    }
}
