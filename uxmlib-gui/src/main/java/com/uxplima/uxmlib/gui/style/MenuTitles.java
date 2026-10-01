package com.uxplima.uxmlib.gui.style;

import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.text.GlyphWidthTable;
import com.uxplima.uxmlib.text.style.TitleAlignment;

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
 *
 * <p>A title whose width the plain letters do not give is left where the client draws it. A translated key is drawn
 * as whatever the client's language or resource pack says, a font from a pack is drawn at the pack's widths, and a
 * keybind as the player's own key, so padding measured from the letters lands a few pixels off, every time. A server
 * drawing its windows with a pack lines those titles up itself, and padding is what broke them.
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
        // A title that already starts with a space was laid out by whoever wrote it, this class included: centring
        // it again would measure the padding as part of the title and push it right of the middle.
        if (plain.isBlank() || plain.startsWith(SPACE) || !measurable(title)) {
            return title;
        }
        int free = WINDOW_WIDTH - 2 * TITLE_ORIGIN - GlyphWidthTable.widthOf(plain, false);
        int spaces = Math.round(free / (2f * GlyphWidthTable.SPACE_WIDTH));
        return spaces <= 0 ? title : Component.text(SPACE.repeat(spaces)).append(title);
    }

    /** {@code title} where {@code alignment} puts it: centred as {@link #centre} does, or exactly as written. */
    public static Component lay(Component title, TitleAlignment alignment) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(alignment, "alignment");
        return alignment == TitleAlignment.LEFT ? title : centre(title);
    }

    /** Whether every part of {@code component} is literal text in the default font, the only width this class knows. */
    private static boolean measurable(Component component) {
        if (!(component instanceof TextComponent) || component.style().font() != null) {
            return false;
        }
        for (Component child : component.children()) {
            if (!measurable(child)) {
                return false;
            }
        }
        return true;
    }
}
