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
 * The palette: what it answers with nothing configured, what one line of a file changes, and: the part
 * worth guarding, what a file that names one key does <em>not</em> change for the keys it leaves out.
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
}
