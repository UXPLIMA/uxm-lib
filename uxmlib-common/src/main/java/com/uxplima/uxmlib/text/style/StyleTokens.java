package com.uxplima.uxmlib.text.style;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import com.uxplima.uxmlib.text.Text;
import org.jspecify.annotations.Nullable;

/**
 * Expands the style tokens of a message template into the MiniMessage a client is sent.
 *
 * <p>A catalog line names a role and never a colour: {@code <accent>}, {@code <value>}, {@code <body>}. Each
 * one becomes the colour {@link Theme} holds for that role. Three tokens insert text of their own rather than
 * wrapping it:
 *
 * <ul>
 *   <li>{@code <tag:'HOME'>}: the category prefix a chat line opens with: the word in bold, coloured by what
 *       the category is about, then the separator glyph in the dim colour;
 *   <li>{@code <etag:'ERROR'>}: the same prefix in the failure colour, whichever feature raised the line;
 *   <li>{@code <h:'REWARDS'>}: the bold header a lore block opens with, in the accent colour or in the
 *       theme's {@code header} gradient when it names one. It may name a gradient of its own instead:
 *       {@code <h:'REWARDS':mint>}.
 * </ul>
 *
 * <p>The label of a prefix lives in the template rather than here, because it is a word a player reads and a
 * translator has to be able to move it.
 *
 * <p>Anything that is not a token is copied through, so an operator keeps every MiniMessage tag: a click, a
 * hover, a font, and the placeholders the plugin fills in.
 */
public final class StyleTokens {

    /**
     * A labelled token: {@code <tag:'HOME'>}, {@code <tag:HOME>} or {@code <tag:"HOME">}, and a header with
     * a gradient of its own: {@code <h:'HOME':mint>}.
     */
    private static final Pattern LABELLED = Pattern.compile(
            "<(tag|etag|h):(?:'([^']*)'|\"([^\"]*)\"|([^:>]*))(?::([a-z0-9_-]+))?>", Pattern.CASE_INSENSITIVE);

    /** The gradient a {@code <h:>} header is painted with, when the theme names one. */
    private static final String HEADER = "header";

    /** A colour token, opening or closing: {@code <accent>}, {@code </accent>}. */
    private static final Pattern COLOUR = Pattern.compile("</?([a-z]+)>");

    private StyleTokens() {}

