package com.uxplima.uxmlib.text.style;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A label is written in small capitals and the tags inside it are not.
 *
 * <p>uxm-plots found {@code <h:'<contest>'>} drawn as the word in angle brackets in English: the label was put into
 * small capitals whole, so {@code <contest>} became {@code <ᴄᴏɴᴛᴇꜱᴛ>}, which is no longer a tag. Turkish is not written
 * in small capitals, so it showed only in English. A box name holding a colour, put into a heading, broke the same
 * way.
 */
class ALabelKeepsItsTagsTest {

    private final Theme theme = Theme.defaults();

    @Test
    @DisplayName("a placeholder tag inside a heading survives the small capitals")
    void aPlaceholderInsideAHeadingSurvives() {
        String expanded = StyleTokens.expand("<h:'<contest>'>", theme, true);

        assertThat(expanded).contains("<contest>").doesNotContain("ᴄᴏɴᴛᴇꜱᴛ");
    }

    @Test
    @DisplayName("the words around a tag are still small capitals, and the tag is untouched")
    void theWordsAroundATagAreStillSmallCapitals() {
        String expanded = StyleTokens.expand("<h:'Box <gold>rare</gold>'>", theme, true);

        assertThat(expanded).contains("ʙᴏx", "<gold>", "ʀᴀʀᴇ", "</gold>");
    }

    @Test
    @DisplayName("a prefix label keeps a tag the same way")
    void aPrefixLabelKeepsATag() {
        assertThat(StyleTokens.expand("<tag:'<name>'>", theme, true)).contains("<name>");
    }
}
