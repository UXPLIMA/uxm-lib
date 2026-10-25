package com.uxplima.uxmlib.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import org.junit.jupiter.api.Test;

@org.jspecify.annotations.NullUnmarked
class CenteredTextPadderTest {

    @Test
    void short_line_gets_leading_spaces() {
        String pad = CenteredTextPadder.pad("Hi", false);
        // "Hi" = H(6) + i(2) = 8px; (320 - 8) / 2 = 156px; 156 / 4 = 39 spaces.
        assertThat(pad).isEqualTo(" ".repeat(39));
    }

    @Test
    void empty_string_centres_full_width() {
        // 0px text → (320 - 0) / 2 = 160px → 40 spaces.
        assertThat(CenteredTextPadder.pad("", false)).isEqualTo(" ".repeat(40));
    }

    @Test
    void over_wide_line_gets_no_padding() {
        String wide = "W".repeat(100); // 600px >> 320
        assertThat(CenteredTextPadder.pad(wide, false)).isEmpty();
    }

    @Test
    void bold_text_is_wider_so_fewer_pad_spaces() {
        String plain = CenteredTextPadder.pad("Hi", false);
        String bold = CenteredTextPadder.pad("Hi", true);
        assertThat(bold.length()).isLessThan(plain.length());
    }

    @Test
    void rejects_null() {
        assertThatNullPointerException().isThrownBy(() -> CenteredTextPadder.pad(null, false));
    }

    @Test
    void a_component_is_measured_run_by_run_with_its_own_boldness() {
        // "Hi" in bold (7 + 3 = 10px) then " there" plain: the bold run is the only one that grows.
        Component line = Component.text()
                .append(Component.text("Hi").decorate(TextDecoration.BOLD))
                .append(Component.text(" there"))
                .build();
        int width = 10 + GlyphWidthTable.widthOf(" there", false);
        assertThat(CenteredTextPadder.pad(line)).isEqualTo(" ".repeat((320 - width) / 2 / 4));
    }

    @Test
    void bold_is_inherited_by_children_and_can_be_turned_off_again() {
        Component line = Component.text("A")
                .decorate(TextDecoration.BOLD)
                .append(Component.text("B"))
                .append(Component.text("C").decoration(TextDecoration.BOLD, false));
        int width = GlyphWidthTable.widthOf("AB", true) + GlyphWidthTable.widthOf("C", false);
        assertThat(CenteredTextPadder.pad(line)).isEqualTo(" ".repeat((320 - width) / 2 / 4));
    }

    @Test
    void a_centred_line_is_the_padding_then_the_line() {
        Component line = Component.text("Hi");
        assertThat(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(CenteredTextPadder.centred(line)))
                .isEqualTo(" ".repeat(39) + "Hi");
    }
}