    /**
     * {@code template} with every token replaced by MiniMessage.
     *
     * @param smallCaps whether the label of a prefix is written in small capitals, which follows the language
     *     the line is being rendered for
     * @throws IllegalArgumentException when a prefix token carries no label, which would render as a bare
     *     separator with nothing in front of it
     */
    public static String expand(String template, Theme theme, boolean smallCaps) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(theme, "theme");
        return colours(labels(template, theme, smallCaps), theme);
    }

    private static String labels(String template, Theme theme, boolean smallCaps) {
        Matcher matcher = LABELLED.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1).toLowerCase(Locale.ROOT);
            String label = firstOf(matcher.group(2), matcher.group(3), matcher.group(4));
            if (label.isBlank()) {
                throw new IllegalArgumentException("a <" + token + "> token needs a label: " + template);
            }
            matcher.appendReplacement(
                    out, Matcher.quoteReplacement(expanded(token, label, matcher.group(5), theme, smallCaps)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String expanded(
            String token, String label, @Nullable String gradient, Theme theme, boolean smallCaps) {
        String text = smallCaps ? SmallCaps.of(label) : label;
        return switch (token) {
            case "tag" -> prefix(text, theme.hex(theme.categoryRole(label)), theme);
            case "etag" -> prefix(text, theme.hex("bad"), theme);
            default -> header(text, stopsOf(theme, gradient), theme);
        };
    }

    /**
     * A component painted the way a {@code <h:'…'>} token is: across the theme's {@code header} gradient
     * when it names two stops or more, in the single stop when it names one, and in the accent colour when
     * it names none.
     */
    public static Component header(Theme theme, Component text) {
        return paint(theme, text, HEADER);
    }

    /**
     * {@code text} painted across the theme's gradient named {@code gradient}.
     *
     * <p>This is the seam a menu paints a tile title with, and the reason it takes a name rather than a
     * colour: which gradient a title takes is a decision about one interface, so it belongs to the file that
     * knows what the title is about, and a library that chose for the caller would paint a wardrobe and a
     * lobby list in the same order for reasons neither of them could see.
     *
     * <p>A name the theme does not know falls back to {@code header}, so a spelling mistake shows as the
     * ordinary colour rather than as no colour at all.
     *
     * <p>A component that already carries a colour anywhere inside it is handed back untouched. A header is
     * often not a literal (a lobby name, a rank, a player's chosen tag), and a value that arrived with a
     * colour of its own means it, so painting over it would lose the one thing that made it that value. The
     * check walks the whole component, because a colour a caller set on a child is just as deliberate as one
     * set on the root.
     */
    public static Component paint(Theme theme, Component text, String gradient) {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(gradient, "gradient");
        return painted(theme, text, stopsOf(theme, gradient));
    }

    /** {@code text} across {@code stops}: the gradient, the one stop flat, or the accent when there is none. */
    private static Component painted(Theme theme, Component text, List<TextColor> stops) {
        if (coloured(text)) {
            return text;
        }
        if (stops.size() < 2) {
            return text.color(stops.size() == 1 ? stops.get(0) : theme.colour("accent"));
        }
        StringBuilder tag = new StringBuilder("<gradient");
        for (TextColor stop : stops) {
            tag.append(':').append(hex(stop));
        }
        return Text.mini(tag.append('>')
                .append(Text.serialize(text))
                .append("</gradient>")
                .toString());
    }

    /** Whether any part of {@code component} names a colour, root or child. */
    private static boolean coloured(Component component) {
        if (component.color() != null) {
            return true;
        }
        for (Component child : component.children()) {
            if (coloured(child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The stops a line paints with: the gradient it named, or the theme's header when it named none or named
     * one the theme does not hold.
     */
    private static List<TextColor> stopsOf(Theme theme, @Nullable String gradient) {
        if (gradient == null || HEADER.equalsIgnoreCase(gradient)) {
            return theme.gradient(HEADER);
        }
        List<TextColor> named = theme.gradient(gradient);
        return named.isEmpty() ? theme.gradient(HEADER) : named;
    }

    /**
     * A header: the gradient when it names two stops or more, and the flat accent otherwise. A one-stop
     * gradient is a flat colour, so an operator can switch the effect off by shortening the list rather than
     * by learning a second key.
     */
    private static String header(String text, List<TextColor> stops, Theme theme) {
        if (stops.size() < 2) {
            return bold(text, stops.size() == 1 ? hex(stops.get(0)) : theme.hex("accent"));
        }
        StringBuilder tag = new StringBuilder("<gradient");
        for (TextColor stop : stops) {
            tag.append(':').append(hex(stop));
        }
        return tag.append("><b>").append(text).append("</b></gradient>").toString();
    }

    private static String hex(TextColor colour) {
        return colour.asHexString().toLowerCase(Locale.ROOT);
    }

    /** The word, one ordinary space, then the separator. The gap before the body comes from the catalog. */
    private static String prefix(String label, String hex, Theme theme) {
        return bold(label, hex) + " <color:" + theme.hex("dim") + ">" + theme.separator() + "</color>";
    }

    private static String bold(String text, String hex) {
        return "<b><color:" + hex + ">" + text + "</color></b>";
    }

    private static String colours(String template, Theme theme) {
        Matcher matcher = COLOUR.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String role = matcher.group(1);
            if (!theme.hasColour(role)) {
                continue; // not a role of this theme, so it is somebody else's tag and is left alone
            }
            boolean closing = matcher.group().charAt(1) == '/';
            String replacement = closing ? "</color>" : "<color:" + theme.hex(role) + ">";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** The first alternative the regex actually matched; the others are null by construction. */
    private static String firstOf(@Nullable String... values) {
        for (@Nullable String value : values) {
            if (value != null) {
                return value;
            }
        }
        return "";
    }
}
