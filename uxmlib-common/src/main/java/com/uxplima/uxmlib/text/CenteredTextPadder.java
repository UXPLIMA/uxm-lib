package com.uxplima.uxmlib.text;

import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Pure arithmetic that centres a line of plain text within the default chat width by computing the
 * leading-space string. The pixel widths come from {@link GlyphWidthTable}.
 *
 * <p>Content wider than {@link #CHAT_WIDTH_PX} gets no padding (never negative space). Only visible glyphs
 * count. A caller with plain text hands the text and says whether it is bold; a caller with a drawn line hands
 * the component, and every run is measured at its own boldness.
 */
public final class CenteredTextPadder {

    private CenteredTextPadder() {}

    /** Default single-line chat width in pixels. */
    public static final int CHAT_WIDTH_PX = 320;

    /**
     * Returns the leading-space string that centres {@code plain} within {@link #CHAT_WIDTH_PX}. Returns
     * {@code ""} when the text is at least as wide as the chat line.
     */
    public static String pad(String plain, boolean bold) {
        Objects.requireNonNull(plain, "plain");
        int textWidth = GlyphWidthTable.widthOf(plain, bold);
        if (textWidth >= CHAT_WIDTH_PX) {
            return "";
        }
        int leftoverPx = (CHAT_WIDTH_PX - textWidth) / 2;
        // Padding is emitted with regular (non-bold) spaces, which advance SPACE_WIDTH (4px) each: the
        // operator's <bold> only affects the visible content, not the leading whitespace.
        int spaces = leftoverPx / GlyphWidthTable.SPACE_WIDTH;
        return " ".repeat(spaces);
    }
    /**
     * Returns the leading-space string that centres {@code line} as it will draw. Each run of text is measured
     * with its own boldness, inherited down the tree the way the client draws it, so a bold title and a plain
     * name on one line both count at their real width. A run that is not text, a translated name, counts as
     * nothing, so a line built around one leans a little to the right rather than past the edge.
     */
    public static String pad(Component line) {
        Objects.requireNonNull(line, "line");
        int textWidth = widthOf(line, false);
        if (textWidth >= CHAT_WIDTH_PX) {
            return "";
        }
        return " ".repeat((CHAT_WIDTH_PX - textWidth) / 2 / GlyphWidthTable.SPACE_WIDTH);
    }

    /** {@code line} with the spaces in front of it that put it in the middle of the chat. */
    public static Component centred(Component line) {
        Objects.requireNonNull(line, "line");
        String pad = pad(line);
        return pad.isEmpty() ? line : Component.text(pad).append(line);
    }

    private static int widthOf(Component component, boolean inheritedBold) {
        TextDecoration.State state = component.decoration(TextDecoration.BOLD);
        boolean bold = state == TextDecoration.State.NOT_SET ? inheritedBold : state == TextDecoration.State.TRUE;
        int width = component instanceof TextComponent text ? GlyphWidthTable.widthOf(text.content(), bold) : 0;
        for (Component child : component.children()) {
            width += widthOf(child, bold);
        }
        return width;
    }
}
