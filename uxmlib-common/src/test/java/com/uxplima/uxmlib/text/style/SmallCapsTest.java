package com.uxplima.uxmlib.text.style;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The alphabet itself, including the two letters Unicode does not have a small capital for. */
class SmallCapsTest {

    @Test
    void everyLetterConverts() {
        assertThat(SmallCaps.of("abcdefghijklmnopqrstuvwxyz")).isEqualTo("ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ");
    }

    @Test
    void caseDoesNotMatterBecauseTheResultHasOnlyOne() {
        assertThat(SmallCaps.of("Home")).isEqualTo(SmallCaps.of("HOME"));
    }

    @Test
    void xKeepsItsOwnShapeAndQTakesTheOneUnicodeOffers() {
        assertThat(SmallCaps.of("x")).isEqualTo("x");
        assertThat(SmallCaps.of("q")).isEqualTo("ǫ");
    }

    @Test
    void aDigitAndAGlyphArePassedThrough() {
        assertThat(SmallCaps.of("3 ▶ (50%)")).isEqualTo("3 ▶ (50%)");
    }

    @Test
    void lettersOutsideAsciiKeepTheirOwnWriting() {
        assertThat(SmallCaps.of("Etiket açık")).isEqualTo("ᴇᴛɪᴋᴇᴛ ᴀçıᴋ");
    }
}
