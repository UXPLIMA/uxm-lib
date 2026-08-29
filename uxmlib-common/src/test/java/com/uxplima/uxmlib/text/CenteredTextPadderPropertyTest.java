package com.uxplima.uxmlib.text;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.StringLength;

/**
 * Property-based bounds for {@link CenteredTextPadder}. The centring maths must stay inside the chat line for
 * <em>every</em> input: padding is never negative and the padded line never overflows
 * {@link CenteredTextPadder#CHAT_WIDTH_PX}. Complements the worked pixel examples in
 * {@link CenteredTextPadderTest}.
 */
@org.jspecify.annotations.NullUnmarked
class CenteredTextPadderPropertyTest {

    private static int widthOf(String s, boolean bold) {
        int total = 0;
        for (int i = 0; i < s.length(); i++) {
            total += GlyphWidthTable.widthOf(s.charAt(i), bold);
        }
        return total;
    }

    /** Requirement: the pad is only ever leading spaces: it must not inject any other character. */
    @Property
    void pad_is_only_spaces(@ForAll @StringLength(max = 80) String plain, @ForAll boolean bold) {
        String pad = CenteredTextPadder.pad(plain, bold);
        assertThat(pad.chars()).allMatch(c -> c == ' ');
    }

    /** Requirement: padding is never negative space: a defined, exception-free result for any input. */
    @Property
    void pad_length_is_never_negative(@ForAll @StringLength(max = 80) String plain, @ForAll boolean bold) {
        assertThat(CenteredTextPadder.pad(plain, bold).length()).isGreaterThanOrEqualTo(0);
    }

    /**
     * Requirement: for any line that actually fits the chat width, the leading pad never pushes the visible
     * text past the right edge: pad pixels plus text pixels stay within {@link CenteredTextPadder#CHAT_WIDTH_PX}.
     */
    @Property
    void padding_never_pushes_a_fitting_line_past_the_right_edge(
            @ForAll @StringLength(max = 80) String plain, @ForAll boolean bold) {
        int textPx = widthOf(plain, bold);
        if (textPx < CenteredTextPadder.CHAT_WIDTH_PX) {
            int padPx = widthOf(CenteredTextPadder.pad(plain, bold), false); // pad uses non-bold spaces
            assertThat(padPx + textPx).isLessThanOrEqualTo(CenteredTextPadder.CHAT_WIDTH_PX);
        }
    }

    /** Requirement: text at least as wide as the chat line gets no padding: never negative space. */
    @Property
    void over_wide_text_gets_no_padding(@ForAll @StringLength(min = 60, max = 200) String plain, @ForAll boolean bold) {
        // 60+ default-width glyphs comfortably exceed the 320px line.
        if (widthOf(plain, bold) >= CenteredTextPadder.CHAT_WIDTH_PX) {
            assertThat(CenteredTextPadder.pad(plain, bold)).isEmpty();
        }
    }

    /** Requirement: bold text is at least as wide as plain, so the bold pad is never longer than the plain pad. */
    @Property
    void bold_pad_is_never_longer_than_plain_pad(@ForAll @StringLength(max = 80) String plain) {
        int boldPad = CenteredTextPadder.pad(plain, true).length();
        int plainPad = CenteredTextPadder.pad(plain, false).length();
        assertThat(boldPad).isLessThanOrEqualTo(plainPad);
    }
}
