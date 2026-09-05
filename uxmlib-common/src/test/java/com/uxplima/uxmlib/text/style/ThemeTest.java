package com.uxplima.uxmlib.text.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The palette: what it answers with nothing configured, what one line of a file changes, and the part worth
 * guarding, which is what a file that names one key does <em>not</em> change for the keys it leaves out.
 */
class ThemeTest {

    @Test
    void theShippedPaletteAnswersEveryRole() {
        Theme theme = Theme.defaults();

        assertThat(theme.hex("accent")).isEqualTo("#38b6ff");
        assertThat(theme.hex("value")).isEqualTo("#8fd9ff");
        assertThat(theme.hasColour("nonsense")).isFalse();
        assertThat(theme.colour("nonsense")).isEqualTo(theme.colour("body"));
    }

    @Test
    void aFileChangesTheColoursItNamesAndKeepsTheRest() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("colours", "accent").set("#ff0000");

        Theme theme = Theme.from(node);

        assertThat(theme.hex("accent")).isEqualTo("#ff0000");
        assertThat(theme.hex("body")).isEqualTo("#ffffff");
    }

    @Test
    void aCategoryTakesTheColourTheFileGivesIt() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("prefix", "categories", "parkour").set("event");

        assertThat(Theme.from(node).categoryRole("PARKOUR")).isEqualTo("event");
        assertThat(Theme.from(node).categoryRole("tags")).isEqualTo("accent");
    }

    @Test
    void glyphsAreConfigurableAndDefaultToTheShippedOnes() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("glyphs", "row").set("-");

        Theme theme = Theme.from(node);

        assertThat(theme.glyph("row")).isEqualTo("-");
        assertThat(theme.glyph("action")).isEqualTo("→");
        assertThat(theme.glyph("nothing-is-called-this")).isEmpty();
    }

    /** The separator lived under the prefix block before the glyphs were configurable. Both still work. */
    @Test
    void aSeparatorUnderThePrefixBlockIsStillRead() throws ConfigurateException {
        ConfigurationNode legacy = CommentedConfigurationNode.root();
        legacy.node("prefix", "separator").set("»");

        ConfigurationNode both = CommentedConfigurationNode.root();
        both.node("prefix", "separator").set("»");
        both.node("glyphs", "separator").set("|");

        assertThat(Theme.from(legacy).separator()).isEqualTo("»");
        assertThat(Theme.from(both).separator()).isEqualTo("|");
    }

    @Test
    void aGradientIsWhateverStopsTheFileNames() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("gradients", "header").setList(String.class, List.of("#48cae4", "#6c8dfb"));

        Theme theme = Theme.from(node);

        assertThat(theme.gradient("header")).hasSize(2);
        assertThat(theme.gradient("header").get(0).asHexString()).isEqualToIgnoringCase("#48cae4");
        assertThat(theme.gradient("nothing-is-called-this")).isEmpty();
    }

    @Test
    void aThemeWithNoGradientBlockNamesNoGradients() {
        assertThat(Theme.defaults().gradient("header")).isEmpty();
    }

    @Test
    void smallCapitalsFollowTheLanguage() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("small-caps", "en").set(true);
        node.node("small-caps", "tr").set(false);

        Theme theme = Theme.from(node);

        assertThat(theme.smallCaps(Locale.ENGLISH)).isTrue();
        assertThat(theme.smallCaps(Locale.of("tr"))).isFalse();
    }

    /**
     * The one that has to hold: a typeface is a thing the file decides, so a file that names no language
     * converts nothing. A library that wrote English in small capitals by itself would repaint a plugin
     * that only wanted the colours, and no line anywhere would say why.
     */
    @Test
    void nothingIsWrittenInSmallCapitalsUntilTheFileNamesALanguage() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("small-caps", "fr").set(true);

        Theme theme = Theme.from(node);

        assertThat(theme.smallCaps(Locale.FRENCH)).isTrue();
        assertThat(theme.smallCaps(Locale.ENGLISH)).isFalse();
        assertThat(theme.smallCaps(Locale.of("el"))).isFalse();
    }

    @Test
    void anInvalidColourIsADefectAtLoadRatherThanABlackMessageInTheGame() {
        ConfigurationNode node = CommentedConfigurationNode.root();

        assertThatThrownBy(() -> {
                    node.node("colours", "accent").set("blue");
                    Theme.from(node);
                })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blue");
    }

    /** The file the library ships has to parse into exactly the defaults, or copying it changes the look. */
    @Test
    void theShippedFileParsesIntoTheShippedDefaults() throws Exception {
        Theme defaults = Theme.defaults();

        Theme fromFile = Theme.from(shippedTheme());

        assertThat(fromFile.hex("accent")).isEqualTo(defaults.hex("accent"));
        assertThat(fromFile.hex("event")).isEqualTo(defaults.hex("event"));
        assertThat(fromFile.glyph("title")).isEqualTo(defaults.glyph("title"));
        assertThat(fromFile.separator()).isEqualTo(defaults.separator());
        assertThat(fromFile.categoryRole("error")).isEqualTo("bad");
        assertThat(fromFile.smallCaps(Locale.ENGLISH)).isFalse();
        assertThat(fromFile.smallCaps(Locale.of("tr"))).isFalse();
    }

    private static ConfigurationNode shippedTheme() throws Exception {
        var stream = Theme.class.getClassLoader().getResourceAsStream("uxmlib/theme.conf");
        assertThat(stream).describedAs("uxmlib/theme.conf is on the classpath").isNotNull();
        try (Reader reader = new InputStreamReader(java.util.Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
            return HoconConfigurationLoader.builder()
                    .source(() -> new java.io.BufferedReader(reader))
                    .build()
                    .load();
        }
    }

    @Test
    void aGradientIsFoundWhateverCaseTheLineWritesItIn() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("gradients", "mint").setList(String.class, List.of("#4ecca3", "#48cae4"));

        Theme theme = Theme.from(node);

        assertThat(theme.gradient("MINT")).isEqualTo(theme.gradient("mint")).hasSize(2);
    }

    @Test
    void aThemeThatNamesNoGradientHasNone() {
        assertThat(Theme.defaults().gradient("mint")).isEmpty();
        assertThat(Theme.defaults().gradient("header")).isEmpty();
    }

    @Test
    void aRoleMayNameAColourOfThePalette() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("palette", "sky").set("#48cae4");
        node.node("roles", "accent").set("sky");

        assertThat(Theme.from(node).hex("accent")).isEqualTo("#48cae4");
    }

    @Test
    void theServerMayCallItsColoursWhateverItLikes() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("palette", "kirmizi").set("#e23d3d");
        node.node("roles", "bad").set("kirmizi");

        assertThat(Theme.from(node).hex("bad")).isEqualTo("#e23d3d");
    }

    @Test
    void aRoleMayNameAHexWithNoPaletteAtAll() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("roles", "bad").set("#e23d3d");

        assertThat(Theme.from(node).hex("bad")).isEqualTo("#e23d3d");
    }

    @Test
    void aRoleTheLibraryNeverHeardOfBecomesARole() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("palette", "lilac").set("#b388ff");
        node.node("roles", "premium").set("lilac");

        Theme theme = Theme.from(node);

        assertThat(theme.hasColour("premium")).isTrue();
        assertThat(theme.hex("premium")).isEqualTo("#b388ff");
    }

    @Test
    void theOldColoursBlockStillWorksAndARoleWins() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("colours", "accent").set("#ff0000");
        node.node("colours", "value").set("#00ff00");
        node.node("roles", "accent").set("#0000ff");

        Theme theme = Theme.from(node);

        assertThat(theme.hex("accent")).isEqualTo("#0000ff");
        assertThat(theme.hex("value")).isEqualTo("#00ff00");
    }

    @Test
    void aValueThatIsNeitherAColourNorAPaletteNameFailsAtLoad() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("roles", "accent").set("mint");

        assertThatThrownBy(() -> Theme.from(node))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mint");
    }

    @Test
    void theWheelGivesEachPositionTheArcToItsNeighbour() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("palette", "rose").set("#ff6b8b");
        node.node("palette", "mint").set("#4ecca3");
        node.node("palette", "sky").set("#48cae4");
        node.node("wheel").setList(String.class, List.of("rose", "mint", "sky"));

        Theme theme = Theme.from(node);

        assertThat(hexes(theme.arc(0))).containsExactly("#ff6b8b", "#4ecca3");
        assertThat(hexes(theme.arc(1))).containsExactly("#4ecca3", "#48cae4");
    }

    @Test
    void theWheelWrapsSoALongMenuNeverRunsOut() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("wheel").setList(String.class, List.of("#ff6b8b", "#4ecca3"));

        Theme theme = Theme.from(node);

        assertThat(hexes(theme.arc(2))).isEqualTo(hexes(theme.arc(0)));
        assertThat(hexes(theme.arc(-1))).isEqualTo(hexes(theme.arc(1)));
    }

    @Test
    void aThemeWithNoWheelPaintsNothingOfItsOwn() {
        assertThat(Theme.defaults().wheel()).isEmpty();
        assertThat(Theme.defaults().arc(0)).isEmpty();
    }

    @Test
    void aGradientMayNameColoursOfThePalette() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("palette", "rose").set("#ff6b8b");
        node.node("palette", "peach").set("#ffa07a");
        node.node("gradients", "header").setList(String.class, List.of("rose", "peach"));

        assertThat(hexes(Theme.from(node).gradient("header"))).containsExactly("#ff6b8b", "#ffa07a");
    }

    private static List<String> hexes(List<net.kyori.adventure.text.format.TextColor> colours) {
        return colours.stream()
                .map(colour -> colour.asHexString().toLowerCase(Locale.ROOT))
                .toList();
    }
}
