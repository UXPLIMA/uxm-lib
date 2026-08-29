package com.uxplima.uxmlib.text;

import java.util.Objects;

/**
 * Pure arithmetic that centres a line of plain text within the default chat width by computing the
 * leading-space string. The pixel widths come from {@link GlyphWidthTable}.
 *
 * <p>Content wider than {@link #CHAT_WIDTH_PX} gets no padding (never negative space). Only visible glyphs
 * count: callers flatten MiniMessage to plain text before calling, so nested colour tags do not affect
 * width.
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
}
