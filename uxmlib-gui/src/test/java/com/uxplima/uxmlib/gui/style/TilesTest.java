package com.uxplima.uxmlib.gui.style;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.Test;

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

    @Test
    void aButtonWithNoTitleKeepsItsLoreUntouched() {
        Component lore = Component.text("just a line");

        assertThat(Tiles.titled(theme, Component.empty(), lore)).isEqualTo(lore);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
