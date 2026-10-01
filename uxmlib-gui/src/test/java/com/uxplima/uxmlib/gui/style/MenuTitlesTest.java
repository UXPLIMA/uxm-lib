package com.uxplima.uxmlib.gui.style;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.text.style.TitleAlignment;
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

    /**
     * A title whose width cannot be measured is left where the client draws it. A translated key, a resource-pack font
     * or a keybind is drawn at a width the plain letters say nothing about, so padding measured from them puts a
     * title the operator lined up with negative-space glyphs a few pixels off, every time.
     */
    @Test
    void aTitleThatCannotBeMeasuredIsNotPadded() {
        Component translated = Component.translatable("space.-8").append(Component.text("Main Menu"));
        Component font = Component.text(
                        "\uE000", Style.style().font(Key.key("pack", "icons")).build())
                .append(Component.text("Main Menu"));
        Component keybind = Component.text("press ").append(Component.keybind("key.jump"));

        assertThat(MenuTitles.centre(translated)).isSameAs(translated);
        assertThat(MenuTitles.centre(font)).isSameAs(font);
        assertThat(MenuTitles.centre(keybind)).isSameAs(keybind);
    }

    /**
     * Centring twice is centring once. A plugin that centred an editor's title handed it to the engine, which centred
     * it again, measuring the padding as part of the title: every such editor sat to the right of the middle.
     */
    @Test
    void aTitleAlreadyLaidOutIsLeftAlone() {
        Component once = MenuTitles.centre(Component.text("ᴛᴀɢꜱ"));

        assertThat(MenuTitles.centre(once)).isSameAs(once);
    }

    /** A server that lines its titles up from the left gets them exactly as written. */
    @Test
    void aLeftAlignedTitleIsHandedBackAsWritten() {
        Component title = Component.text("ᴛᴀɢꜱ");

        assertThat(MenuTitles.lay(title, TitleAlignment.LEFT)).isSameAs(title);
        assertThat(plain(MenuTitles.lay(title, TitleAlignment.CENTRE))).startsWith(" ");
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
