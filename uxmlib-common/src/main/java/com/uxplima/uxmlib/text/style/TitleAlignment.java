package com.uxplima.uxmlib.text.style;

import java.util.Locale;

/**
 * Where a window title sits: in the middle of the window, or where the client draws it, at the left.
 *
 * <p>The client offers no alignment of its own. A centred title is a left one with spaces in front of it, so a server
 * that draws its windows with a resource pack and lines its glyphs up from the left edge wants none of them.
 */
public enum TitleAlignment {
    CENTRE,
    LEFT;

    /**
     * The alignment {@code value} names, as a theme file writes it.
     *
     * @throws IllegalArgumentException when it names neither, so an operator sees the typo at load
     */
    public static TitleAlignment parse(String value) {
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "centre", "center" -> CENTRE;
            case "left" -> LEFT;
            default -> throw new IllegalArgumentException("menu-titles is \"" + value + "\": write centre or left");
        };
    }
}
