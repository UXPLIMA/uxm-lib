package com.uxplima.uxmlib.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmlib.text.Text;

/**
 * Word-wraps lore lines to a maximum visible width, so a long description in config renders as several
 * tidy lines instead of one that runs off the tooltip. Operates on raw MiniMessage strings: width is
 * measured against the <em>visible</em> text (tags stripped) while the wrapped output keeps every tag, so
 * colour and formatting survive the split. An explicit {@code \n} always starts a new line.
 *
 * <p>This is a pure string transform with no Bukkit or Adventure rendering: {@link ItemConfig} feeds the
 * wrapped lines through MiniMessage afterwards.
 */
public final class Lore {

    private Lore() {}

    /**
     * Split {@code line} on {@code \n} and greedily pack its words so each output line's visible width is
     * at most {@code maxWidth}. A single word longer than {@code maxWidth} is kept whole on its own line
     * rather than broken mid-word.
     *
     * @throws IllegalArgumentException if {@code maxWidth} is not positive
     */
    public static List<String> wrap(String line, int maxWidth) {
        Objects.requireNonNull(line, "line");
        if (maxWidth < 1) {
            throw new IllegalArgumentException("maxWidth must be >= 1");
        }
        List<String> out = new ArrayList<>();
        for (String segment : line.split("\n", -1)) {
            wrapSegment(segment, maxWidth, out);
        }
        return out;
    }

    private static void wrapSegment(String segment, int maxWidth, List<String> out) {
        StringBuilder current = new StringBuilder();
        int visible = 0;
        for (String word : segment.split(" ", -1)) {
            if (word.isEmpty()) {
                continue;
            }
            int wordWidth = visibleLength(word);
            if (visible > 0 && visible + 1 + wordWidth > maxWidth) {
                out.add(current.toString());
                current.setLength(0);
                visible = 0;
            }
            if (visible > 0) {
                current.append(' ');
                visible++;
            }
            current.append(word);
            visible += wordWidth;
        }
        out.add(current.toString());
    }

    private static int visibleLength(String word) {
        return Text.stripTags(word).length();
    }
}
