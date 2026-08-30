package com.uxplima.uxmlib.text.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import com.uxplima.uxmlib.text.Text;
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
    void aHeaderMayNameAToneOfItsOwn() throws ConfigurateException {
        Theme toned = themeWithTones();

        assertThat(StyleTokens.expand("<h:'REWARDS':mint>", toned, false))
                .isEqualTo("<gradient:#4ecca3:#48cae4><b>REWARDS</b></gradient>");
    }

    /** A tone nobody named is a spelling mistake, and the header it falls back to is still readable. */
    @Test
    void aToneTheThemeDoesNotKnowFallsBackToTheHeader() throws ConfigurateException {
        Theme toned = themeWithTones();

        assertThat(StyleTokens.expand("<h:'REWARDS':moss>", toned, false))
                .isEqualTo("<gradient:#ffe66d:#ff6b8b><b>REWARDS</b></gradient>");
    }

    @Test
    void aTitleTakesOneOfTheTonesAndAlwaysTheSameOne() throws ConfigurateException {
        Theme toned = themeWithTones();

        String once = toneOf(StyleTokens.title(toned, Component.text("Emotes")));
        String again = toneOf(StyleTokens.title(toned, Component.text("  emotes ")));

        assertThat(once).isEqualTo(again).startsWith("<gradient:");
    }

    /** The point of the tones: two tiles of one menu do not read as the same heading. */
    @Test
    void twoTitlesTakeTwoTones() throws ConfigurateException {
        Theme toned = themeWithTones();

        assertThat(toneOf(StyleTokens.title(toned, Component.text("Emotes"))))
                .isNotEqualTo(toneOf(StyleTokens.title(toned, Component.text("Particle trails"))));
    }

    /** A theme that names no tone keeps every title on the header gradient, as it did before tones existed. */
    @Test
    void noTonesMeansTheHeaderGradient() throws ConfigurateException {
        Theme gradient = themeWithHeaderStops("#48cae4", "#6c8dfb");

        assertThat(serialize(StyleTokens.title(gradient, Component.text("Emotes"))))
                .isEqualTo(serialize(StyleTokens.header(gradient, Component.text("Emotes"))));
    }

    /** A title that arrived painted was painted on purpose: a lobby name, a rank, a player's own tag. */
    @Test
    void aTitleThatCarriesAColourKeepsIt() throws ConfigurateException {
        Theme toned = themeWithTones();
        Component painted = Component.text("Emotes").color(TextColor.fromHexString("#123456"));

        assertThat(StyleTokens.title(toned, painted)).isEqualTo(painted);
    }

    private static Theme themeWithTones() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("gradients", "header").setList(String.class, List.of("#ffe66d", "#ff6b8b"));
        node.node("tones", "strawberry").setList(String.class, List.of("#ff6b8b", "#ffa07a"));
        node.node("tones", "mint").setList(String.class, List.of("#4ecca3", "#48cae4"));
        node.node("tones", "lavender").setList(String.class, List.of("#b388ff", "#ff6b8b"));
        return Theme.from(node);
    }

    private static String serialize(Component component) {
        return Text.serialize(component);
    }

    /** The gradient tag a painted title opens with, which is the tone and not the words inside it. */
    private static String toneOf(Component title) {
        String written = serialize(title);
        int end = written.indexOf('>');
        return end < 0 ? written : written.substring(0, end + 1);
    }

    @Test
    void aPrefixWithNoLabelIsADefectRatherThanAnEmptyLine() {
        assertThatThrownBy(() -> StyleTokens.expand("<tag:''> hello", theme, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label");
    }
}
