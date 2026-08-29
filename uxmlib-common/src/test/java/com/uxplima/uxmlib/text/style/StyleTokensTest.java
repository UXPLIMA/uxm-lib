package com.uxplima.uxmlib.text.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The token pass: a role becomes a colour, a prefix becomes a word plus a separator, a foreign tag survives. */
class StyleTokensTest {

    private final Theme theme = Theme.defaults();

    @Test
    void aColourTokenBecomesTheColourTheThemeHoldsForThatRole() {
        assertThat(StyleTokens.expand("<body>hello</body>", theme, true)).isEqualTo("<color:#ffffff>hello</color>");
    }

    @Test
    void aTagThatIsNotATokenIsLeftForMiniMessage() {
        assertThat(StyleTokens.expand("<b><player></b>", theme, true)).isEqualTo("<b><player></b>");
    }

    @Test
    void aCategoryPrefixIsTheWordInBoldThenTheSeparator() {
        assertThat(StyleTokens.expand("<tag:'HOME'>", theme, true))
                .isEqualTo("<b><color:#38b6ff>ʜᴏᴍᴇ</color></b> <color:#6b7886>▶</color>");
    }

    @Test
    void aCategoryTheThemeColoursDifferentlyKeepsItsOwnColour() {
        assertThat(StyleTokens.expand("<tag:'shop'>", theme, false)).contains("<color:#5be38c>shop</color>");
    }

    @Test
    void aDenialReadsInTheFailureColourWhicheverFeatureRaisedIt() {
        assertThat(StyleTokens.expand("<etag:'ERROR'>", theme, true))
                .isEqualTo("<b><color:#ff6b6b>ᴇʀʀᴏʀ</color></b> <color:#6b7886>▶</color>");
    }

    @Test
    void aHeaderIsBoldAndInTheAccentColour() {
        assertThat(StyleTokens.expand("<h:'REWARDS'>", theme, true))
                .isEqualTo("<b><color:#38b6ff>ʀᴇᴡᴀʀᴅꜱ</color></b>");
    }

    @Test
    void aPrefixWithNoLabelIsADefectRatherThanAnEmptyLine() {
        assertThatThrownBy(() -> StyleTokens.expand("<tag:''> hello", theme, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label");
    }
}
