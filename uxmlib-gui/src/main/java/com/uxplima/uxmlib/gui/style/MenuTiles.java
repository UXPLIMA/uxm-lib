package com.uxplima.uxmlib.gui.style;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmlib.text.message.MessageKey;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;

/**
 * A menu tile, in the six blocks a window draws: the title, the category under it, the description, the
 * facts, and the line that says what a click does.
 *
 * <p>A menu file draws a tile by writing one lore line of the shape {@code tile:<colour> @<key> [fact ...]}.
 * The colour is a gradient of {@code theme.conf}, a role, or a place on the wheel written as a number, and it
 * is written in the file rather than in the catalogue because which tiles on a screen should look alike is a
 * question about the screen and not about the words. The key names a block of the catalogue, and the facts
 * name the rows of that block, in the order the file writes them: an operator who wants one fact fewer takes
 * a word off the line, and a translator never sees the layout at all.
 *
 * <p>A word written with a minus in front of it leaves a block out: {@code -action} draws a tile that answers
 * no click without a second block of words for it. That is what a tab of a window needs, which says the same
 * thing about itself whether or not you can click it.
 *
 * <p>A word written {@code state:<fact>} draws that fact as a state rather than as a fact: it takes the theme's
 * {@code status} glyph and its value is not painted in the value colour. {@code state:<fact>:<role>} names the
 * role of {@code theme.conf} the value takes when it carries none of its own, and the role may be a
 * {@code %token%}, so a plugin answers {@code good} or {@code bad} and the theme says what either one looks
 * like. See {@link #STATE_MARK}.
 *
 * <p>A word written as {@code action:@<key>} takes the closing sentence from the key it names instead of from
 * {@code <key>.action} under the block. It reads like the mark that opens the line, a name and then what it is
 * set to, and it can sit anywhere after the block key. The sentence stays a line of the catalogue, so it keeps
 * the colour it is written in and the translator keeps the words:
 *
 * <pre>
 *  lore = ["tile:%lobby_colour% @menu.lobby %lobby_players% state:open:%lobby_state% action:@menu.action.%lobby_state%"]
 * </pre>
 *
 * <p>That is for a tile which says one thing about itself and a different thing about the click, state by
 * state. Without it a file has to write the whole block again for each state, title and crumb and facts and
 * all, in every language it ships. A {@code %token%} inside the key is filled in before the line is read as a
 * tile, so the state can be a placeholder. A key nobody translated leaves the sentence off, exactly as a
 * missing {@code .action} does, and {@code -action} still leaves the block out whether or not a key is named.
 *
 * <p>The shape is the library's and the look is not. {@link Lore} holds the glyphs, the columns and the air
 * between the blocks, and reads all three from the theme, so a server that renames a glyph or a colour
 * changes every tile of every window at once.
 */
public final class MenuTiles {

    /** What a lore line starts with to draw a tile rather than a line. */
    public static final String MARK = "tile:";

    /**
     * What a word of a tile line starts with to name the catalogue line the closing sentence comes from.
     *
     * <p>It carries a colon, so it can never be read as a fact: a fact is one bare word and the row it draws is
     * {@code <key>.<fact>.label}, which a colon has no place in. A tile whose facts include one called
     * {@code action} is untouched by this.
     */
    public static final String ACTION_MARK = "action:";

    /**
     * What a word of a tile line starts with to draw that fact as a state rather than as a fact.
     *
     * <p>A fact is a number, a name or a duration, and it reads in the value colour because that is what a value
     * is. A state is a word that means something is good or bad or wants attention: a minion that is idle, an
     * enchant that cannot be applied, a rate that is being held down. Drawn as a fact it reads in the value colour
     * with every other number on the tile, which is the one thing it must not do.
     *
     * <p>{@code state:<fact>} draws the fact through {@link Lore#status}, which leaves the value's own colour
     * alone rather than painting it, and marks the line with the theme's {@code status} glyph instead of its
     * {@code row} one.
     *
     * <p>{@code state:<fact>:<role>} says which role of {@code theme.conf} the value takes when it carries no
     * colour of its own: {@code state:power:good}. The role may be a {@code %token%}, which is what makes the
     * colour follow the state rather than the file: {@code state:power:%minion_health%} lets the plugin answer
     * {@code good} or {@code bad} and the theme say what either one looks like.
     *
     * <p>The plugin names a role and never a colour, and a name the theme does not hold paints nothing. So the
     * value a player controls still cannot repaint a tile, which is the reason a value goes in as text and never
     * as markup, and that reason is untouched here.
     */
    public static final String STATE_MARK = "state:";

    /** The word over the description block, and the word over the facts. Both are the same in every window. */
    private static final String DESCRIPTION = "menu.lore.description";

    private static final String DETAILS = "menu.lore.details";

    private static final String TITLE = ".title";

    private static final String CRUMB = ".crumb";

    private static final String TEXT = ".description";

    private static final String ACTION = ".action";

    private static final String LABEL = ".label";

    private static final String VALUE = ".value";

    private final Messages messages;
    private final Styler styler;

