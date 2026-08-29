package com.uxplima.uxmlib.text.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

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
        assertThat(StyleTokens.expand("<h:'REWARDS'>", theme, true)).isEqualTo("<b><color:#38b6ff>ʀᴇᴡᴀʀᴅꜱ</color></b>");
    }

    /** A theme that names a header gradient paints the header across it; nothing else changes. */
    @Test
    void aHeaderTakesTheThemesGradientWhenThereIsOne() throws ConfigurateException {
        Theme gradient = themeWithHeaderStops("#48cae4", "#6c8dfb");

        assertThat(StyleTokens.expand("<h:'REWARDS'>", gradient, true))
                .isEqualTo("<gradient:#48cae4:#6c8dfb><b>ʀᴇᴡᴀʀᴅꜱ</b></gradient>");
    }

    /** One stop is a flat colour, which is how an operator switches the effect off. */
    @Test
    void aSingleStopIsAFlatColour() throws ConfigurateException {
        Theme flat = themeWithHeaderStops("#ff0000");

        assertThat(StyleTokens.expand("<h:'REWARDS'>", flat, false)).isEqualTo("<b><color:#ff0000>REWARDS</color></b>");
    }

    private static Theme themeWithHeaderStops(String... stops) throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("gradients", "header").setList(String.class, List.of(stops));
        return Theme.from(node);
    }

    @Test
    void aPrefixWithNoLabelIsADefectRatherThanAnEmptyLine() {
        assertThatThrownBy(() -> StyleTokens.expand("<tag:''> hello", theme, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label");
    }
}
