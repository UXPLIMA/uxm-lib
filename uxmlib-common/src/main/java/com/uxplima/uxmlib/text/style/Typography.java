package com.uxplima.uxmlib.text.style;

import java.util.Objects;

/**
 * The letters pass over a message template.
 *
 * <p>A catalog is written in ordinary words and rendered in small capitals, so a translator reads and writes
 * normal letters and no build step ever rewrites the file they work in.
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
        StringBuilder out = new StringBuilder(template.length());
        int index = 0;
        int plainDepth = 0;
        while (index < template.length()) {
            char current = template.charAt(index);
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