    public MenuTiles(Messages messages, Styler styler) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.styler = Objects.requireNonNull(styler, "styler");
    }

    /** Whether {@code written} is a tile rather than one line of words. */
    public static boolean marks(String written) {
        Objects.requireNonNull(written, "written");
        return written.startsWith(MARK);
    }

    /**
     * The whole tooltip of one tile, as a single component with the line breaks in it. The item builder
     * splits them, so a tile is one entry of the {@code lore} list of the menu file and not six.
     *
     * <p>The viewer is an {@link Audience} rather than a player because what a tile takes from it is a language and
     * nothing else. A player brings their own, and every other audience brings the catalogue's default, which is the
     * honest answer for a draw that has nobody in front of it: a tile asked for with no viewer is still drawn as a
     * tile, in the language the catalogue is written in, rather than handed back as the characters an operator typed.
     */
    public Component lore(Audience viewer, String written, TagResolver... resolvers) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(written, "written");
        Spec spec = Spec.read(written);
        Lore lore = Lore.of(styler.theme());
        if (spec.draws(CRUMB) && has(viewer, spec.key + CRUMB)) {
            lore.crumb(words(viewer, spec.key + CRUMB, resolvers));
        }
        if (spec.draws(TEXT) && has(viewer, spec.key + TEXT)) {
            lore.description(words(viewer, DESCRIPTION, resolvers), words(viewer, spec.key + TEXT, resolvers));
        }
        if (!spec.facts.isEmpty()) {
            lore.details(words(viewer, DETAILS, resolvers));
            for (Fact fact : spec.facts) {
                Component label = words(viewer, spec.key + "." + fact.name() + LABEL, resolvers);
                Component value = words(viewer, spec.key + "." + fact.name() + VALUE, resolvers);
                if (fact.state()) {
                    lore.status(label, painted(value, fact.role()));
                } else {
                    lore.row(label, value);
                }
            }
        }
        String action = spec.action();
        if (spec.draws(ACTION) && has(viewer, action)) {
            lore.action(words(viewer, action, resolvers));
        }
        return Tiles.titled(styler.theme(), words(viewer, spec.key + TITLE, resolvers), lore.build(), spec.colour);
    }

    /** The line of the catalogue at {@code path}, in the language of the viewer, with the values written in. */
    private Component words(Audience viewer, String path, TagResolver... resolvers) {
        return messages.render(viewer, MessageKey.of(path, path), resolvers);
    }

    /**
     * Whether the catalogue holds a line at {@code path}.
     *
     * <p>A block that leaves one out draws a tile without it rather than a tile with the key printed on it.
     * That is what lets one shape serve a tile that answers no click and a tile that answers two.
     */
    private boolean has(Audience viewer, String path) {
        return messages.catalog()
                .find(MessageKey.of(path, path), messages.localeOf(viewer))
                .isPresent();
    }

    /**
     * A state's value in the role the line named, when it carries no colour of its own.
     *
     * <p>A value the catalogue already painted keeps that colour, because a line a translator coloured means it.
     * A role the theme does not hold paints nothing at all rather than something invented, so a spelling mistake
     * and a {@code %token%} the plugin left unanswered both read as an unpainted state rather than as a colour
     * nobody chose.
     */
    private Component painted(Component value, String role) {
        if (role.isEmpty() || !styler.theme().hasColour(role)) {
            return value;
        }
        return value.colorIfAbsent(styler.theme().colour(role));
    }

    /**
     * One word of the facts part of a tile line: which row of the block it draws, whether it is a state, and the
     * role a state's value takes when it has no colour of its own.
     */
    private record Fact(String name, boolean state, String role) {

        /** A plain fact, drawn in the value colour. */
        static Fact of(String name) {
            return new Fact(name, false, "");
        }

        /** {@code state:<fact>} or {@code state:<fact>:<role>}, with an empty role for the shorter form. */
        static Fact state(String written) {
            int at = written.indexOf(':');
            return at < 0
                    ? new Fact(written, true, "")
                    : new Fact(written.substring(0, at), true, written.substring(at + 1));
        }
    }

    /**
     * What one {@code tile:} line names: the colour of the title, the block of words, the facts, the closing
     * sentence when the line names one of its own, and what it leaves out.
     */
    private record Spec(String colour, String key, List<Fact> facts, Set<String> without, String named) {

        private static final Pattern WORDS = Pattern.compile("\\s+");

        static Spec read(String written) {
            String[] words = WORDS.split(written.trim(), -1);
            String colour = words[0].substring(MARK.length());
            String key = words.length > 1 ? name(words[1]) : "";
            List<Fact> facts = new ArrayList<>();
            Set<String> without = new LinkedHashSet<>();
            String named = "";
            for (int at = 2; at < words.length; at++) {
                if (words[at].startsWith("-")) {
                    without.add("." + words[at].substring(1));
                } else if (words[at].startsWith(ACTION_MARK)) {
                    named = name(words[at].substring(ACTION_MARK.length()));
                } else if (words[at].startsWith(STATE_MARK)) {
                    // A mark with nothing after it names no row, exactly as an action mark with nothing after
                    // it names no key, so it draws nothing rather than a row whose paths are all dots.
                    String marked = words[at].substring(STATE_MARK.length());
                    if (!marked.isEmpty() && marked.charAt(0) != ':') {
                        facts.add(Fact.state(marked));
                    }
                } else if (!words[at].isEmpty()) {
                    facts.add(Fact.of(words[at]));
                }
            }
            return new Spec(colour, key, List.copyOf(facts), Set.copyOf(without), named);
        }

        /**
         * The catalogue line the closing sentence comes from: the one this line names, or the one under the
         * block's own key when it names none. A line that writes the mark and nothing after it names none.
         */
        String action() {
            return named.isEmpty() ? key + ACTION : named;
        }

        /** Whether the tile draws the block this line does not ask to leave out. */
        boolean draws(String part) {
            return !without.contains(part);
        }

        /** The key as the catalogue spells it, with the {@code @} a menu file marks a key with taken off. */
        private static String name(String written) {
            return written.startsWith("@") ? written.substring(1) : written;
        }
    }
}
