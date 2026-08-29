package com.uxplima.uxmlib.gui.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

/** The tile shape: a blank name that is a space, a title that opens the lore, and a button left untouched. */
class TilesTest {

    private final Theme theme = Theme.defaults();

    /** An empty name makes the client draw the material's own name, which is the bug this guards. */
    @Test
    void theBlankNameIsASpaceRatherThanAnEmptyComponent() {
        assertThat(plain(Tiles.blankName())).isEqualTo(" ");
        assertThat(Tiles.isBlank(Tiles.blankName())).isTrue();
    }

    @Test
    void aTitledTileOpensWithTheGlyphAndClosesWithAir() {
        Component lore = Component.text("a line");

        String plain = plain(Tiles.titled(theme, Component.text("Tags"), lore));

        assertThat(plain).contains("◆").contains("Tags").contains("a line");
        assertThat(plain).endsWith(" ");
    }

    @Test
    void theTitleLineIsBold() {
        Component head = Tiles.head(theme, Component.text("Tags"));

        assertThat(head.children().stream().anyMatch(child -> child.hasDecoration(TextDecoration.BOLD)))
                .isTrue();
    }

    /** A catalog writes the words of a title and nothing else, so this line has to paint it. */
    @Test
    void theTitleIsPaintedAndNotLeftToTheClientsLoreColour() {
        Component head = Tiles.head(theme, Component.text("Tags"));

        assertThat(colours(head)).contains(theme.colour("accent"));
    }

    /** A title that came in coloured means it (a lobby name, a rank), and keeps what it arrived with. */
    @Test
    void aTitleThatCarriesItsOwnColourKeepsIt() {
        Component head = Tiles.head(theme, Component.text("Lobby", NamedTextColor.GOLD));

        assertThat(colours(head)).contains(NamedTextColor.GOLD).doesNotContain(theme.colour("accent"));
    }

    /** With a header gradient configured, a title is painted across it rather than in one flat colour. */
    @Test
    void aHeaderGradientReachesTheTileTitle() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("gradients", "header").setList(String.class, List.of("#48cae4", "#6c8dfb"));
        Theme gradient = Theme.from(node);

        Component head = Tiles.head(gradient, Component.text("Tags"));

        assertThat(plain(head)).contains("Tags");
        assertThat(colours(head)).hasSizeGreaterThan(2); // the icon, and a colour per letter of the title
    }

    /** A lore that closes its own box must not be closed twice, or the tile sits a line higher than the rest. */
    @Test
    void aLoreThatAlreadyEndsOnAirIsNotGivenMore() {
        Component lore = Lore.of(theme).crumb(Component.text("Cosmetic")).build();

        String[] lines =
                plain(Tiles.titled(theme, Component.text("Tags"), lore)).split("\n", -1);

        assertThat(lines[lines.length - 1]).isBlank();
        assertThat(lines[lines.length - 2]).contains("Cosmetic");
    }

    @Test
    void aButtonWithNoTitleKeepsItsLoreUntouched() {
        Component lore = Component.text("just a line");

        assertThat(Tiles.titled(theme, Component.empty(), lore)).isEqualTo(lore);
    }

    /** Every colour anywhere in {@code component}, so a test can say what a line was painted with. */
    private static Set<TextColor> colours(Component component) {
        Set<TextColor> found = new LinkedHashSet<>();
        TextColor colour = component.color();
        if (colour != null) {
            found.add(colour);
        }
        component.children().forEach(child -> found.addAll(colours(child)));
        return found;
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
