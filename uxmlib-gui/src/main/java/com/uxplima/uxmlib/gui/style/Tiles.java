package com.uxplima.uxmlib.gui.style;

import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.text.style.StyleTokens;
import com.uxplima.uxmlib.text.style.Theme;

/**
 * Puts the title of a menu tile on the first line of its lore, under a blank name.
 *
 * <p>The client draws an item's display name hard against the top edge of the tooltip and will not put a line
 * above it. A blank name buys that line of air, and the title then reads as the first thing inside the
 * tooltip rather than as its lid; a blank line closes it the same way, so the text sits in a box of air.
 *
 * <p>The blank name is a single space and not an empty component. That is the piece of client behaviour worth
 * remembering: an empty name makes the client fall back to the material's own name, and the tile then says
 * "Ender Eye" where the blank line belongs.
 *
 * <p>A button is not a tile. A page arrow or a filler pane keeps its one-line name and carries no lore at
 * all, so {@link #titled} hands such an item back untouched.
 */
public final class Tiles {

    /** Every lore line is padded one space either side, so no text touches the edge of the tooltip. */
    public static final String PADDING = " ";

    private Tiles() {}

    /**
     * The name a titled tile carries: one space, never {@link Component#empty()}.
     *
     * <p>A blank name is a value here rather than the absence of one, which matters to anything that reads
     * a name from a file: {@code isBlank} answers true for the name a titled tile is supposed to have, so a
     * reader that treats blank as "not configured" will quietly hand the tile back its material name.
     * Absent and blank have to be different tests.
     */
    public static Component blankName() {
        return Component.text(PADDING);
    }

    /**
     * The lore of a tile: the title line, the lore as it was written, and a blank line to close the box. The
     * result is one component with newlines in it, which the item builder splits into lines.
     *
     * <p>The closing blank is only added when the lore does not already end on one. {@link Lore#build()}
     * closes its own box, so a tile built the usual way would otherwise end on two blank lines and sit a
     * line higher than every other tile.
     */
    public static Component titled(Theme theme, Component title, Component lore) {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(lore, "lore");
        if (isBlank(title)) {
            return lore;
        }
        Component titled = head(theme, title).append(Component.newline()).append(lore);
        return endsBlank(lore) ? titled : titled.append(Component.newline()).append(Component.text(PADDING));
    }

    /**
     * The title line on its own: the theme's title glyph in the icon colour, then the title, bold and
     * painted the way {@link StyleTokens#header} paints one: the header gradient when the theme names one,
     * the accent colour when it does not.
     *
     * <p>It paints as well as bolds because a catalog that writes a title writes words and nothing else,
     * which is the right assumption for it to make: this line forces bold, so it owns the look of the line,
     * and a title left unpainted falls back to the client's own lore colour rather than to the theme. A
     * title that arrives already carrying a colour (a lobby name, a rank) keeps it.
     */
    public static Component head(Theme theme, Component title) {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(title, "title");
        return Component.text(PADDING)
                .append(Component.text(theme.glyph("title") + PADDING, theme.colour("icon")))
                .append(StyleTokens.header(theme, title).decoration(TextDecoration.BOLD, true))
                .append(Component.text(PADDING));
    }

    /** Whether {@code lore} already ends on a blank line, so closing it again would double the air. */
    private static boolean endsBlank(Component lore) {
        String plain = PlainTextComponentSerializer.plainText().serialize(lore);
        int lastBreak = plain.lastIndexOf('\n');
        return lastBreak >= 0 && plain.substring(lastBreak + 1).isBlank();
    }

    /** Whether {@code title} would put a title on a tile, or is the blank a titled tile already carries. */
    public static boolean isBlank(Component title) {
        Objects.requireNonNull(title, "title");
        return PlainTextComponentSerializer.plainText().serialize(title).isBlank();
    }
}
