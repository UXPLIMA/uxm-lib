package com.uxplima.uxmlib.menu.render;

import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Where a value a player typed lands in a menu line, and what it has to become to stay the words they typed there.
 *
 * <p>A menu line is MiniMessage, so a typed value that goes in as written is read as markup: a player who types a click
 * tag into a menu argument gets a button that runs a command for whoever clicks it. What makes a value safe depends on
 * where it sits. In the words of a line, MiniMessage reads a backslash before {@code <} or another backslash, so both
 * are escaped. Inside a quoted tag argument the quote would end the argument, so the quote and the backslash are
 * escaped instead; a click or insertion argument is then text, while any other argument (a hover's) is read as
 * MiniMessage again and is escaped for that too.
 *
 * <p>A spot knows how to treat a value at its own top level and what the line it sits in does to everything written
 * at it. A menu-local placeholder expands to a template an operator wrote, and a typed value inside that template sits
 * both in the template and wherever the template went, which is what {@link #at} composes.
 *
 * <p>Public because the engine fits typed values in two packages: the renderer's lines here, and the line an action
 * such as {@code message} sends, in {@code runtime}.
 */
public final class ValueSpot {

    /** A value read as data: a material, an amount, a condition. It goes in as written. */
    public static final ValueSpot DATA = new ValueSpot(UnaryOperator.identity(), UnaryOperator.identity(), false);

    /**
     * A value in the key of a {@code @key} line, or in a line whose arguments are built from tokens. Only the
     * characters a key may hold are kept, so a value cannot end the key, add an argument or open a quote.
     */
    public static final ValueSpot KEY = new ValueSpot(ValueSpot::asKey, UnaryOperator.identity(), false);

    /** A value in the words of a line an operator wrote. */
    public static final ValueSpot TEXT = new ValueSpot(ValueSpot::asMiniMessage, UnaryOperator.identity(), true);

    /** The tags whose argument MiniMessage keeps as text rather than reading it again as MiniMessage. */
    private static final Pattern TEXT_ARGUMENT = Pattern.compile("(?:click|insert|insertion)(?::.*)?", Pattern.DOTALL);

    private static final Pattern NOT_KEY = Pattern.compile("[^A-Za-z0-9_.-]");

    private final UnaryOperator<String> value;
    private final UnaryOperator<String> around;
    private final boolean positional;

    private ValueSpot(UnaryOperator<String> value, UnaryOperator<String> around, boolean positional) {
        this.value = value;
        this.around = around;
        this.positional = positional;
    }

    /** {@code typed} made safe to go in at this spot. */
    public String fit(String typed) {
        return value.apply(typed);
    }

    /**
     * The spot of position {@code at} of {@code line}, a line or template that itself sits at this spot. A data or key
     * spot is the same everywhere in it.
     */
    public ValueSpot at(String line, int at) {
        if (!positional) {
            return this;
        }
        int tagStart = -1;
        char quote = 0;
        for (int i = 0; i < at; i++) {
            char c = line.charAt(i);
            if (tagStart < 0) {
                if (c == '\\') {
                    i++;
                } else if (c == '<') {
                    tagStart = i;
                }
            } else if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '>') {
                tagStart = -1;
            } else if (c == '\'' || c == '"') {
                quote = c;
            }
        }
        if (tagStart < 0) {
            return this;
        }
        if (quote == 0) {
            // An unquoted argument ends at the next colon or bracket, which nothing can escape, so the most a value
            // there can do is end the tag early; what follows it is words, and words are escaped.
            return new ValueSpot(compose(around, ValueSpot::asMiniMessage), around, true);
        }
        char mark = quote;
        UnaryOperator<String> inQuote = compose(around, typed -> asArgument(typed, mark));
        String tag = line.substring(tagStart + 1, at).toLowerCase(Locale.ROOT);
        UnaryOperator<String> typedHere =
                TEXT_ARGUMENT.matcher(tag).matches() ? inQuote : compose(inQuote, ValueSpot::asMiniMessage);
        return new ValueSpot(typedHere, inQuote, true);
    }

    /** {@code text} as words in MiniMessage, which reads a backslash before {@code <} or another backslash. */
    static String asMiniMessage(String text) {
        if (text.indexOf('<') < 0 && text.indexOf('\\') < 0) {
            return text;
        }
        return text.replace("\\", "\\\\").replace("<", "\\<");
    }

    /** {@code text} inside an argument quoted with {@code quote}: MiniMessage reads a backslash before either back. */
    static String asArgument(String text, char quote) {
        return text.replace("\\", "\\\\").replace(String.valueOf(quote), "\\" + quote);
    }

    /** {@code text} with every character a catalog key may not hold taken out. */
    static String asKey(String text) {
        return NOT_KEY.matcher(text).replaceAll("");
    }

    /** {@code inner} first, then {@code outer}. */
    private static UnaryOperator<String> compose(UnaryOperator<String> outer, UnaryOperator<String> inner) {
        return text -> outer.apply(inner.apply(text));
    }
}
