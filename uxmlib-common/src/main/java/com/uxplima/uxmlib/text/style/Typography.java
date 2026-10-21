package com.uxplima.uxmlib.text.style;

import java.util.Objects;

import com.uxplima.uxmlib.text.message.CatalogLine;

/**
 * The letters pass over a message template.
 *
 * <p>A catalog is written in ordinary words and rendered in small capitals, so a translator reads and writes
 * normal letters and no build step ever rewrites the file they work in.
 *
 * <p>A key a line names, {@code @menu.title}, is left as written: it is a reference to a catalogue entry, whose own
 * words are converted where that catalogue is, and a converted reference names nothing.
 *
 * <p>Placeholders keep their spelling, because what fills them reads them after this pass: a {@code {name}} a caller
 * substitutes as text, and a {@code %placeholder%} another plugin answers. In small capitals either named nothing and
 * the player read the token. A per cent sign in prose ({@code 50% off}) opens no placeholder and is text.
 *
 * <p>Two kinds of text are left alone. Everything inside angle brackets is a tag or a placeholder, so a
 * colour name, a click action and the {@code <player>} a value arrives through are copied through untouched.
 * Everything inside {@code <plain>…</plain>} is text that must stay in ordinary letters: a version number, a
 * command, an id, a URL. Both markers are removed here, because typography is the only thing they mean and
 * nothing further along the chain has to know about them.
 *
 * <p>A value a player supplies (a name, a world, a nickname) is inserted after the template is parsed, so
 * it never reaches this pass and is always shown as they wrote it. A value that is the interface talking
 * rather than a player is marked in the file with {@link SmallCapsTag}, and that marker is the one thing
 * kept here rather than removed: it is read at render, where the value is. A language that keeps its
 * ordinary letters loses it with everything else, so the tag can never convert on its own.
 */
public final class Typography {

    private static final String PLAIN_OPEN = "<plain>";
    private static final String PLAIN_CLOSE = "</plain>";

    private static final String CAPS_OPEN = "<" + SmallCapsTag.NAME + ">";
    private static final String CAPS_CLOSE = "</" + SmallCapsTag.NAME + ">";

    private Typography() {}

    /**
     * {@code template} with its letters converted when {@code smallCaps} is true, and with the
     * {@code <plain>} markers removed either way.
     */
    public static String apply(String template, boolean smallCaps) {
        Objects.requireNonNull(template, "template");
        if (CatalogLine.parse(template).isPresent()) {
            return template;
        }
        StringBuilder out = new StringBuilder(template.length());
        int index = 0;
        int plainDepth = 0;
        while (index < template.length()) {
            char current = template.charAt(index);
            if (current == '@' && (index == 0 || Character.isWhitespace(template.charAt(index - 1)))) {
                int end = endOfWord(template, index);
                out.append(template, index, end);
                index = end;
                continue;
            }
            int placeholder = endOfPlaceholder(template, index);
            if (placeholder > 0) {
                out.append(template, index, placeholder);
                index = placeholder;
                continue;
            }
            if (current != '<') {
                out.append(smallCaps && plainDepth == 0 ? SmallCaps.of(current) : String.valueOf(current));
                index++;
                continue;
            }
            int end = endOfTag(template, index);
            if (end < 0) {
                // A stray bracket, which MiniMessage shows as text. Treat it as text here too.
                out.append(current);
                index++;
                continue;
            }
            String tag = template.substring(index, end + 1);
            if (PLAIN_OPEN.equals(tag)) {
                plainDepth++;
            } else if (PLAIN_CLOSE.equals(tag)) {
                plainDepth = Math.max(0, plainDepth - 1);
            } else if (CAPS_OPEN.equals(tag) || CAPS_CLOSE.equals(tag)) {
                // The tag converts a value at render, and a value is only converted where the letters of
                // the template are. A language that keeps its letters loses the markers here.
                if (smallCaps && plainDepth == 0) {
                    out.append(tag);
                }
            } else {
                out.append(tag);
            }
            index = end + 1;
        }
        return out.toString();
    }

    /**
     * The index just past the placeholder opening at {@code start}, or -1 when none opens there: a {@code {name}}
     * closed on the same word, or a {@code %name%} of the characters a PlaceholderAPI token is written in.
     */
    private static int endOfPlaceholder(String template, int start) {
        char open = template.charAt(start);
        if (open == '{') {
            for (int index = start + 1; index < template.length(); index++) {
                char current = template.charAt(index);
                if (current == '}') {
                    return index > start + 1 ? index + 1 : -1;
                }
                if (!isNameCharacter(current)) {
                    return -1;
                }
            }
            return -1;
        }
        if (open != '%') {
            return -1;
        }
        for (int index = start + 1; index < template.length(); index++) {
            char current = template.charAt(index);
            if (current == '%') {
                return index > start + 1 ? index + 1 : -1;
            }
            if (!isNameCharacter(current) && current != '{' && current != '}') {
                return -1;
            }
        }
        return -1;
    }

    private static boolean isNameCharacter(char current) {
        return Character.isLetterOrDigit(current)
                || current == '_'
                || current == '-'
                || current == '.'
                || current == ':';
    }

    /** The index just past the word starting at {@code start}: the next space, or the end. */
    private static int endOfWord(String template, int start) {
        int index = start;
        while (index < template.length() && !Character.isWhitespace(template.charAt(index))) {
            index++;
        }
        return index;
    }

    /**
     * The index of the bracket that closes the tag opening at {@code start}, or -1 when nothing closes it. A
     * quoted argument may hold a bracket of its own ({@code <hover:show_text:'a > b'>}), so a bracket inside
     * quotes does not end the tag.
     */
    private static int endOfTag(String template, int start) {
        char quote = 0;
        for (int index = start + 1; index < template.length(); index++) {
            char current = template.charAt(index);
            if (quote != 0) {
                if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
            } else if (current == '>') {
                return index;
            }
        }
        return -1;
    }
}
