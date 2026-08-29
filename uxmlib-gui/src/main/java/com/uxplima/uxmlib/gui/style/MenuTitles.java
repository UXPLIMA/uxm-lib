package com.uxplima.uxmlib.gui.style;

import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.text.GlyphWidthTable;

/**
 * Centres the title of a chest window.
 *
 * <p>The client draws a window title from a fixed origin and offers no way to align it, so the only way to
 * centre one is to put spaces in front of it. The arithmetic is the client's own layout: the window is 176
 * pixels wide, the label starts 8 pixels in from the left edge, and a space advances 4. Forgetting the origin
 * is what makes a whole menu look subtly wrong — it pushes every title two spaces right and the rounding then
 * lands differently for each length.
 *
 * <p>The styling is dropped rather than trusted to every catalog line: a title is flattened to plain text and
 * handed back as one plain component, so a key that still carries a colour cannot paint a two-tone title.
 */
public final class MenuTitles {

    /** The inside width of a chest window, in pixels. */
    private static final int WINDOW_WIDTH = 176;

    /** How far in from the edge the client starts drawing the title. */
    private static final int TITLE_ORIGIN = 8;

    private static final String SPACE = " ";

    private MenuTitles() {}

    /** {@code title} with every colour removed and enough leading spaces to sit in the middle of the window. */
    public static Component centre(Component title) {
        Objects.requireNonNull(title, "title");
        String plain = PlainTextComponentSerializer.plainText().serialize(title);
        if (plain.isBlank()) {
            return title;
        }
        int free = WINDOW_WIDTH - 2 * TITLE_ORIGIN - widthOf(plain);
        int spaces = Math.round(free / (2f * GlyphWidthTable.SPACE_WIDTH));
        return Component.text(spaces <= 0 ? plain : SPACE.repeat(spaces) + plain);
    }

    private static int widthOf(String plain) {
        int total = 0;
        for (int index = 0; index < plain.length(); index++) {
            total += GlyphWidthTable.widthOf(plain.charAt(index), false);
        }
        return total;
    }
}
