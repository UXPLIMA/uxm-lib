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

    /**
     * A capital with no small-capital form stood at full height among the small capitals: {@code DİL} read as a tall
     * {@code İ} between two small letters. It is written in lower case, the height of a small capital, and a capital
     * whose lower case is a Latin letter takes that letter's small capital.
     */
    @Test
    void aCapitalWithNoSmallFormTakesItsLowerCase() {
        assertThat(SmallCaps.of("DİL ÇOK ĞÖŞÜ")).isEqualTo("ᴅɪʟ çᴏᴋ ğöşü");
        assertThat(SmallCaps.of("Çevrimiçi")).isEqualTo("çᴇᴠʀɪᴍɪçɪ");
    }

    /** Only a Latin capital is written in lower case. A script with no small capitals at all is left as written. */
    @Test
    void aCapitalOfAnotherScriptIsLeftAsWritten() {
        assertThat(SmallCaps.of("Ваш скин")).isEqualTo("Ваш скин");
        assertThat(SmallCaps.of("Ωμέγα")).isEqualTo("Ωμέγα");
    }
}
