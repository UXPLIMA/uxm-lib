package com.uxplima.uxmlib.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

@org.jspecify.annotations.NullUnmarked
class RenderDowngradeTest {

    private static final ClientCapabilities FULL = new ClientCapabilities(true, true, true);
    private static final ClientCapabilities NO_GRADIENT = new ClientCapabilities(false, true, true);
    private static final ClientCapabilities NO_FONT = new ClientCapabilities(true, false, true);
    private static final ClientCapabilities NO_HOVER_ITEM = new ClientCapabilities(true, true, false);
    private static final ClientCapabilities NOTHING = new ClientCapabilities(false, false, false);

    @Test
    void full_capabilities_returns_text_verbatim() {
        String text = "<gradient:red:blue>hi</gradient> <font:uniform>x</font> "
                + "<hover:show_item:'minecraft:diamond':3>gem</hover>";
        assertThat(RenderDowngrade.apply(text, FULL)).isEqualTo(text);
    }

    @Test
    void gradient_flattened_to_first_colour() {
        assertThat(RenderDowngrade.apply("<gradient:red:blue>hello</gradient>", NO_GRADIENT))
                .isEqualTo("<red>hello</red>");
    }

    @Test
    void gradient_with_multiple_stops_keeps_only_first() {
        assertThat(RenderDowngrade.apply("<gradient:#ff0000:#00ff00:#0000ff>x</gradient>", NO_GRADIENT))
                .isEqualTo("<#ff0000>x</#ff0000>");
    }

    @Test
    void gradient_with_phase_arg_flattens_to_first_colour() {
        // <gradient:red:blue:0.5>: the trailing numeric phase is not a colour.
        assertThat(RenderDowngrade.apply("<gradient:red:blue:0.5>x</gradient>", NO_GRADIENT))
                .isEqualTo("<red>x</red>");
    }

    @Test
    void gradient_short_alias_flattened() {
        assertThat(RenderDowngrade.apply("<gr:gold:aqua>x</gr>", NO_GRADIENT)).isEqualTo("<gold>x</gold>");
    }

    @Test
    void gradient_left_intact_when_gradient_supported() {
        String text = "<gradient:red:blue>x</gradient>";
        assertThat(RenderDowngrade.apply(text, FULL)).isEqualTo(text);
    }

    @Test
    void custom_font_tag_dropped_both_ends() {
        assertThat(RenderDowngrade.apply("<font:uniform>plain</font>", NO_FONT)).isEqualTo("plain");
    }

    @Test
    void custom_font_namespaced_dropped() {
        assertThat(RenderDowngrade.apply("<font:myplugin:fancy>x</font>", NO_FONT))
                .isEqualTo("x");
    }

    @Test
    void font_kept_when_custom_font_supported() {
        String text = "<font:uniform>x</font>";
        assertThat(RenderDowngrade.apply(text, FULL)).isEqualTo(text);
    }

    @Test
    void show_item_hover_falls_back_to_show_text() {
        // The item key + amount become a textual tooltip.
        assertThat(RenderDowngrade.apply("<hover:show_item:'minecraft:diamond':3>gem</hover>", NO_HOVER_ITEM))
                .isEqualTo("<hover:show_text:'minecraft:diamond x3'>gem</hover>");
    }

    @Test
    void show_item_without_amount_falls_back_to_bare_key() {
        assertThat(RenderDowngrade.apply("<hover:show_item:'minecraft:stone'>s</hover>", NO_HOVER_ITEM))
                .isEqualTo("<hover:show_text:'minecraft:stone'>s</hover>");
    }

    @Test
    void show_text_hover_left_intact() {
        String text = "<hover:show_text:'<gray>tip</gray>'>x</hover>";
        assertThat(RenderDowngrade.apply(text, NO_HOVER_ITEM)).isEqualTo(text);
    }

    @Test
    void show_item_left_intact_when_hover_item_supported() {
        String text = "<hover:show_item:'minecraft:diamond':3>gem</hover>";
        assertThat(RenderDowngrade.apply(text, FULL)).isEqualTo(text);
    }

    @Test
    void all_three_downgrades_combine() {
        String text = "<gradient:red:blue>a</gradient><font:uniform>b</font>"
                + "<hover:show_item:'minecraft:gold_ingot':2>c</hover>";
        assertThat(RenderDowngrade.apply(text, NOTHING))
                .isEqualTo("<red>a</red>b<hover:show_text:'minecraft:gold_ingot x2'>c</hover>");
    }

    @Test
    void plain_text_unchanged_under_any_caps() {
        assertThat(RenderDowngrade.apply("just words", NOTHING)).isEqualTo("just words");
    }

    @Test
    void rejects_null_text() {
        assertThatNullPointerException().isThrownBy(() -> RenderDowngrade.apply(null, FULL));
    }

    @Test
    void rejects_null_capabilities() {
        assertThatNullPointerException().isThrownBy(() -> RenderDowngrade.apply("x", null));
    }
}
