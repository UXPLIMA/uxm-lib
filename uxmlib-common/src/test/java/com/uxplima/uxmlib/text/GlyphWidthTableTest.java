package com.uxplima.uxmlib.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

@org.jspecify.annotations.NullUnmarked
class GlyphWidthTableTest {

    @Test
    void space_is_four_pixels() {
        assertThat(GlyphWidthTable.widthOf(' ', false)).isEqualTo(4);
    }

    @Test
    void narrow_glyphs_are_narrower_than_default() {
        assertThat(GlyphWidthTable.widthOf('i', false)).isEqualTo(2);
        assertThat(GlyphWidthTable.widthOf('l', false)).isEqualTo(3);
        assertThat(GlyphWidthTable.widthOf('!', false)).isEqualTo(2);
        assertThat(GlyphWidthTable.widthOf('.', false)).isEqualTo(2);
    }

    @Test
    void default_glyph_is_six_pixels() {
        assertThat(GlyphWidthTable.widthOf('A', false)).isEqualTo(6);
        assertThat(GlyphWidthTable.widthOf('z', false)).isEqualTo(6);
    }

    @Test
    void bold_adds_one_pixel() {
        assertThat(GlyphWidthTable.widthOf('A', true)).isEqualTo(7);
        assertThat(GlyphWidthTable.widthOf(' ', true)).isEqualTo(5);
    }

    @Test
    void unknown_code_point_falls_back_to_default() {
        assertThat(GlyphWidthTable.widthOf(0xE000, false)).isEqualTo(6); // private use: the font draws nothing
    }

    /**
     * The measured table, not the guess it replaced. A bracket pair is three ink columns wide, the same as
     * {@code I}, so it advances 4 and not the 5 the hand-written table used to answer.
     */
    @Test
    void bracketsAreFourPixelsWideAsTheFontDrawsThem() {
        assertThat(GlyphWidthTable.widthOf('(', false)).isEqualTo(4);
        assertThat(GlyphWidthTable.widthOf(')', false)).isEqualTo(4);
        assertThat(GlyphWidthTable.widthOf('{', false)).isEqualTo(4);
        assertThat(GlyphWidthTable.widthOf('}', false)).isEqualTo(4);
    }

    /**
     * The half of the font a hand-written ASCII table cannot hold. A Turkish sentence is full of dotless i,
     * and small capitals are what a stylised interface is set in; both were being counted 6 pixels wide.
     */
    @Test
    void theTableKnowsTheGlyphsOutsideAscii() {
        assertThat(GlyphWidthTable.widthOf('ı', false)).isEqualTo(2);
        assertThat(GlyphWidthTable.widthOf('ɪ', false)).isEqualTo(4);
        assertThat(GlyphWidthTable.widthOf('→', false)).isEqualTo(8);
        assertThat(GlyphWidthTable.widthOf('☃', false)).isEqualTo(8);
    }

    /** A surrogate pair is one glyph. Measuring by char counts it as two default-width ones. */
    @Test
    void anEmojiIsOneGlyphRatherThanTwoSurrogates() {
        String wave = new String(Character.toChars(0x1F30A));

        assertThat(GlyphWidthTable.widthOf(0x1F30A, false)).isEqualTo(9);
        assertThat(GlyphWidthTable.widthOf(wave, false)).isEqualTo(9);
        assertThat(GlyphWidthTable.widthOf(wave.charAt(0), false) + GlyphWidthTable.widthOf(wave.charAt(1), false))
                .isEqualTo(12);
    }

    @Test
    void aStringIsTheSumOfItsGlyphs() {
        assertThat(GlyphWidthTable.widthOf("Al", false)).isEqualTo(6 + 3);
        assertThat(GlyphWidthTable.widthOf("Al", true)).isEqualTo(7 + 4);
        assertThat(GlyphWidthTable.widthOf("", false)).isZero();
    }
}
