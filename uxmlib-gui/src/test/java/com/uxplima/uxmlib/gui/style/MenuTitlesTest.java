package com.uxplima.uxmlib.gui.style;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

/** Centring a window title: padded into the middle, stripped of colour, and left alone when it will not fit. */
class MenuTitlesTest {

    @Test
    void aTitleIsPaddedIntoTheMiddleOfTheWindow() {
        String plain = plain(MenuTitles.centre(Component.text("ᴛᴀɢꜱ")));

        assertThat(plain).startsWith(" ").endsWith("ᴛᴀɢꜱ");
        assertThat(plain.length()).isGreaterThan("ᴛᴀɢꜱ".length());
    }

    /** The padding is this class's, the look is the caller's: a title that arrived coloured stays coloured. */
    @Test
    void aColourOnATitleIsKept() {
        Component title = Component.text("tags", NamedTextColor.RED);

        Component centred = MenuTitles.centre(title);

        assertThat(centred.children()).contains(title);
        assertThat(plain(centred)).startsWith(" ").endsWith("tags");
    }

    @Test
    void aTitleWiderThanTheWindowIsNotPadded() {
        String wide = "a".repeat(80);

        assertThat(plain(MenuTitles.centre(Component.text(wide)))).isEqualTo(wide);
    }

    @Test
    void aBlankTitleIsHandedBackBecauseAWindowTitledWithSpacesIsWorse() {
        assertThat(plain(MenuTitles.centre(Component.empty()))).isEmpty();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
