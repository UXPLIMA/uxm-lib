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
 * is what makes a whole menu look subtly wrong: it pushes every title two spaces right and the rounding then
 * lands differently for each length.
 *
 * <p>What the title says and how it looks are the caller's: the padding is measured from the plain letters,
 * and the component is handed back with whatever style it arrived in. A window title that should carry no
 * colour is a house rule, and a house rule belongs to the house rather than to this class.
 */
public final class MenuTitles {

    /** The inside width of a chest window, in pixels. */
    private static final int WINDOW_WIDTH = 176;

    /** How far in from the edge the client starts drawing the title. */
    private static final int TITLE_ORIGIN = 8;

    private static final String SPACE = " ";

    private MenuTitles() {}

    /** {@code title} with enough leading spaces in front of it to sit in the middle of the window. */
    public static Component centre(Component title) {
        Objects.requireNonNull(title, "title");
        String plain = PlainTextComponentSerializer.plainText().serialize(title);
        if (plain.isBlank()) {
            return title;
        }
        int free = WINDOW_WIDTH - 2 * TITLE_ORIGIN - GlyphWidthTable.widthOf(plain, false);
        int spaces = Math.round(free / (2f * GlyphWidthTable.SPACE_WIDTH));
        return spaces <= 0 ? title : Component.text(SPACE.repeat(spaces)).append(title);
    }
}
