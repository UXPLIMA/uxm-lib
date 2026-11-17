package com.uxplima.uxmlib.board;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;

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
 *
 * <p>A head is answered two ways. {@link #answer} gives the text a {@code <head:...>} tag takes: a name, an id or an
 * asset path. {@link #headComponent} gives the head itself, which can also be drawn from a {@link StoredSkin}, a
 * skin the plugin keeps for an offline-mode or Bedrock player that no tag argument can name.
 */
public final class BoardAnswers {

    private static final String TOP = "top_";
    private static final String RANK = "rank_";
    private static final String VALUE = "value_";
    private static final String RESETS = "resets_";
    private static final String HEAD = "head";

    /** The profile property a skin is carried in. */
    private static final String TEXTURES = "textures";

    /** A name an account can have. Anything else, a Bedrock name with its prefix for one, is drawn by its id. */
    private static final Pattern ACCOUNT_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    /** The uuid version Mojang gives an account; the client can ask Mojang only about these. */
    private static final int MOJANG_UUID_VERSION = 4;

    private record Target(Board board, BoardPeriod period) {}

    private final Leaderboard leaderboard;
    private final Standings standings;
    private final BoardText text;
    private final Supplier<String> emptyHead;
    private final Function<UUID, Optional<String>> storedHead;
    private final Function<UUID, Optional<StoredSkin>> storedSkin;

    /** @param emptyHead read on every answer, so a reload changes it */
    public BoardAnswers(Leaderboard leaderboard, Standings standings, BoardText text, Supplier<String> emptyHead) {
        this(leaderboard, standings, text, emptyHead, player -> Optional.empty());
    }

    /**
     * @param emptyHead read on every answer, so a reload changes it
     * @param storedHead the head the plugin keeps for a player, as a texture path the tag takes, answered from
     *     memory since a placeholder may be read on a server thread; it is drawn before anything else
     */
    public BoardAnswers(
            Leaderboard leaderboard,
            Standings standings,
            BoardText text,
            Supplier<String> emptyHead,
            Function<UUID, Optional<String>> storedHead) {
        this(leaderboard, standings, text, emptyHead, storedHead, player -> Optional.empty());
    }

    /**
     * @param emptyHead read on every answer, so a reload changes it
     * @param storedHead the head the plugin keeps for a player, as a texture path the tag takes, answered from
     *     memory since a placeholder may be read on a server thread; it is drawn before anything else
     * @param storedSkin the skin the plugin keeps for a player, answered from memory; {@link #headComponent} draws it
     *     before anything else, while {@link #answer} cannot carry it
     */
    public BoardAnswers(
            Leaderboard leaderboard,
            Standings standings,
            BoardText text,
            Supplier<String> emptyHead,
            Function<UUID, Optional<String>> storedHead,
            Function<UUID, Optional<StoredSkin>> storedSkin) {
        this.leaderboard = Objects.requireNonNull(leaderboard, "leaderboard");
        this.standings = Objects.requireNonNull(standings, "standings");
        this.text = Objects.requireNonNull(text, "text");
        this.emptyHead = Objects.requireNonNull(emptyHead, "emptyHead");
        this.storedHead = Objects.requireNonNull(storedHead, "storedHead");
        this.storedSkin = Objects.requireNonNull(storedSkin, "storedSkin");
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
            return reader == null ? null : head(reader, readerName);
        }
        return null;
    }

    /**
     * The head {@code request} asks for, as a component to put in a line, or null for a request that is not a head
     * ({@code head} and {@code top_<board>_<period>_<n>_head} are) or that names a board, period or place the plugin
     * does not have. The reader's own head needs a reader.
     *
     * <p>A player is drawn from the skin the plugin keeps for them first, then as {@link #head(UUID, String)} says,
     * and a place nobody holds as the empty head. A skin is drawn from its {@code textures} property alone, so the
     * client never asks Mojang who the player is. Text is read the way the {@code <head:...>} tag reads its argument:
     * an id, an asset path, or a name. A plugin answers a component placeholder with this, such as a MiniPlaceholders
     * tag, where a line can hold a component rather than text.
     */
    public @Nullable Component headComponent(@Nullable UUID reader, @Nullable String readerName, String request) {
        Objects.requireNonNull(request, "request");
        if (request.equals(HEAD)) {
            return reader == null ? null : Component.object(headContents(reader, readerName));
        }
        if (!request.startsWith(TOP)) {
            return null;
        }
        Optional<Place> place = place(request.substring(TOP.length()));
        if (place.isEmpty() || !place.get().field().equals("head")) {
            return null;
        }
        Optional<Leaderboard.Row> row = place.get().row();
        PlayerHeadObjectContents head =
                row.isPresent() ? headContents(row.get().player(), row.get().name()) : headFrom(emptyHead.get());
        return Component.object(head);
    }

    /** {@code player}'s head: from the skin the plugin keeps, else from what {@link #head} answers. */
    private PlayerHeadObjectContents headContents(UUID player, @Nullable String name) {
        Optional<StoredSkin> skin = storedSkin.apply(player);
        if (skin.isEmpty()) {
            return headFrom(head(player, name));
        }
        String value = skin.get().value();
        String signature = skin.get().signature();
        return ObjectContents.playerHead()
                .profileProperty(
                        signature == null
                                ? PlayerHeadObjectContents.property(TEXTURES, value)
                                : PlayerHeadObjectContents.property(TEXTURES, value, signature))
                .build();
    }

    /**
     * A head from tag text, read as the {@code <head:...>} tag reads it: an id, an asset path holding a {@code /}, or
     * a name. Text that is none of them, a misconfigured empty head for one, is the client's default head.
     */
    private static PlayerHeadObjectContents headFrom(String argument) {
        try {
            return ObjectContents.playerHead(UUID.fromString(argument));
        } catch (IllegalArgumentException notAnId) {
            // Read as a path or a name below.
        }
        if (argument.contains("/") && Key.parseable(argument)) {
            return ObjectContents.playerHead().texture(Key.key(argument)).build();
        }
        if (PlayerHeadObjectContents.isValidName(argument)) {
            return ObjectContents.playerHead(argument);
        }
        return ObjectContents.playerHead().build();
    }

    /**
     * What a {@code <head:...>} tag draws {@code player} from: the head the plugin keeps for them, else as
     * {@link #headOf(UUID, String, String)} says, with the configured empty head.
     */
    public String head(UUID player, @Nullable String name) {
        Objects.requireNonNull(player, "player");
        Optional<String> stored = storedHead.apply(player).filter(texture -> !texture.isBlank());
        return stored.isPresent() ? stored.get() : headOf(player, name, emptyHead.get());
    }

    /**
     * What a {@code <head:...>} tag draws a player from, given no head of their own. The tag hands a name or an id to
     * the client, which asks Mojang about it, so only an account Mojang made (a version 4 id) is drawn that way: by
     * its name when an account can have it, else by its id. An offline-mode account (version 3) or a Bedrock one
     * through Floodgate (version 0) is drawn as {@code emptyHead}, since Mojang would answer with whoever holds its
     * name there, or with no one.
     */
    public static String headOf(UUID player, @Nullable String name, String emptyHead) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(emptyHead, "emptyHead");
        if (player.version() != MOJANG_UUID_VERSION) {
            return emptyHead;
        }
        return name != null && ACCOUNT_NAME.matcher(name).matches() ? name : player.toString();
    }

    /**
     * As {@link #headOf(UUID, String, String)}, with the stock Steve head for an account Mojang did not make.
     *
     * @deprecated a board has a configured empty head: use {@link #head(UUID, String)}, or the three-argument form
     */
    @Deprecated
    @SuppressWarnings("InlineMeSuggester") // inlining would hard-code Steve where a caller has its own empty head
    public static String headOf(UUID player, @Nullable String name) {
        return headOf(player, name, BoardSettings.STEVE);
    }

    /** A place on a board in a period, the field asked of it, and who holds it, if anyone. */
    private record Place(Target target, String field, Optional<Leaderboard.Row> row) {}

    /** {@code <board>_<period>_<n>_<field>}, for a board and period the plugin has. */
    private Optional<Place> place(String params) {
        // Read from the end: a board's word may hold an underscore, the period, place and field never do.
        String[] parts = params.split("_", -1);
        int last = parts.length - 1;
        if (parts.length < 4) {
            return Optional.empty();
        }
        Optional<Target> target = target(String.join("_", Arrays.copyOfRange(parts, 0, last - 2)), parts[last - 2]);
        OptionalInt place = number(parts[last - 1]);
        if (target.isEmpty() || place.isEmpty()) {
            return Optional.empty();
        }
        Optional<Leaderboard.Row> row =
                leaderboard.at(target.get().board(), target.get().period(), place.getAsInt());
        return Optional.of(new Place(target.get(), parts[last], row));
    }

    /** {@code <board>_<period>_<n>_<field>}. */
    private @Nullable String top(@Nullable UUID reader, String params) {
        Optional<Place> found = place(params);
        if (found.isEmpty()) {
            return null;
        }
        Board board = found.get().target().board();
        BoardPeriod period = found.get().target().period();
        Optional<Leaderboard.Row> row = found.get().row();
        return switch (found.get().field()) {
            case "name" -> row.map(Leaderboard.Row::name).orElseGet(() -> text.nobody(reader));
            case "value" ->
                row.map(held -> text.score(board, period, held.score(), reader)).orElseGet(() -> text.none(reader));
            case "head" -> row.map(held -> head(held.player(), held.name())).orElseGet(emptyHead);
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
        BoardPeriod period = target.get().period();
        return text.score(board, period, standings.scoreOf(reader, board, period), reader);
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
        int split = params.lastIndexOf('_');
        return split < 1 ? Optional.empty() : target(params.substring(0, split), params.substring(split + 1));
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
