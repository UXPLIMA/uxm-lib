package com.uxplima.uxmlib.gui.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

/** The lore shape: the order of the blocks, the air between them, and glyphs that come from the theme. */
class LoreTest {

    private final Theme theme = Theme.defaults();

    @Test
    void theBlocksComeOutInTheOrderTheShapeAllows() {
        Component lore = Lore.of(theme)
                .crumb(Component.text("Cosmetic"))
                .description(Component.text("About"), Component.text("What it does"))
                .details(Component.text("Details"))
                .row(Component.text("Owned"), Component.text("12"))
                .action(Component.text("Click to wear"))
                .build();

        List<String> lines = lines(lore);
        assertThat(lines.get(0)).contains("Cosmetic");
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("✎").contains("About"));
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("≡").contains("Details"));
        assertThat(lines)
                .anySatisfy(
                        line -> assertThat(line).contains("▪").contains("Owned").contains("12"));
        assertThat(lines.get(lines.size() - 1)).contains("→").contains("Click to wear");
    }

    @Test
    void aBlockNobodyFilledInTakesNoSpace() {
        Component lore = Lore.of(theme)
                .description(Component.text("About"), Component.text("Text"))
                .build();

        assertThat(lines(lore)).hasSize(2);
    }

    @Test
    void aBlankLineSeparatesTwoBlocks() {
        Component lore = Lore.of(theme)
                .crumb(Component.text("Cosmetic"))
                .action(Component.text("Click"))
                .build();

        assertThat(lines(lore).get(1)).isBlank();
    }

    /** A description a translator wrote over two lines stays two lines. */
    @Test
    void aMultiLineDescriptionKeepsTheBreaksTheTranslatorWrote() {
        Component text = Component.text("first").append(Component.newline()).append(Component.text("second"));

        Component lore =
                Lore.of(theme).description(Component.text("About"), text).build();

        assertThat(lines(lore)).hasSize(3);
        assertThat(lines(lore).get(1)).contains("first");
        assertThat(lines(lore).get(2)).contains("second");
    }

    @Test
    void theGlyphsComeFromTheThemeSoAServerCanChangeThem() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("glyphs", "row").set("-");
        Lore lore = Lore.of(Theme.from(node));

        Component built = lore.details(Component.text("Details"))
                .row(Component.text("Owned"), Component.text("12"))
                .build();

        assertThat(lines(built)).anySatisfy(line -> assertThat(line).contains("- Owned"));
    }

    private static List<String> lines(Component lore) {
        String plain = PlainTextComponentSerializer.plainText().serialize(lore);
        return List.of(plain.split("\n", -1));
    }
}
